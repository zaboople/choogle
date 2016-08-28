package org.tmotte.common.jettyserver;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Starts up a web server that will feed requests to a MyHandler
 */
public class MyJettyServer {

  /**
   * @param myHandler
   *   The MyHandler that will receive request/response objects with
   *   all the headers and good stuff like a traditional servlet.
   * @param async FIXME
   *   If > 0, will try to take advantage of the new Servlet "asynchronous" specification.
   *   Requests will be forked off into a separate thread pool of our own devising. Not
   *   necessarily useful, but interesting.
   * @param port
   *   Just the port we want to listen on. HTTPS not supported sorry.
   */
  public static void serve(MyHandler myHandler, int port, int asyncPoolSize) throws Exception  {
    Server server = new Server(port);

    // Doing it the servlet way:
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    handler.addServletWithMapping(
      new ServletHolder(
        new MyServlet(myHandler, asyncPoolSize)
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
