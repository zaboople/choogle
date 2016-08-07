package org.tmotte.choogle.servejetty.framework;
import java.util.Queue;
import javax.servlet.ServletContext;
import javax.servlet.AsyncContext;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

/**
 * This implements our service using "Async" requests.
 * It hands most of the work off to MyAsyncProcessor.
 */
public class MyServlet extends HttpServlet implements java.io.Serializable {

  public static final long serialVersionUID = 1;
  private final MyAsyncProcessor masp;

  public void service(HttpServletRequest request, HttpServletResponse response) {
    //System.out.print("A");
    masp.add(
      request.startAsync(request, response)
    );
  }

  public MyServlet(MyHandler myHandler) {
    masp=new MyAsyncProcessor(myHandler);
    new Thread(masp).start();
  }
}

