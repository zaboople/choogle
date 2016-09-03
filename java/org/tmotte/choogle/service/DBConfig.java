package org.tmotte.choogle.service;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.util.Map;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

/**
 * Relies on environment variables to configure a postgres JDBC DataSource.
 */
class DBConfig  {

  static DataSource getDataSource(int debugLevel) throws Exception {
    ComboPooledDataSource cpds=new ComboPooledDataSource();
    Map<String, String> env = System.getenv();
    String
      host        = getEnvString(env, "JDBC_HOST", "0.0.0.0"),
      database    = getEnvString(env, "JDBC_DB",      "mydb"),
      user        = getEnvString(env, "JDBC_USER",    "user"),
      pass        = getEnvString(env, "JDBC_PASS",    "pass");

    String
      driverClass = "org.postgresql.Driver",
      jdbcURL = String.format("jdbc:postgresql://%s/%s", host, database);

    if (debugLevel > 0)
      System.out.append("Connecting with JDBC driver: ")
        .append(driverClass)
        .append("\n")
        .append(" and jdbc URL: ")
        .append(jdbcURL)
        .append("\n");

    cpds.setDriverClass(driverClass);
    cpds.setJdbcUrl(jdbcURL);
    cpds.setUser(user);
    cpds.setPassword(pass);
    cpds.getConnection().close();

    return cpds;
  }
  private static String getEnvString(Map<String,String> env, String name, String defaultValue) {
    String res = env.get(name);
    if (res != null)
      return res;
    return defaultValue;
  }
}
