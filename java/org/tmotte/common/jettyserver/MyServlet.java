package org.tmotte.common.jettyserver;
import java.util.Queue;
import javax.servlet.ServletContext;
import javax.servlet.AsyncContext;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

/**
 * Does some internal dirty work the Servlet way.
 */
class MyServlet extends HttpServlet implements java.io.Serializable {

  public static final long serialVersionUID = 1;

  private final MyAsyncProcessor masp;
  private final MyHandler myHandler;

  public MyServlet(MyHandler myHandler, int asyncPoolSize) {
    if (asyncPoolSize > 0) {
      this.myHandler=null;
      masp=new MyAsyncProcessor(myHandler, asyncPoolSize);
      new Thread(masp).start();
    }
    else {
      this.masp=null;
      this.myHandler=myHandler;
    }
  }

  public void service(HttpServletRequest request, HttpServletResponse response) {
    if (masp!=null)
      masp.add(
        request.startAsync(request, response)
      );
    else
      try {
        myHandler.handle(request, response);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
  }

}

