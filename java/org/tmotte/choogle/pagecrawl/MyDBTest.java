package org.tmotte.choogle.pagecrawl;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.tmotte.common.text.Outlog;

/**
 * This seems to run optimally with 1 readers and 4 writers.
 */
public class MyDBTest {
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: java org.tmotte.choogle.pagecrawl.MyDBTest <logLevel> <readThreads> <writeThreads>");
    }
    int logLevel=Integer.parseInt(args[0]);
    int readThreads=Integer.parseInt(args[1]);
    int writeThreads=Integer.parseInt(args[2]);
    test(logLevel, readThreads, writeThreads);
  }

  private static void test(int logLevel, int readerThreads, int writerThreads) throws Exception {
    Outlog log = new Outlog().setLevel(logLevel);
    MyDB db=new MyDB(log, true);
    String site="hello.com";
    db.truncate(site);
    db.establish(site);

    final AtomicInteger count=new AtomicInteger();
    final int urlCount=10000;
    final int urlsPerWriter=urlCount/writerThreads;
    final int overlap=1000;
    final int urisPerRead=300;

    List<Thread>
      putters=makeThreads(
        writerThreads,
        threadIndex-> runnable(
          ()->{
            int firstURLIndex=(threadIndex-1) * urlsPerWriter;
            int myCount=db.addURIs(
              site,
              Stream.iterate(firstURLIndex, x->x+1)
                .limit(urlsPerWriter+overlap)
                .map(x->"/putter/"+x)
            );
            if (log.is(1))
              log.add("** DONE PUTTER ** ", threadIndex, " ", myCount);
          }
        )
      )
      ,
      getters=makeThreads(
        readerThreads,
        i-> runnable(
          ()->{
            int thisCount=0;
            int breaks=5;
            List<String> uris=new java.util.ArrayList<>();
            while (true){
              db.getNextURIs(site, urisPerRead, s -> uris.add(s));

              if (uris.size()==0)  breaks--;
              else if (breaks<5) breaks++;
              if (breaks<=0) break;

              if (log.is(2))
                log.add("GETTER ", i, " URIS: ", uris.size());
              for (String s: uris) {
                if (log.is(3))
                  log.add(i, " ->", s);
                count.incrementAndGet();
                thisCount++;
              }
              uris.clear();
            }
            if (log.is(1))
              log.add("** DONE GETTER ** ", i, " ", thisCount);
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

  private static List<Thread> makeThreads(int threadLimit, Function<Integer, Runnable> consumer) {
    return Stream.iterate(1, x->x+1)
      .limit(threadLimit)
      .map(
        i->consumer.apply(i)
      )
      .map(r -> new Thread(r))
      .collect(Collectors.toList());
  }

}