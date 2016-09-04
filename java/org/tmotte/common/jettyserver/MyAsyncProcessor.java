package org.tmotte.common.jettyserver;
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
class MyAsyncProcessor implements Runnable {

  private final ArrayBlockingQueue<AsyncContext> jobs;
  private final ArrayBlockingQueue<MyRunnable> runners;
  private final Executor executor;


  public MyAsyncProcessor(MyHandler imh, int threadPoolSize) {
    jobs = new ArrayBlockingQueue<>(threadPoolSize * 10);

    int runnerCount = threadPoolSize * 2;
    runners = new ArrayBlockingQueue<>(runnerCount);
    for (int i=0; i<runnerCount; i++)
      runners.add(new MyRunnable(runners, imh));

    executor=Executors.newFixedThreadPool(threadPoolSize, new MyThreadFactory());
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
      // The context.complete() is what blows up when we combine HEAD & GET
      // requests on Jetty.
      try {
        HttpServletRequest req=(HttpServletRequest)context.getRequest();
        HttpServletResponse res=(HttpServletResponse)context.getResponse();
        ih.handle(req, res);
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

  private static class MyThreadFactory implements ThreadFactory {
    int index=0;
    ThreadFactory parent=Executors.defaultThreadFactory();
    public Thread newThread(Runnable r){
      index++;
      Thread t=parent.newThread(r);
      t.setName("Async "+index);
      return t;
    }
  }
}
