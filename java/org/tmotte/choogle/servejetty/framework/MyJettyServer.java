package org.tmotte.choogle.servejetty.framework;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * This boots our framework. It creates an instance of MyServlet using
 * a provided MyHandler, and boots up a jetty server running the servlet.
 */
public class MyJettyServer {
  public static void serve(MyHandler myHandler) throws Exception  {
    Server server = new Server(8080);

    // Doing it the servlet way:
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    handler.addServletWithMapping(
      new ServletHolder(
        new MyServlet(myHandler)
      ),
      "/*"
    );

    // Doing it the other way:
    //server.setHandler(new ChoogleHandler());

    server.start();
    server.dumpStdErr();
    server.join();
  }
}