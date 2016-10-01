package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import org.tmotte.choogle.pagecrawl.AnchorReader;
import org.tmotte.common.net.Link;
import org.tmotte.common.text.Outlog;


/**
 * The idea is to follow all the redirects so that we can start kicking hard.
 */
class SiteStarter implements SiteReader{

  // NON-CHANGING STATE:
  private final Outlog log;
  private final SiteConnectionFactory connFactory;
  private final long limit;
  private final Consumer<SiteStarter> callOnClose;
  private final SiteCrawlerDebug debugger=new SiteCrawlerDebug();

  // SITE STATE:
  private String sitehost;
  private String sitescheme;
  private int siteport;
  private SiteConnection siteConnection;
  private String key;
  private int index=0;
  private boolean found=false;

  // RAPIDLY CHANGING STATE:
  private URI currentURI=null, redirectTo=null;
  private int siteRedirectCount=0;
  private boolean lastWasSiteRedirect=false;
  private URI uriInFlight;

  public SiteStarter(
      Outlog log, SiteConnectionFactory connFactory, long limit, Consumer<SiteStarter> callOnClose
    ){
    this.log=log;
    this.connFactory=connFactory;
    this.limit=limit;
    this.callOnClose=callOnClose;
    debugger.log=log;
  }

  ///////////////////////
  // SITE-LEVEL API's: //
  ///////////////////////

  final SiteStarter start(String initialURI) throws Exception {
    return start(getURI(initialURI));
  }

  /**
   * Checks the URI to see if we get a redirect.
   */
  final SiteStarter start(URI initialURI) throws Exception {
    this.sitehost=initialURI.getHost();
    this.sitescheme=initialURI.getScheme();
    this.siteport=initialURI.getPort();
    this.index++;
    this.key=sitehost+":"+siteport+"-S"+index;
    debugger.sitename=this.key;
    this.currentURI=initialURI;
    redirectTo=null;
    debugger.doHead(currentURI);
    this.siteConnection=connFactory.get(this, initialURI);
    siteConnection.doHead(currentURI);
    return this;
  }

  /** Just prints the host/port/etc that we're crawling */
  public String toString() {
    return getConnectionKey();
  }

  /**
   * This should uniquely identify our connection, i.e.
   * "host:port-index". Every time we open a new connection
   * we need to give it a unique key, which is why we add the "-index",
   * a simple int that is incremented every time we open a connection.
   */
  String getConnectionKey() {
    return key;
  }
  long getLimit() {
    return limit;
  }
  URI getRedirectURI() {
    return redirectTo;
  }
  URI getCurrentURI() {
    return currentURI;
  }

  ////////////////
  // OVERRIDES: //
  ////////////////

  public @Override void onClose(SiteConnection sc) throws Exception {
    callOnClose.accept(this);
  }


  /**
   * Should be called when headers from the server arrive.
   * @return false if we don't want the data. A HEAD request will
   *   tell us what is coming before a GET to crawl the page, so
   *   the latter should be expected to return true most of the time.
   */
  public @Override final boolean pageStart(
      URI currentURI,
      boolean onHead,
      int statusCode,
      String contentType,
      String eTag,
      String lastModified,
      boolean closed,
      boolean redirected,
      String locationHeader
    ) throws Exception {
    if (redirected) siteRedirectCount++;
    if (log.is(2))
      debugger.headers(
        currentURI, statusCode, contentType,
        eTag, lastModified, closed, redirected,
        locationHeader
      );
    if (redirected && locationHeader!=null)
      redirectTo=Link.getURI(currentURI, locationHeader);
    else
      found=String.valueOf(statusCode).startsWith("2");
    return false;
  }

  public @Override final void pageBody(URI currentURI, String s) throws Exception{}

  /**
   * Should be called when the page is complete.
   * @return True if we have more work to do and want to keep our connection alive.
   */
  public @Override final void pageComplete(URI currentURI, boolean onHead) throws Exception {
    if (redirectTo==null || !Link.sameSite(currentURI, redirectTo))
      closeConnection();
    callOnClose.accept(this);
  }


  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////


  private void closeConnection() throws Exception {
    if (siteConnection!=null) {
      if (log.is(1)) debugger.closing();
      SiteConnection temp=siteConnection;
      siteConnection=null;
      temp.close();
    }
  }

  private static URI getURI(String uri) throws Exception {
    URI realURI=Link.getURI(uri);
    if (realURI==null)
      throw new RuntimeException("Could not interpret URI: "+uri);
    return realURI;
  }

}
