package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.List;
import org.tmotte.common.text.Outlog;

/**
 * Crawls a group of web sites in parallel, waiting for all to finish.
 *
 * Should be thread-safe.
 */
public class WorldCrawler  {

  // This is mostly just a proxy to WorldWatcher, but WorldWatcher has a lot of
  // synchronized stuff and we don't want it exposed to anyone that might try to
  // use it for locking.
  private final WorldWatcher worldWatcher;

  /** Convenience shortcut to new SiteCrawler(...).crawl(...) */
  public static void crawl(
      Outlog log,
      SiteConnectionFactory factory,
      boolean cacheResults,
      Runnable onComplete,
      boolean db,
      boolean dbreset,
      List<String> uris,
      long limit,
      int connsPer
    ) throws Exception {
    new WorldCrawler(log, factory, cacheResults, onComplete, db, dbreset).crawl(uris, limit, connsPer);
  }

  /**
   * Creates a new WorldCrawler.
   * @param log A logger to be used during crawling.
   * @param factory Used by SiteCrawlers to obtain connections to sites
   * @param cacheResults Generally should be true; set to false when using choogle to load test a server
   *   so that we don't run out of memory storing lots of URLs in our cache.
   * @param onComplete Called whenever all currently active site crawls are finished. This is mainly for
   *   cleanup/shutdown; we don't yet have site-by-site callbacks, which is obviously needed (and not hard).
   */
  public WorldCrawler(
      Outlog log, SiteConnectionFactory factory, boolean cacheResults, Runnable onComplete, boolean db, boolean dbreset
    ) throws Exception {
    this.worldWatcher=new WorldWatcher(
      log,
      factory,
      cacheResults,
      onComplete,
      db,
      dbreset
    );
  }

  /**
   * Crawls the given list of URIs; since choogle is non-blocking, this will return immediately.
   * @param uris The web sites to crawl. Redirects will be followed as necessary, such as when
   *   foo.org redirects to www.foo.org, http to https, etc.
   * @param limit The # of pages to crawl per site.
   * @param connsPer The number of connections to use simultaneously per site crawled.
   */
  public void crawl(List<String> uris, long limit, int connsPer) throws Exception {
    worldWatcher.crawl(uris, limit, connsPer);
  }

}
