package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.tmotte.common.text.Outlog;

/**
 * Crawls a group of web sites in parallel, waiting for all to finish.
 *
 * The good news is that WorldCrawler is HTTP-library independent. The bad news
 * is we have a rather nasty inheritance mess with WorldCrawler & SiteCrawler both forming
 * partial implementations that must be filled in the rest of the way using
 * your chosen library, which right now is Netty - i.e. refer to NettyWorldCrawler.
 *
 * Also, this stuff is generally not thread-safe. Fixing that is easy for WorldCrawler
 * (one variable), but SiteCrawler is intrinsically designed for not-threadsafe because
 * that's what non-blocking I/O is really all about.
 */
public class WorldCrawler  {

  //////////////////////////////////
  // PRIVATE STATE & CONSTRUCTOR: //
  //////////////////////////////////

  private final long startTime=System.currentTimeMillis();
  private final long limit;
  private final Outlog log;
  private final boolean cacheResults;
  private final SiteConnectionFactory connFactory;
  private SiteCounter siteCounter=new SiteCounter(); // inner class below

  public static void crawl(
      SiteConnectionFactory factory, List<String> uris, long limit, Outlog log, boolean cacheResults
    ) throws Exception {
    new WorldCrawler(factory, limit, log, cacheResults).crawl(uris);
  }

  public WorldCrawler(SiteConnectionFactory factory, long limit, Outlog log, boolean cacheResults){
    this.connFactory=factory;
    this.limit=limit;
    this.log=log;
    this.cacheResults=cacheResults;
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public void crawl(List<String> uris) throws Exception {
    siteCounter.add(uris.size());
    start(uris);
  }


  ////////////////////////
  // PRIVATE FUNCTIONS: //
  ////////////////////////

  private void start(List<String> uris) throws Exception {
    for (String uri : uris) {
      if (!uri.startsWith("http"))
        uri="http://"+uri;
      SiteCrawler sc=new SiteCrawler(
        connFactory,
        (crawler -> onClose(crawler)),
        limit,
        log,
        cacheResults
      );
      sc.start(uri);
    }
  }

  private void onClose(SiteCrawler sc) {
    siteCounter.siteDone();
    if (siteCounter.done())
      try {
        log.date().add(
          String.format("Completed in %d ms", System.currentTimeMillis()-startTime)
        );
        connFactory.finish();
      } catch (Exception e) {
        e.printStackTrace();
      }
  };

  private class SiteCounter {
    private int siteCount=0;
    private int sitesDone=0;
    synchronized void add(int more) {
      siteCount+=more;
    }
    synchronized void siteDone() {
      sitesDone++;
      if (log.is(1))
        log.date()
          .add("COMPLETED ").add(sitesDone)
          .add(" OF ").add(siteCount).add(" SITES").lf();
    }
    public synchronized boolean done() {
      return siteCount==sitesDone;
    }
  }

}
