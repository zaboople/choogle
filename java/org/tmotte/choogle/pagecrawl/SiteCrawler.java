package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.tmotte.choogle.pagecrawl.AnchorReader;
import org.tmotte.common.net.Link;
import org.tmotte.common.text.Outlog;

/**
 * Crawls a single web site until the connection is closed or the desired number of URLs is crawled.
 * This does *not* follow redirects.
 *
 * Note that this class is not threadsafe because it's intended for use with a non-blocking IO library.
 *
 * Testing note: Apache.org is a great place to test connection close, as they tend to limit
 * connection lifetime. Also good for testing content types, as they have lots of PDF's and EMF's and
 * I don't know what's.
 *
 */
class SiteCrawler implements SiteReader {

  // IMMUTABLE STATE:
  private final Outlog log;
  private final SiteConnectionFactory connFactory;
  private final Consumer<SiteCrawler> callOnClose;
  private final SiteCrawlerDebug debugger;
  private final int index;

  // TECHNICALLY IMMUTABLE CONNECTION STATE:
  private String sitehost;
  private String sitescheme;
  private int siteport;
  private SiteConnection siteConnection;
  private String crawlKey;
  private String siteKey;

  // RAPIDLY CHANGING SITE STATE:
  private final SiteState siteState;

  // ULTRA-RAPIDLY CHANGING PAGE STATE:
  private final AnchorReader pageParser;
  private final Collection<String> tempLinks=new HashSet<>(128);
  private boolean pageAccepted=true;
  private URI uriInFlight;
  private int pageSize=0;
  private boolean neverAgain=false;
  private boolean moreConnsAllowed=true;

  public SiteCrawler(
      Outlog log,
      SiteConnectionFactory connFactory,
      SiteState siteState,
      Consumer<SiteCrawler> callOnClose
    ){
    this.log=log;
    this.connFactory=connFactory;
    this.callOnClose=callOnClose;

    // Copy some things from parent crawler:
    this.siteState=siteState;
    this.index=siteState.getNextConnIndex();

    // Non-reuseable stuff:
    // 1. debugger needs a crawlKey that is instance-specific (handled later on)
    // 2. pageParser is not thread-safe:
    this.debugger=new SiteCrawlerDebug(this.log);
    this.pageParser=new AnchorReader(
      tempLinks,
      log.is(4)
        ?x -> log.add(x)
        :null
    );
  }

  ///////////////////////
  // SITE-LEVEL API's: //
  ///////////////////////

  final SiteCrawler start(String initialURI) throws Exception {
    return start(getURI(initialURI));
  }

  /**
   * Starts the crawling process. Called by WorldCrawler & internal
   * crawl restarts.
   */
  final SiteCrawler start(URI initialURI) throws Exception {
    if (neverAgain)
      throw new RuntimeException("Should not have restarted "+this);
    neverAgain=true;
    this.sitehost=initialURI.getHost();
    this.sitescheme=initialURI.getScheme();
    this.siteport=initialURI.getPort();
    this.siteConnection=connFactory.get(this, initialURI);
    this.siteKey=sitehost+":"+siteport;
    this.crawlKey=siteKey+"-C"+index;
    debugger.sitename=this.crawlKey;
    siteState.addInitialPath(initialURI);
    read(initialURI);
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
    return crawlKey;
  }
  /**
   * Uniquely identify the site by host + port
   */
  String getSiteKey() {
    return siteKey;
  }
  /**
   * A shareable object representing the crawl state of the site.
   */
  SiteState getSiteState() {
    return siteState;
  }

  /**
   * This should be called by the SiteConnection whenever it detects
   * a connection close. This method will check to see if we have outstanding
   * work to do and open a new SiteConnection if so. The message will
   * usually be ignored if SiteCrawler initiated the close on purpose, due to
   * completion.
   *
   * A new connection is opened by actually sending a message back to
   * WorldCrawler, which will invoke such in a separate Thread.
   */
  public @Override void onClose() throws Exception {
    callOnClose.accept(this);
  }



