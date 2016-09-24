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

  private final long limit;
  private final Outlog log;
  private final boolean cacheResults;
  private final SiteConnectionFactory connFactory;
  private final SiteWatcher siteWatcher; // inner class below

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
    this.siteWatcher=new SiteWatcher(
      log,
      ()-> {
        try {connFactory.finish();}
        catch (Exception e) {e.printStackTrace();}
      }
    );
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
    siteWatcher.add(uris.size());
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
    siteWatcher.siteDone(sc);
  };

}
