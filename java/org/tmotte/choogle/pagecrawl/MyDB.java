package org.tmotte.choogle.pagecrawl;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.tmotte.choogle.util.DBConfig;
import org.tmotte.common.db.HotRodDataSource;
import org.tmotte.common.text.Outlog;

/**
 * This does not prevent accidental duplicate crawls, but it does prevent different crawlers
 * from causing deadlocks with another.
 */
class MyDB {

  private final Outlog log;
  private final DBConfig dbConfig;
  private final HotRodDataSource hds;

  public MyDB(Outlog log, boolean forceRebuild) throws Exception {
    this.log=log;
    dbConfig=new DBConfig(1);
    hds=new HotRodDataSource(dbConfig.get());
    setupPostgresTables(forceRebuild);
    truncate();
  }
  void close() throws Exception {
    dbConfig.close();
  }

  ////////////////////////////////
  // CREATION / INITIALIZATION: //
  ////////////////////////////////

  private void setupPostgresTables(boolean forceRebuild) throws Exception {
    try (Connection conn=hds.getConnection()) {
      makeTable(
        conn,
        "url_queue",
        forceRebuild,
        "create table url_queue( "
          +" id serial primary key not null,"
          +" site varchar(128), "
          +" uri varchar(512), "
          +" locked boolean, "
          +" deleted boolean"
          +")"
        ,
        "CREATE INDEX idx_site ON url_queue USING hash (site)"
        ,
        "CREATE INDEX idx_uri ON url_queue USING hash (uri)"
        ,
        "CREATE INDEX idx_locked ON url_queue USING hash (locked)"
        ,
        "CREATE INDEX idx_deleted ON url_queue USING hash (deleted)"
      );
      makeTable(
        conn,
        "site_lock",
        forceRebuild,
        "create table site_lock(site varchar(128) primary key not null, locked boolean)"
      );
    }
  }
  private void makeTable(Connection conn, String table, boolean forceRebuild, String... tableCreates) throws Exception {
    Boolean tableExists=
      hds.withQueryResult(
        hds.prepare(
          conn,
          "SELECT table_name, table_schema FROM information_schema.tables "
          +" WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')"
          +" and table_name=? ",
          table
        ),
        rs -> rs.next()
      );
    if (tableExists && forceRebuild)
      hds.runUpdate(conn, "drop table "+table);
    if (!tableExists || forceRebuild)
      for (String sql: tableCreates)
        hds.runUpdate(conn, sql);
  }

  void truncate() throws Exception {
    hds.runUpdate("delete from url_queue");
  }
  void truncate(String site) throws Exception {
    hds.runUpdate("delete from url_queue where site=?", site);
  }
  void cleanQueue(String site) throws Exception {
    hds.runUpdate("update url_queue set locked=false where locked=true and deleted=false and site=?", site);
    hds.runUpdate("delete from url_queue where deleted=true and site=?", site);
  }

  //////////////
  // RUNTIME: //
  //////////////

  void establish(String site) throws Exception {
    hds.runUpdate(
      "insert into site_lock(site, locked) "
     +" select ?, false where not exists (select 1 from site_lock SL where SL.site=?)"
     ,
     site, site
    );
  }

  private String insertQueueSQL=
    "insert into url_queue(site, uri, locked, deleted) "+
      "select ?, ?, false, false where not exists ( "+
      "  select 1 from url_queue where site=? and uri=?"+
      ")";
  int addURIs(String site, Stream<String> uris) throws Exception {
    try (
        Connection conn=hds.getConnection();
        PreparedStatement psInsert=conn.prepareStatement(insertQueueSQL);
      ){
      if (log.is(2))
        log.date().add("MyDB.addURIs() ", site);
      return uris.collect(Collectors.summingInt(
        uri->{
          try {
            return hds.runUpdate(psInsert, site, uri, site, uri);
          } catch (Exception e) {
            log.date().add(e);
            return 0;
          }
        }
      ));
    }
  }
  void addURI(String site, String uri) throws Exception {
    hds.runUpdate(insertQueueSQL, site, uri, site, uri);
  }

