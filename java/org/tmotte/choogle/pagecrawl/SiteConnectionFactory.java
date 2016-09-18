package org.tmotte.choogle.pagecrawl;
import java.net.URI;

/**
 * Responsible for providing SiteConnection instances to SiteCrawler.
 */
public interface SiteConnectionFactory {
  /**
   * Obtains an http connection to a web site represented by uri.
   */
  public SiteConnection get(SiteCrawler sc, URI uri) throws Exception ;
  /**
   * Gives the factory a chance to clean up thread pools etc. when we
   * shut down crawling.
   */
  public void finish() throws Exception ;
}
