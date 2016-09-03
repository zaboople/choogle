package org.tmotte.choogle.service;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.tmotte.common.jettyserver.MyHandler;


/**
 * This is an attempt to verify that
 * - Our web server is not pathetic
 * - Our database server is not pathetic
 */
public class LoadTest implements MyHandler {
  private LoadTestHTML generator=new LoadTestHTML();
  private int debugLevel=1;
  private final DataSource dataSource;

  public LoadTest(int debugLevel, boolean useDataSource) throws Exception {
    this.debugLevel=debugLevel;
    dataSource=useDataSource
      ?DBConfig.getDataSource(debugLevel)
      :null;
  }

  public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {

    // Set content type and get writer going:
    response.setContentType("text/html; charset=utf-8");
    PrintWriter out = response.getWriter();

    // Get path info:
    String path=request.getPathInfo();
    System.out.append("Request: ").append(path).append("\n");
    if (path.equals("/favicon.ico")) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // Get requested index:
    String indexStr=path.substring(
      path.lastIndexOf("/")
    );
    long index=1;
    Exception parseFail=null;
    if (indexStr.length()>1)
      try {
        index=Long.parseLong(indexStr.substring(1));
      } catch (Exception e) {
        parseFail=e;
      }
    long nextIndex=index-1;

    if (dataSource != null) doDatabase(index);

    // Write response:
    // FIXME this behaves badly when we blow up; it doesn't close the http connection.
    response.setStatus(HttpServletResponse.SC_OK);
    try {
      generator.makeContent(out, parseFail, index);
    } finally {
      out.close();
    }
  }

  private void doDatabase(long index) throws Exception {
    // Database things:
    Connection conn = dataSource.getConnection();
    try {
      if (dataSource!=null && index % 100 == 0) {
        if (debugLevel > 1)
          System.out.println("INSERTING");
        String sql=
            "insert into myschema.hits (create_date, resource_type, resource_name, hits) "
          +" select ?, ?, ?, ? "
          +" where not exists ( "
          +"   select 1 from myschema.hits where resource_type=? and resource_name=?  "
          +" )"
          ;
        PreparedStatement ps=conn.prepareStatement(sql);
        String resourceName=String.valueOf(index), resourceType="load test";
        int z=0;
        ps.setObject(++z, new java.sql.Date(System.currentTimeMillis()));
        ps.setObject(++z, resourceType);
        ps.setObject(++z, resourceName);
        ps.setObject(++z, 1);
        ps.setObject(++z, resourceType);
        ps.setObject(++z, resourceName);
        int inserted = ps.executeUpdate();
        ps.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      conn.close();
    }
  }

}