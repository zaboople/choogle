package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.tmotte.common.text.Outlog;

/**
 * Crawls a group of web sites in parallel, waiting for all to finish.
 *
 * Should be thread-safe.
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
  private final SiteCounter siteCounter=new SiteCounter(); // inner class below

  /** Convenience shortcut to new SiteCrawler(...).crawl(uris) */
  public static void crawl(
      SiteConnectionFactory factory, List<String> uris, long limit, Outlog log, boolean cacheResults
    ) throws Exception {
    new WorldCrawler(factory, limit, log, cacheResults).crawl(uris);
  }

  /**
   * Creates a new WorldCrawler.
   * @param factory Used by SiteCrawlers to obtain connections to sites
   * @param limit The # of pages to crawl per site.
   * @param log A logger to be used during crawling.
   * @param cacheResults Generally should be true; set to false when using choogle to load test a server
   *   so that we don't run out of memory storing lots of URLs in our cache.
   */
  public WorldCrawler(SiteConnectionFactory factory, long limit, Outlog log, boolean cacheResults){
    this.connFactory=factory;
    this.limit=limit;
    this.log=log;
    this.cacheResults=cacheResults;
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  /**
   * Crawls the given list of URIs; since choogle is non-blocking, this will return immediately.
   * @param uris The web sites to crawl. Redirects will be followed as necessary, such as when
   *   foo.org redirects to www.foo.org.
   */
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
        this::onClose,
        limit,
        log,
        cacheResults
      );
      sc.start(uri);
    }
  }

  /** Invoked by callback from SiteCrawler on connection close */
  private void onClose(SiteCrawler sc) {
    logOnClose(sc);
    siteCounter.siteDone(sc);
  };

  private class SiteCounter implements Runnable { //FIXME needs its own file
    private List<SiteCrawler> sites=new ArrayList<>(30);
    private HashSet<Object> already=new HashSet<>();
    private int siteCount=0;
    private int sitesDone=0;
    public SiteCounter() {
      new Thread(this, "Choogle Watcher Thread").start();
    }
    synchronized void add(int more) {
      siteCount+=more;
    }
    synchronized void siteDone(SiteCrawler sc) {
      Object hashNote=sc.getConnectionKey();
      if (!already.contains(hashNote)) {
        already.add(hashNote);
        sites.add(sc);
        this.notify();
      }
    }
    public void run() {
      List<SiteCrawler> recrawls=new ArrayList<SiteCrawler>(5);
      HashSet<String> alreadyRetried=new HashSet<String>();
      boolean allDone = false;
      while (true) {

        synchronized(this) {
          // Wait for notification from a site:
          try {this.wait();}
            catch (InterruptedException e) {
              e.printStackTrace();
            }

          // Now schedule recrawls and find out if we're done:
          for (SiteCrawler sc: sites) {
            if (log.is(1)) logReconnectCheck(sc);
            if (!alreadyRetried.contains(sc.getConnectionKey())){
              alreadyRetried.add(sc.getConnectionKey());
              if (sc.needsReconnect())
                recrawls.add(sc);
              else
                allDone=incrementDone();
            }
          }
          sites.clear();
        }

        // Recrawl as asked; if that doesn't happen, it's a failure:
        for (SiteCrawler sc: recrawls){
          boolean b=false;
          try {
            logReconnect(sc);
            b=sc.reconnectIfUnfinished();
          } catch (Exception e) {
            e.printStackTrace();
          }
          if (!b) {
            logReconnectFail(sc);
            allDone=incrementDone();
          }
        }

        // Reset state:
        recrawls.clear();

        // Bail if all done:
        if (allDone) {
          logDone();
          try {connFactory.finish();} catch (Exception e) {e.printStackTrace();}
          return;
        }
      }
    }
    private synchronized boolean incrementDone() {
      sitesDone++;
      logSiteDone(sitesDone, siteCount);
      return sitesDone==siteCount;
    }
  }

  private void logOnClose(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc.getConnectionKey()).add(" CONNECTION CLOSE DETECTED").lf();
  }
  private void logReconnectCheck(SiteCrawler sc) {
    log.date().add(sc).add(" CHECKING FOR RECONNECT")
      .lf();
  }
  private void logReconnect(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc).add(" ATTEMPTING RECONNECT/REDIRECT")
        .lf();
  }
  private void logReconnectFail(SiteCrawler sc) {
    log.date().add("ERROR: Asked for recrawl and didn't: ").add(sc)
      .lf();
  }
  private void logSiteDone(int completed, int count) {
    if (log.is(1))
      log.date()
        .add("COMPLETED ").add(completed)
        .add(" OF ").add(count).add(" SITES")
        .lf();
  }
  private void logDone() {
    if (log.is(1))
      try {
        log.date()
          .add(
            String.format("Completed in %d ms", System.currentTimeMillis()-startTime)
          )
          .add("... cleaning up...")
          .lf();
      } catch (Exception e) {
        e.printStackTrace();
      }

  }

}
