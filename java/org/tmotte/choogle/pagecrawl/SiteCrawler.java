package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import org.tmotte.choogle.pagecrawl.AnchorReader;
import org.tmotte.choogle.pagecrawl.Link;


/**
 * //FIXME move debugging prints outside class
 * //FIXME get better timestamping
 * //FIXME test infinite redirect
 * //FIXME test early connection close
 *
 *
 * Note that this class is not threadsafe and is really intended for use with a non-blocking IO library.
 *
 * Testing note: Apache.org is a great place to test connection close, as they tend to limit
 * connection lifetime.
 */
public class SiteCrawler {

  // NON-CHANGING STATE:
  private final SiteConnectionFactory connFactory;
  private final Consumer<SiteCrawler> callOnComplete;
  private final long limit;
  private final int debugLevel;
  private final boolean cacheResults;
  private final AnchorReader pageParser;

  // SITE STATE:
  private String sitehost;
  private String sitescheme;
  private int siteport;
  private SiteConnection siteConnection;

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
      Consumer<SiteCrawler> callOnComplete,
      long limit,
      int debugLevel,
      boolean cacheResults
    ){
    this.connFactory=connFactory;
    this.debugLevel=debugLevel;
    this.pageParser=new AnchorReader(
      tempLinks,
      debug(3)
        ?x -> System.out.print(x)
        :null
    );
    this.limit=limit;
    this.cacheResults=cacheResults;
    this.callOnComplete=callOnComplete;
  }

  ///////////////////////
  // SITE-LEVEL API's: //
  ///////////////////////

  /** Just prints the host that we're crawling */
  public String toString() {
    return sitehost;
  }

  public final SiteCrawler start(String initialURI) throws Exception {
    return start(getURI(initialURI));
  }

  /**
   * Starts the crawling process. Calls read(uri).
   */
  public final SiteCrawler start(URI initialURI) throws Exception {
    this.sitehost=initialURI.getHost();
    this.sitescheme=initialURI.getScheme();
    this.siteport=initialURI.getPort();
    this.siteConnection=connFactory.get(this, initialURI);
    scheduledSet.add(initialURI.getRawPath());
    read(initialURI);
    return this;
  }


  /**
   * This should be called whenever the connection is closed. It will
   * check to see if we have outstanding work on that connection
   * and force a reconnect if so. The message will be ignored if
   * SiteCrawler is the one who initiated it.
   *
   * Otherwise it will send a message back to the callOnComplete object
   * (refer to our constructor) saying we are done.
   *
   * (callOnComplete is really just WorldCrawler)
   */
  public void onClose(SiteConnection sc) throws Exception {
    if (sc==siteConnection && !reconnectIfUnfinished())
      callOnComplete.accept(this);
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
    if (debug(2))
      debugHeaders(
        currentURI, statusCode, contentType,
        eTag, lastModified,
        closed, redirected,
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
    if (debug(2)) debugPageBody(s);
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
    if (debug(1)) debugPageComplete(currentURI);

    // Reset page state:
    pageParser.reset();
    pageSize=0;

    // If not finished, crawl more or connect to redirect:
    if (unfinished()){
      addLinks(currentURI);
      URI nextURI = getNextForConnection();
      if (nextURI!=null) {
        read(nextURI);
        return; //RETURN
      }
      if (followSiteRedirect())
        return;
    }

    // Well then close connection:
    if (debug(1)) debugSiteComplete();
    closeConnection();
    callOnComplete.accept(this);
  }


  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private final boolean debug(int level) {
    return level <= debugLevel;
  }

  private void read(URI uri) throws Exception {
    uriInFlight=uri;
    pageAccepted=true;
    debugDoHead(uri);
    siteConnection.doHead(uri);
  }

  /** Call to the next scheduled URL for the current site. */
  private URI getNextForConnection() {
    return scheduled.size() > 0
      ?scheduled.removeFirst()
      :null;
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
    tempLinks.clear();
    if (debug(2)) debugLinkProcessing();
  }

  private boolean unfinished() {
    return limit == -1 || count < limit;
  }

  private final boolean reconnectIfUnfinished() throws Exception {
    if (followSiteRedirect())
      return true;
    else
    if (unfinished() && scheduled.size() > 0){
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

  private final boolean followSiteRedirect() throws Exception {
    if (count==0 && lastWasSiteRedirect && scheduled.size()==0 && elsewhere.size()>=1) {
      if (siteRedirectCount > 5){
        System.out.append(sitehost).append(" TOO MANY REDIRECTS");
        return false;
      }
      closeConnection();
      URI newURI=elsewhere.iterator().next();
      elsewhere.clear();
      if (debug(1))
        System.out.append(sitehost)
          .append(" REDIRECTING SITE TO ")
          .append(newURI.toString())
          .append("\n");
      start(newURI);
      return true;
    }
    return false;
  }

  private void closeConnection() throws Exception {
    if (siteConnection!=null) {
      if (debug(2))
        System.out.append(sitehost).append(" CLOSING\n");
      SiteConnection temp=siteConnection;
      siteConnection=null;
      temp.close();
    }
  }


  ///////////////////
  //               //
  //     DEBUG:    // FIXME move to another class, or simplify this is exhausting
  //               //
  ///////////////////

  private void debugDoHead(URI uri) {
    if (debug(2))
      System.out
        .append("\nHEAD: ")
        .append(uri.toString())
        .append("\n");
  }
  private void debugHeaders(
      URI currentURI,
      int statusCode,
      String contentType,
      String eTag,
      String lastModified,
      boolean closed,
      boolean redirected,
      String locationHeader
    ){
    System.out.append("  ").append(sitehost)
      .append(" RESPONSE")
      .append(" STATUS: ")
      .append(String.valueOf(statusCode))
      .append(" CONTENT TYPE: ")
      .append(contentType);
    if (eTag!=null)
      System.out.append(" ETAG: ").append(eTag);
    if (lastModified !=null)
      System.out.append(" LAST MODIFIED: ").append(lastModified);
    if (closed)
      System.out.append("\n  ")
        .append(sitehost).append(" CLOSED");
    if (redirected)
      System.out
        .append("\n  ").append(sitehost)
        .append(" REDIRECT: ").append(locationHeader);
    System.out.print("\n  ");
  }

  private void debugPageBody(String s) {
    if (debug(4))
      System.out.append("\n>>>").append(s).append("<<<\n");
    else
      System.out.append(".");
  }

  private void debugPageComplete(URI currentURI) {
    if (debug(2)) System.out.append("\n  ");
    System.out
      .append(
        String.format(
          "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS ",//FIXME make a datemaker thing god that is gross looking ew
          new java.util.Date()
        )
      )
      .append(sitehost).append(" COMPLETE #").append(String.valueOf(count))
      .append(" SIZE: ").append(String.valueOf(pageSize / 1024)).append("K")
      .append(" URI: ").append(currentURI.toString())
      .append(" TITLE: ").append(pageParser.getTitle())
      .append("\n")
      ;
  }

  private void debugLinkProcessing() {
    if (debug(2))
      System.out
        .append("  ").append(sitehost)
        .append(" RESPONSE COUNT: ").append(String.valueOf(count));
    if (debug(3))
      System.out
        .append(" LINK COUNT: "+tempLinks.size())
        .append(" SCHEDULED: ").append(String.valueOf(scheduled.size()))
        .append(" ELSEWHERE: ").append(String.valueOf(elsewhere.size()));
    if (debug(2))
      System.out.append("\n");
  }

  private void debugSiteComplete() {
    System.out
      .append(sitehost)
      .append("  ALL LINKS READ, CLOSING, COUNT: ")
      .append(String.valueOf(count))
      .append("\n");
  }



  private static URI getURI(String uri) throws Exception {
    URI realURI=Link.getURI(uri);
    if (realURI==null)
      throw new RuntimeException("Could not interpret URI: "+uri);
    return realURI;
  }


  private static void flushln(String s) {
    System.out.println(Thread.currentThread()+s);
    System.out.flush();
  }

}
