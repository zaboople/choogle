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

  /** Convenience shortcut to new SiteCrawler(...).crawl(uris) */
  public static void crawl(
      Outlog log, SiteConnectionFactory factory, long limit, boolean cacheResults, List<String> uris, Runnable onComplete
    ) throws Exception {
    new WorldCrawler(log, factory, cacheResults, onComplete).crawl(uris, limit);
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
      Outlog log, SiteConnectionFactory factory, boolean cacheResults, Runnable onComplete
    ){
    this.worldWatcher=new WorldWatcher(
      log,
      factory,
      cacheResults,
      onComplete
    );
  }

  /**
   * Crawls the given list of URIs; since choogle is non-blocking, this will return immediately.
   * @param uris The web sites to crawl. Redirects will be followed as necessary, such as when
   *   foo.org redirects to www.foo.org, http to https, etc.
   * @param limit The # of pages to crawl per site.
   */
  public void crawl(List<String> uris, long limit) throws Exception {
    worldWatcher.crawl(uris, limit);
  }

}
