package org.tmotte.choogle.servejetty.framework;
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
 * jetty; instead I use Chervlet, which gets hooked in via reflection.
 */
public class OtherHandler extends AbstractHandler {
  Random rand=new Random(System.currentTimeMillis());

  public void handle(
    String target, Request baseRequest,
    HttpServletRequest request, HttpServletResponse response
  ) throws IOException, ServletException{
    response.setContentType("text/html; charset=utf-8");
    response.setStatus(HttpServletResponse.SC_OK);
    PrintWriter out = response.getWriter();
    out.println("<html><body>Hello " + rand.nextInt() + "</body></html>");
    baseRequest.setHandled(true);
  }
}
