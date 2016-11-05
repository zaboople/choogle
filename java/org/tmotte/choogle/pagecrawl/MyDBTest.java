package org.tmotte.choogle.pagecrawl;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class MyDBTest {
  public static void main(String[] args) throws Exception {
    test(new MyDB(new org.tmotte.common.text.Outlog(), true));
  }

  private static void test(MyDB db) throws Exception {
    String site="hello.com";
    db.truncate(site);
    db.establish(site);

    final AtomicInteger count=new AtomicInteger();
    int urlCount=3000;
    int readerThreads=1, writerThreads=3;
    int urlsPerWriter=2 * urlCount/writerThreads;

    List<Thread>
      putters=streamIt(
        writerThreads,
        i-> runnable(
          ()->{
            int myCount=db.addURIs(
              site,
              Stream.iterate(1, x->x+1)
                .limit(urlsPerWriter)
                .map(j->"/putter/"+(i/2)+"/"+j)
            );
            System.out.println("\n** DONE PUTTER ** "+i+" "+myCount);
          }
        )
      )
      ,
      getters=streamIt(
        readerThreads,
        i-> runnable(
          ()->{
            String s;
            while (
                (s=db.getNextURI(site))!=null
              ){
              System.out.println(i+" ->"+s);
              count.incrementAndGet();
              db.complete(site, s);
            }
            System.out.println("\n ** DONE GETTER ** "+i);
          }
        )
      );
    Long startTime=System.nanoTime();

    for (Thread t: putters)
      t.start();
    Thread.sleep(100);
    for (Thread t: getters)
      t.start();
    for (Thread t: putters)
      t.join();
    for (Thread t: getters)
      t.join();

    double timeTaken=(double)(System.nanoTime()-startTime) / (1000D*1000D*1000D);

    System.out.println(
      "Pulled: "+count.get()+" in: "+timeTaken+" seconds = "+
      (
        ((double)count.get()) / timeTaken
      )+ " urls/second "
    );
    System.out.println("Outstanding: "+db.getScheduledSize(site));
    db.close();
  }

  private interface Doer {
    void run() throws Exception;
  }
  private static Runnable runnable(Doer d) {
    return ()->{
      try {d.run();} catch (Exception e) {e.printStackTrace();}
    };
  }
  private static void runRunnable(Doer d) {
    runnable(d).run();
  }

  private static List<Thread> streamIt(int threadLimit, Function<Integer, Runnable> consumer) {
    return Stream.iterate(2, x->x+1)
      .limit(threadLimit)
      .map(
        i->consumer.apply(i)
      )
      .map(r -> new Thread(r))
      .collect(Collectors.toList());
  }

}