  int getScheduledSize(String site) throws Exception {
    return getSize(
      site, "select count(*) from url_queue uq where uq.site=? and uq.locked=false and uq.deleted=false"
    );
  }
  int getElsewhereSize(String site) throws Exception {
    return getSize(
      site, "select count(*) from url_queue uq where uq.site<>? and uq.locked=false and uq.deleted=false"
    );
  }
  private int getSize(String site, String sql) throws Exception {
    try (
        Connection conn=hds.getConnection();
        PreparedStatement ps=hds.prepare(conn, sql, site);
      ){
      return hds.withQueryResult(
        ps, rs -> rs.next() ?rs.getInt(1) :0
      );
    }
  }


  /** This only gets one URL at a time, so it's slow. Also refer to getNextURIs() */
  String getNextURI(String site) throws Exception {
    String
      lockSQL=
        "update url_queue set locked=true, deleted=true where id=? and locked=false and deleted=false"
      ,
      selectSQL=
        "select id, uri from url_queue uq where uq.site=? and uq.locked=false and uq.deleted=false"
      ;
    try (
        Connection conn=hds.getTransaction();
        PreparedStatement psLock=hds.prepare(conn, lockSQL);
        PreparedStatement psQuery=hds.prepare(conn, selectSQL, site)
      ){
      // We MUST set these parameters as such or the driver will
      // try to load the entire resultset into memory on our first rs.next():
      // https://jdbc.postgresql.org/documentation/83/query.html#query-with-cursor
      lockSite(conn, site);
      psQuery.setFetchSize(2);
      psQuery.setFetchDirection(ResultSet.FETCH_FORWARD);
      //
      return hds.withQueryResult(
        psQuery,
        rs -> {
          while (rs.next()) {
            Object id=rs.getObject(1);
            String uri=rs.getString(2);
            if (hds.runUpdate(psLock, id) > 0){
              conn.commit();
              return uri;
            }
          }
          return null;
        }
      );
    }
  }

  /**
   * Gets a large number of URIs for crawling at once. These are marked as deleted
   * immediately, so if they are not crawled, they are effectively lost.
   */
  void getNextURIs(String site, int limit, java.util.function.Consumer<String> consumer) throws Exception {
    if (log.is(2))
      log.date().add("MyDB.getNextURIs() ", site);
    String
      lockSQL=
        "update url_queue set locked=true, deleted=true where (id between ? and ?) and locked=false and deleted=false"
      ,
      selectSQL=
        "select id, uri from url_queue uq where uq.site=? and uq.locked=false and uq.deleted=false order by id"
      ;
    try (
        Connection conn=hds.getTransaction();
        PreparedStatement psQuery=hds.prepare(conn, selectSQL, site)
      ){
      lockSite(conn, site);
      // We MUST use a transaction & set these parameters as such or the driver will
      // try to load the entire resultset into memory on our first rs.next():
      // https://jdbc.postgresql.org/documentation/83/query.html#query-with-cursor
      psQuery.setFetchSize(2);
      psQuery.setFetchDirection(ResultSet.FETCH_FORWARD);
      //
      hds.withQuery(
        psQuery,
        rs -> {
          Object minId=null, maxId=null;
          int found=0;
          while (rs.next() && found<limit-1) {
            found++;
            maxId=rs.getObject(1);
            if (minId==null)
              minId=maxId;
            String uri=rs.getString(2);
            consumer.accept(uri);
          }
          if (minId!=null && maxId!=null)
            hds.runUpdate(conn, lockSQL, minId, maxId);
          conn.commit();
        }
      );
    }
  }

  /** FIXME this should probably just go. Both getNextURI() & getNextURIs() mark the records as deleted immediately. */
  boolean complete(String site, String uri) throws Exception {
    try (Connection conn=hds.getTransaction()) {
      lockSite(conn, site);
      return hds.runUpdate(
        conn,
        "update url_queue set deleted=true where locked=true and site=? and uri=?",
        site, uri
      )==1;
    }
  }
  private void lockSite(Connection conn, String site) throws Exception {
    hds.runUpdate(conn, "update site_lock set locked=not locked where site=?", site);
  }

}
