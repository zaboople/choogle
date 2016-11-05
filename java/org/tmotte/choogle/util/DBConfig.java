package org.tmotte.choogle.util;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Relies on environment variables to configure a postgres JDBC DataSource.
 */
public class DBConfig  {

  //Derby note: You define the system directory when Derby starts up
  // by specifying a Java system property called derby.system.home.
  //Derby note: We're not using derby because it sucks.

  private final ComboPooledDataSource cpds=new ComboPooledDataSource();

  public DBConfig(int debugLevel) throws Exception {

    // Get configuration:
    Map<String, String> env = System.getenv();
    String
      postgresDriver="org.postgresql.Driver",
      postgresURL="jdbc:postgresql://192.168.99.100/mydb",
      derbyDriver="org.apache.derby.jdbc.EmbeddedDriver",
      derbyURL="jdbc:derby:./build/testdb;create=true"
      ;
    String
      driverClass = getEnvString(env, "JDBC_DRIVER",  postgresDriver),
      url         = getEnvString(env, "JDBC_URL",     postgresURL),
      user        = getEnvString(env, "JDBC_USER",    "user"),
      pass        = getEnvString(env, "JDBC_PASS",    "pass");
    if (debugLevel > 0)
      System.out.append("Connecting with JDBC driver: ")
        .append(driverClass)
        .append("\n")
        .append(" and jdbc URL: ")
        .append(url)
        .append("\n");

    // Feed config into the pool. Note that by default it's ridiculously
    // stupidly patient and will take forever to realize the DB isn't in
    // town today, so we've toned it down to a more reasonable level:
    cpds.setDriverClass(driverClass);
    cpds.setJdbcUrl(url);
    cpds.setUser(user);
    cpds.setPassword(pass);
    cpds.setAcquireRetryAttempts(2);
    cpds.setCheckoutTimeout(2000);

    // Verify it works:
    cpds.getConnection().close();
  }
  public DataSource get(){
    return cpds;
  }
  public Connection getConnection() throws Exception {
    return cpds.getConnection();
  }
  public void close() throws Exception {
    //cpds.close();
  }
  private static String getEnvString(Map<String,String> env, String name, String defaultValue) {
    String res = env.get(name);
    if (res != null)
      return res;
    return defaultValue;
  }
}
