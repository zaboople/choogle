package org.tmotte.choogle.servejetty;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

public class MyJettyServer {
  public static void serve() throws Exception  {
    Server server = new Server(8080);

    // Doing it the servlet way:
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    handler.addServletWithMapping(Chervlet.class, "/*");

    // Doing it the other way:
    //server.setHandler(new ChoogleHandler());

    server.start();
    server.dumpStdErr();
    server.join();
  }
}