package org.tmotte.common.db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * This gives us some shortcuts for use with Connections, transactions,
 * PreparedStatements, and ResultSets, mostly for use with lambda
 * expressions. Benefits include populating PreparedStatements with
 * varargs Object arrays and auto-close/auto-commit of resources/transactions.
 */
public class HotRodDataSource {
  public static interface TxnUser {
    public void apply(Connection conn) throws Exception;
  }
  public static interface TxnResultUser<T> {
    public T apply(Connection conn) throws Exception;
  }
  public static interface WithQuerierResult<T> {
    T apply(ResultSet rs) throws Exception;
  }
  public static interface WithQuerier {
    void apply(ResultSet rs) throws Exception;
  }

  DataSource ds;
  public HotRodDataSource(DataSource ds) {
    this.ds=ds;
  }
  public DataSource getDataSource() throws Exception {
    return ds;
  }
  public Connection getConnection() throws Exception {
    return ds.getConnection();
  }
  public Connection getTransaction() throws Exception {
    Connection c=getConnection();
    c.setAutoCommit(false);
    return c;
  }
  public void withTxn(TxnUser user) throws Exception {
    try (Connection conn=ds.getConnection()){
      conn.setAutoCommit(false);
      try {
        user.apply(conn);
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }
  public <T> T withTxnResult(TxnResultUser<T> user) throws Exception {
    try (Connection conn=ds.getConnection()){
      conn.setAutoCommit(false);
      try {
        T t=user.apply(conn);
        conn.commit();
        return t;
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }
  public int runUpdate(String sql, Object... args) throws Exception {
    try (Connection conn=ds.getConnection()){
      return runUpdate(conn, sql, args);
    }
  }
  public int runUpdate(Connection conn, String sql, Object... args) throws Exception {
    if (args.length==0)
      try (Statement ps=conn.createStatement()){
        return ps.executeUpdate(sql);
      }
    else
      try (PreparedStatement ps=prepare(conn, sql, args)){
        return ps.executeUpdate();
      }
  }
  public int runUpdate(PreparedStatement ps, Object... args) throws Exception {
    prepare(ps, args);
    return ps.executeUpdate();
  }
  public PreparedStatement prepare(Connection conn, String sql, Object... args) throws Exception {
    return prepare(
      conn.prepareStatement(sql), args
    );
  }
  public PreparedStatement prepare(PreparedStatement ps, Object... args) throws Exception {
    for (int i=0; i<args.length; i++)
      ps.setObject(i+1, args[i]);
    return ps;
  }
  public <T> T withQueryResult(PreparedStatement ps, WithQuerierResult<T> con) throws Exception {
    try (ResultSet rs=ps.executeQuery()) {
      return con.apply(rs);
    }
  }
  public void withQuery(PreparedStatement ps, WithQuerier con) throws Exception {
    try (ResultSet rs=ps.executeQuery()) {
      con.apply(rs);
    }
  }

}