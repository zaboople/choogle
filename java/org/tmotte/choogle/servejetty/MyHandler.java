package org.tmotte.choogle.servejetty;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.util.Random;
import java.io.PrintWriter;
import org.tmotte.choogle.chug.JustCompute;


public class MyHandler implements IMyHandler {
  JustCompute jc=new JustCompute();

  public void handle(AsyncContext aCtx, MyAsyncProcessor asyncProcessor) throws Exception {
    ServletRequest request = aCtx.getRequest();
    System.out.println(request.getParameter("x"));
    HttpServletResponse response = (HttpServletResponse)aCtx.getResponse(); //FIXME bad interface

    response.setContentType("text/html; charset=utf-8");
    response.setStatus(HttpServletResponse.SC_OK);
    PrintWriter out = response.getWriter();
    out.println("<html><body>Hello " + jc.getNext() + "</body></html>");

    aCtx.complete();
  }
}