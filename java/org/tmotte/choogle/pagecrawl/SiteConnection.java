package org.tmotte.choogle.pagecrawl;
import java.net.URI;

/**
 * Represents a minimal implementation necessary for outgoing requests to crawled sites.
 * An actual instance will need a reference to SiteCrawler so that it can send messages
 * about page events, i.e. start, body, end.
 */
public interface SiteConnection {

  /**
   * Should close the http connection. This may in turn invoke SiteCrawler.onClose(),
   * although it is not actually necessary to do so since SiteCrawler is already
   * aware of the fact.
   */
  public void close() throws Exception;

  /**
   * Should perform a HEAD request against the URI and call
   * SiteCrawler.pageStart() & pageComplete() accordingly. There is no need
   * to call pageBody(), and pageStart() will return false to say as much.
   */
  public void doHead(URI uri) throws Exception;

  /**
   * Should perform a GET request against the URI and call
   * SiteCrawler.pageStart(), pageBody() & pageComplete() accordingly.
   */
  public void doGet(URI uri) throws Exception;
}