package org.tmotte.choogle.pagecrawl;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.tmotte.common.text.Outlog;

public class MyDBTest {
  public static void main(String[] args) throws Exception {
    test();
  }

  private static void test() throws Exception {
    Outlog log = new Outlog().setLevel(1);
    MyDB db=new MyDB(log, true);
    String site="hello.com";
    db.truncate(site);
    db.establish(site);

    final AtomicInteger count=new AtomicInteger();
    int urlCount=3000;
    int readerThreads=1, writerThreads=1;
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
            if (log.is(1))
              log.add("** DONE PUTTER ** ", i, " ", myCount);
          }
        )
      )
      ,
      getters=streamIt(
        readerThreads,
        i-> runnable(
          ()->{
            String s;
            int threadCount=0;
            while (
                (s=db.getNextURI(site))!=null
              ){
              if (log.is(2))
                log.add(i, " ->", s);
              count.incrementAndGet();
              db.complete(site, s);
              threadCount++;
            }
            if (log.is(1))
              log.add("** DONE GETTER ** ", i, " ", threadCount);
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

    log.lf().add(
      "Pulled: "+count.get()+" in: "+timeTaken+" seconds = "+
      (
        ((double)count.get()) / timeTaken
      )+ " urls/second "
    );
    log.lf().add("Outstanding: "+db.getScheduledSize(site));
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
    return Stream.iterate(1, x->x+1)
      .limit(threadLimit)
      .map(
        i->consumer.apply(i)
      )
      .map(r -> new Thread(r))
      .collect(Collectors.toList());
  }

}