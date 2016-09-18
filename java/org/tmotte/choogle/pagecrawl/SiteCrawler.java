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
 * Crawls a single web site, following redirects as necessary.
 *
 * Note that this class is not threadsafe and is really intended for use with a non-blocking IO library.
 *
 * Testing note: Apache.org is a great place to test connection close, as they tend to limit
 * connection lifetime.
 * FIXME test infinite redirect
 * FIXME test early connection close
 */
public class SiteCrawler {

  // NON-CHANGING STATE:
  private final SiteConnectionFactory connFactory;
  private final Consumer<SiteCrawler> callOnClose;
  private final long limit;
  private final boolean cacheResults;
  private final AnchorReader pageParser;
  private final Outlog log;
  private final SiteCrawlerDebug debugger=new SiteCrawlerDebug();

  // SITE STATE:
  private String sitehost;
  private String sitescheme;
  private int siteport;
  private SiteConnection siteConnection;
  private String key;
  private int index=0;

  // RAPIDLY CHANGING STATE:
  private final ArrayDeque<URI> scheduled=new ArrayDeque<>(128);
  private final Set<String> scheduledSet=new HashSet<>();
  private final Set<URI>    elsewhere=new HashSet<>();
  private final Collection<String> tempLinks=new HashSet<>(128);
  private int count=0;
  private int siteRedirectCount=0;
  private URI uriInFlight;
  private int pageSize=0;
  private boolean lastWasSiteRedirect=false;
  private boolean pageAccepted=true;

  public SiteCrawler(
      SiteConnectionFactory connFactory,
      Consumer<SiteCrawler> callOnClose,
      long limit,
      Outlog log,
      boolean cacheResults
    ){
    this.connFactory=connFactory;
    this.log=log;
    this.pageParser=new AnchorReader(
      tempLinks,
      log.is(4)
        ?x -> log.add(x)
        :null
    );
    this.limit=limit;
    this.cacheResults=cacheResults;
    this.callOnClose=callOnClose;
    debugger.log=log;
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
    this.sitehost=initialURI.getHost();
    this.sitescheme=initialURI.getScheme();
    this.siteport=initialURI.getPort();
    this.siteConnection=connFactory.get(this, initialURI);
    this.index++;
    this.key=sitehost+":"+siteport+"-"+index;
    debugger.sitename=this.key;
    scheduledSet.add(initialURI.getRawPath());
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
    return key;
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
  public void onClose(SiteConnection sc) throws Exception {
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
  public final void pageStart(
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
    lastWasSiteRedirect=count==0 && redirected;
    if (lastWasSiteRedirect) siteRedirectCount++;
    if (log.is(2) && onHead)
      debugger.headers(
        currentURI, statusCode, contentType,
        eTag, lastModified, closed, redirected,
        locationHeader
      );
    if (redirected && locationHeader!=null){
      tempLinks.add(locationHeader);
      pageAccepted=false;
    }
    else
    if (contentType != null && !contentType.startsWith("text"))
      pageAccepted=false;
    else
      pageAccepted=true; //FIXME why not return a value to say it is, we documented that
  }

  /**
   * Should be called when the body of the page is delivered; can be called
   * incrementally as chunks arrive.
   */
  public final void pageBody(URI currentURI, String s) throws Exception{
    if (!pageAccepted) return;
    pageSize+=s.length();
    if (log.is(2)) debugger.pageBody(s);
    pageParser.add(s);
  }

  /**
   * Should be called when the page is complete.
   * @return True if we have more work to do and want to keep our connection alive.
   */
  public final void pageComplete(URI currentURI, boolean onHead) throws Exception{
    uriInFlight=null;

    // Move on from head:
    if (onHead && pageAccepted) {
      siteConnection.doGet(currentURI);
      return;
    }

    // Check count and print stats:
    if (!lastWasSiteRedirect)
      count++;
    if (log.is(1))
      debugger.pageComplete(
        currentURI, count, pageSize, pageParser.getTitle()
      );

    // Reset page state:
    pageParser.reset();
    pageSize=0;

    // If not finished, crawl more:
    if (lessThanLimit()){
      addLinks(currentURI);
      URI nextURI = getNextForConnection();
      if (nextURI!=null) {
        read(nextURI);
        return; //RETURN
      }
    }

    // Well then close connection:
    if (!needsReconnect() && log.is(1)) debugger.siteComplete(count);
    closeConnection();
    callOnClose.accept(this);
  }

  /**
   * Called by WorldCrawler in a separate thread after we tell it
   * we've closed the connection
   */
  final boolean needsReconnect() {
    return moreToCrawl() || redirectWaiting();
  }

  final boolean reconnectIfUnfinished() throws Exception {
    if (redirectWaiting()) {
      URI newURI=elsewhere.iterator().next();
      elsewhere.clear();
      if (log.is(1)) debugger.redirecting(newURI);
      start(newURI);
      return true;
    }
    else
    if (moreToCrawl()){
      // Connection dropped:
      if (uriInFlight != null)
        start(uriInFlight);
      else
        start(scheduled.removeFirst());
      return true;
    }
    else
      return false;
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


  private void addLinks(URI relativeTo) {
    if (count + scheduled.size()<limit || limit == -1)
      for (String maybeStr: tempLinks) {
        URI maybe=null;
        try {
          maybe=Link.getURI(relativeTo, maybeStr);
        } catch (Exception e) {
          e.printStackTrace();//FIXME add to list
          continue;
        }
        if (maybe==null)
          continue;

        // Only crawl it if it's the same host/scheme/port,
        // otherwise you need to go to a different channel.
        if (maybe.getHost().equals(sitehost) &&
            maybe.getScheme().equals(sitescheme) &&
            maybe.getPort()==siteport){
          String raw = maybe.getRawPath();
          if (!scheduledSet.contains(raw)) {
            scheduled.add(maybe);
            if (cacheResults)
              scheduledSet.add(raw);
          }
        }
        else elsewhere.add(maybe);
      }
    if (log.is(2))
      debugger.linkProcessing(count, tempLinks.size(), scheduled.size(), elsewhere.size());
    tempLinks.clear();
  }

  /** Call to the next scheduled URL for the current site. */
  private URI getNextForConnection() {
    return scheduled.size() > 0
      ?scheduled.removeFirst()
      :null;
  }

  private boolean moreToCrawl() {
    boolean hasMore=lessThanLimit() && scheduled.size()>0;
    return hasMore;
  }
  private boolean redirectWaiting() {
    boolean maybe=count==0 && lastWasSiteRedirect && scheduled.size()==0 && elsewhere.size()>=1;
    if (maybe && siteRedirectCount > 5){
      if (log.is(1)) log.add(sitehost).add(" TOO MANY REDIRECTS");
      maybe=false;
    }
    return maybe;
  }
  private boolean lessThanLimit() {
    return limit == -1 || count < limit;
  }


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
