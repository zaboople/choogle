package org.tmotte.choogle.pagecrawl;
import java.net.URI;

/**
 * A SiteReader receives page/connection events from a SiteConnection. Both SiteCrawler
 * & SiteStarter are SiteReaders.
 */
public interface SiteReader {

  public void onClose(SiteConnection sc) throws Exception; //FIXME we don't need the SiteConnection being sent to us.
  public boolean pageStart(
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