  ///////////////////////
  // PAGE-LEVEL API'S: //
  ///////////////////////

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
    if (log.is(2) && onHead)
      debugger.headers(
        currentURI, statusCode, contentType,
        eTag, lastModified, closed, redirected,
        locationHeader
      );
    if (redirected && locationHeader!=null){
      tempLinks.add(locationHeader);
      return pageAccepted=false;
    }
    else
    if (contentType!=null && !contentType.startsWith("text"))
      return pageAccepted=false;
    else
      return pageAccepted=true;
  }

  /**
   * Should be called when the body of the page is delivered; can be called
   * incrementally as chunks arrive.
   */
  public @Override final void pageBody(URI currentURI, String s) throws Exception{
    if (!pageAccepted) return;
    pageSize+=s.length();
    if (log.is(2)) debugger.pageBody(s);
    pageParser.add(s);
  }

  /**
   * Should be called when the page is complete.
   */
  public @Override final void pageComplete(URI currentURI, boolean onHead) throws Exception{
    uriInFlight=null;

    // Move on from head:
    if (onHead && pageAccepted) {
      siteConnection.doGet(currentURI);
      return;
    }

    // Check count and print stats:
    siteState.addCount();
    if (log.is(1))
      debugger.pageComplete(
        currentURI, siteState.getCount(), pageSize, pageParser.getTitle()
      );

    // Reset page state:
    pageParser.reset();
    pageSize=0;

    // If not finished, crawl more:
    if (siteState.lessThanLimit()){
      addLinks(currentURI);
      URI nextURI=siteState.getNextForConnection();
      if (nextURI!=null) {
        read(nextURI);
        if (moreConnsAllowed && siteState.hasScheduled() && (moreConnsAllowed=siteState.moreConnsAllowed())){
          nextURI=siteState.getNextForConnection();
          if (nextURI==null)
            siteState.moreConnsFailed();// Very very rare
          else
            new SiteCrawler(log, connFactory, siteState, callOnClose).start(nextURI);
        }
        return; //RETURN
      }
    }

    // Well then close connection:
    if (!siteState.moreToCrawl()) debugger.siteComplete(siteState.getCount());
    closeConnection();
    callOnClose.accept(this);
  }


  final URI getReconnectURI() throws Exception {
    if (siteState.moreToCrawl())
      return uriInFlight != null
        ?uriInFlight
        :siteState.getNextForConnection();
    return null;
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////


  private void read(URI uri) throws Exception {
    uriInFlight=uri;
    pageAccepted=true;
    debugger.doHead(uri);
    siteConnection.doHead(uri);
  }


  private void addLinks(URI relativeTo) throws Exception {
    if (siteState.notEnoughURLsForLimit())
      siteState.addURIs(
        tempLinks.stream()
          .map(maybeStr -> {
            try {
              URI maybe=Link.getURI(relativeTo, maybeStr);

              // Only crawl it if it's the same host/scheme/port,
              // otherwise you need to go to a different channel.
              if (maybe!=null && !Link.sameSite(sitehost, sitescheme, siteport, maybe)){
                siteState.addElsewhereURI(maybe);
                maybe=null;
              }
              return Optional.ofNullable(maybe);
            } catch (Exception e) {
              log.date().add(e);
              return Optional.ofNullable((URI)null);
            }
          })
          .filter(maybe -> maybe.isPresent())
          .map(maybe -> maybe.get())
      );
    if (log.is(2))
      debugger.linkProcessing(
        siteState.getCount(),
        tempLinks.size(),
        siteState.getScheduledSize(),
        siteState.getElsewhereSize()
      );
    tempLinks.clear();
  }

  private void closeConnection() throws Exception {
    if (siteConnection!=null) {
      debugger.closing();
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
