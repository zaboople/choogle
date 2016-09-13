package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
public abstract class WorldCrawler  {

  //////////////////////////////////
  // PRIVATE STATE & CONSTRUCTOR: //
  //////////////////////////////////

  private final long limit;
  private final int debugLevel;
  private final boolean cacheResults;
  private SiteCounter siteCounter=new SiteCounter(); // inner class below

  public WorldCrawler(long limit, int debugLevel, boolean cacheResults){
    this.limit=limit;
    this.debugLevel=debugLevel;
    this.cacheResults=cacheResults;
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public void crawl(List<String> uris) throws Exception {
    siteCounter.add(uris.size());
    start(uris);
  }


  ///////////////////////////////////
  // Required protected functions: //
  ///////////////////////////////////

  /**
   * All of these parameters are "pass-thru" items that should be handed
   * to the SiteCrawler without interference. One borrowable item, however,
   * is debugLevel, which can be used as a general indicator for debug noise
   * as per elsewhere.
   */
  protected abstract SiteCrawler createSiteCrawler(
      Consumer<SiteCrawler> callOnComplete,
      long limit,
      int debugLevel,
      boolean cacheResults
    ) throws Exception;

  /**
   * This will be called when all sites have been crawled
   * and we are ready to shut down.
   */
  protected abstract void finish();

  ////////////////////////
  // PRIVATE FUNCTIONS: //
  ////////////////////////

  private void start(List<String> uris) throws Exception {
    for (String uri : uris) {
      if (!uri.startsWith("http"))
        uri="http://"+uri;
      SiteCrawler sc=createSiteCrawler(
        (crawler -> onClose(crawler)),
        limit,
        debugLevel,
        cacheResults
      );
      sc.start(uri);
    }
  }

  private void onClose(SiteCrawler sc) {
    siteCounter.siteDone();
    if (siteCounter.done())
      finish();
  };

  private class SiteCounter {
    private int siteCount=0;
    private int sitesDone=0;
    synchronized void add(int more) {
      siteCount+=more;
    }
    synchronized void siteDone() {
      sitesDone++;
      if (debugLevel > 1)
        System.out.append("COMPLETED ")
          .append(String.valueOf(sitesDone))
          .append(" OF ")
          .append(String.valueOf(siteCount))
          .append(" SITES");
    }
    public synchronized boolean done() {
      return siteCount==sitesDone;
    }
  }

}
