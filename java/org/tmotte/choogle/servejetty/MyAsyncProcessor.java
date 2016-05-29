package org.tmotte.choogle.servejetty;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.Queue;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;


public class MyAsyncProcessor implements Runnable {

  private final static int runnersCount=12;

  static class MyRunnable implements Runnable {
    private AsyncContext ac;
    private MyAsyncProcessor ma;
    private IMyHandler ih;
    public MyRunnable(MyAsyncProcessor ma, IMyHandler ih) {
      this.ma=ma;
      this.ih=ih;
    }
    public void run() {
      try {
        ih.handle(ac, ma);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        this.ac=null;
        ma.runners.add(this);
      }
    }
    public MyRunnable set(AsyncContext ac){
      if (ac==null)
        throw new RuntimeException("WTF ");
      this.ac=ac;
      return this;
    }
  }
  private ThreadFactory tf=new ThreadFactory() {
    int index=0;
    ThreadFactory parent=Executors.defaultThreadFactory();
    public Thread newThread(Runnable r){
      index++;
      Thread t=parent.newThread(r);
      t.setName("Async "+index);
      return t;
    }
  };

  private ArrayBlockingQueue<AsyncContext> jobs = new ArrayBlockingQueue<>(100);
  private ArrayBlockingQueue<MyRunnable> runners = new ArrayBlockingQueue<>(runnersCount);
  private Executor executor = Executors.newFixedThreadPool(2, tf);


  public MyAsyncProcessor(IMyHandler imh) {
    for (int i=0; i<runnersCount; i++)
      runners.add(new MyRunnable(this, imh));
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

}
