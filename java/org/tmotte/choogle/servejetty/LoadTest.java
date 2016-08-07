package org.tmotte.choogle.servejetty;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.util.Enumeration;
import java.util.Random;
import java.io.PrintWriter;
import org.tmotte.choogle.service.LoadTestHTML;
import org.tmotte.choogle.servejetty.framework.MyHandler;

public class LoadTest implements MyHandler {
  private LoadTestHTML generator=new LoadTestHTML();
  private int debugLevel=1;

  public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String path=request.getPathInfo();
    System.out.append("Request: ").append(path).append("\n");
    if (path.equals("/favicon.ico"))
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    else {
      if (debugLevel > 1)
        for (Enumeration<String> names=request.getHeaderNames(); names.hasMoreElements();){
          String name=names.nextElement();
          System.out.append(name).append("=").append(request.getHeader(name)).append("\n");
        }
      response.setContentType("text/html; charset=utf-8");
      response.setStatus(HttpServletResponse.SC_OK);
      PrintWriter out = response.getWriter();
      generator.makeContent(out, request.getPathInfo());
    }
  }
}