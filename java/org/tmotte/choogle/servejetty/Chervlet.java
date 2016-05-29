package org.tmotte.choogle.servejetty;
import java.util.Queue;
import javax.servlet.ServletContext;
import javax.servlet.AsyncContext;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

@WebServlet(name="myServlet", urlPatterns={"/slowprocess"}, asyncSupported=true)
public class Chervlet extends HttpServlet implements java.io.Serializable {

  public static final long serialVersionUID = 1;
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    System.out.print("A");
    masp.add(
      request.startAsync(request, response)
    );
  }

  MyAsyncProcessor masp=new MyAsyncProcessor(new MyHandler());
  public Chervlet() {
    new Thread(masp).start();
  }
}

