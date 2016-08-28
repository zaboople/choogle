package org.tmotte.common.jettyserver;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * I am currently not using this, but it's just another way to hook into
 * jetty; instead I use MyServlet, which gets hooked in via reflection.
 */
public class OtherHandler extends AbstractHandler {

  private MyHandler myHandler;

  public OtherHandler(MyHandler handler) {
    this.myHandler=handler;
  }

  public void handle(
    String target, Request baseRequest,
    HttpServletRequest request, HttpServletResponse response
  ) throws IOException, ServletException {
    try {
      myHandler.handle(request, response);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    baseRequest.setHandled(true);
  }
}
