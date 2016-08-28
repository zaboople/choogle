package org.tmotte.common.jettyserver;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

/** Refer to MyJettyServer */
public interface MyHandler {
  public void handle(HttpServletRequest req, HttpServletResponse res) throws Exception;
}