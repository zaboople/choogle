package org.tmotte.choogle.servejetty.framework;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.servlet.AsyncContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * This is intended for use with the Servlet AsyncContext feature. It maintains
 * a small thread pool for running requests outside the servlet engine's own
 * thread pool.
 *
 * The value of such a strategy is dubious, but the idea was to take advantage
 * of the whole non-blocking yada yada business.
 */
public class MyAsyncProcessor implements Runnable {

  private final static int runnersCount=12;
  private ArrayBlockingQueue<AsyncContext> jobs = new ArrayBlockingQueue<>(100);
  private ArrayBlockingQueue<MyRunnable> runners = new ArrayBlockingQueue<>(runnersCount);
  private Executor executor = Executors.newFixedThreadPool(
    2,
    new ThreadFactory() {
      int index=0;
      ThreadFactory parent=Executors.defaultThreadFactory();
      public Thread newThread(Runnable r){
        index++;
        Thread t=parent.newThread(r);
        t.setName("Async "+index);
        return t;
      }
    }
  );


  public MyAsyncProcessor(MyHandler imh) {
    for (int i=0; i<runnersCount; i++)
      runners.add(new MyRunnable(runners, imh));
  }
  public void add(AsyncContext asc) {
    jobs.add(asc);
  }
  public void run() {
    while(true){
      try {
        executor.execute(
          runners.take().set(
            jobs.take()
          )
        );
      } catch (InterruptedException ie) {
        System.out.println("Interrupted");
      }
    }
  }


  /**
   * A Runnable wrapper for request handlers.
   */
  private static class MyRunnable implements Runnable {
    private final Queue<MyRunnable> returnTo;
    private final MyHandler ih;
    private AsyncContext context;

    public MyRunnable(Queue<MyRunnable> returnTo, MyHandler ih) {
      this.returnTo=returnTo;
      this.ih=ih;
    }
    public void run() {
      try {
        ih.handle(
          (HttpServletRequest)context.getRequest(),
          (HttpServletResponse)context.getResponse()
        );
        context.complete();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        this.context=null;
        returnTo.add(this);
      }
    }
    public MyRunnable set(AsyncContext context){
      if (context==null)
        throw new RuntimeException("Input is null");
      this.context=context;
      return this;
    }
  }
}
