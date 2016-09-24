package org.tmotte.choogle.pagecrawl;
import java.net.URI;

/**
 * Represents a minimal implementation necessary for outgoing requests to crawled sites.
 * An actual instance will need a reference to SiteCrawler so that it can send messages
 * about page events, i.e. start, body, end.
 */
public interface SiteReader {

  public void onClose(SiteConnection sc) throws Exception;
  public void pageStart(
    URI currentURI,
    boolean onHead,
    int statusCode,
    String contentType,
    String eTag,
    String lastModified,
    boolean closed,
    boolean redirected,
    String locationHeader
  ) throws Exception;
  public void pageBody(URI currentURI, String s) throws Exception;
  public void pageComplete(URI currentURI, boolean onHead) throws Exception;
}