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
 * For crawling a single web site. There are three key abstract methods:
 *  doHead(uri)
 *  doGet(uri)
 *  close().
 *
 * Your implementation's doHead/doGet(uri) should call the pageStart/pageBody/pageComplete()
 * methods as data arrives; it should implement close() for synchronizing completion of the work.
 *
 * Note that this class is not threadsafe and is really intended for use with a non-blocking IO library.
 *
 * Testing note: Apache.org is a great place to test connection close, as they tend to limit
 * connection lifetime.
 */
public abstract class SiteCrawler {

  // NON-CHANGING STATE:
  private final int debugLevel;
  private final long limit;
  private final boolean cacheResults;
  private final AnchorReader pageParser;

  // SITE STATE:
  private String sitehost;
  private String sitescheme;
  private int siteport;

  // RAPIDLY CHANGING STATE:
  private final ArrayDeque<URI> scheduled=new ArrayDeque<>(128);
  private final Set<String> scheduledSet=new HashSet<>();
  private final Set<URI>    elsewhere=new HashSet<>();
  private final Collection<String> tempLinks=new HashSet<>(128);
  private URI uriInFlight;
  private int count=0;
  private int pageSize=0;
  private boolean lastWasRedirect=false;
  private boolean accepted=true;
  private Consumer<SiteCrawler> callOnComplete;

  public SiteCrawler(Consumer<SiteCrawler> callOnComplete, long limit, int debugLevel, boolean cacheResults){
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
    scheduledSet.add(initialURI.getRawPath());
    read(initialURI);
    return this;
  }

  /**
   * Should perform a HEAD request against the URI and call
   * pageStart() & pageComplete() accordingly. There is no need
   * to call pageBody().
   */
  protected abstract void doHead(URI uri) throws Exception;

  /**
   * Should perform a GET request against the URI and call
   * pageStart(), pageBody() & pageComplete() accordingly.
   */
  protected abstract void doGet(URI uri) throws Exception;

  /**
   * This should be called whenever the connection is closed. It will
   * check to see if we have outstanding work and force a reconnect if so.
   * Otherwise it will send a message back to the callOnComplete object
   * (refer to our constructor) saying we are done.
   *
   * (callOnComplete is really just WorldCrawler)
   */
  protected void onClose() throws Exception {
    if (!reconnectIfUnfinished())
      callOnComplete.accept(this);
  }



  ///////////////////////
  // PAGE-LEVEL API'S: //
  ///////////////////////

  private void read(URI uri) throws Exception {
    uriInFlight=uri;
    accepted=true;
    debugDoHead(uri);
    doHead(uri);
  }


  /** Call to the next scheduled URL for the current site. */
  private URI getNext() {
    return scheduled.size() > 0
      ?scheduled.removeFirst()
      :null;
  }

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
    lastWasRedirect=redirected && !lastWasRedirect;
    if (debug(2))
      debugHeaders(
        currentURI, statusCode, contentType,
        eTag, lastModified,
        closed, redirected,
        locationHeader
      );
    if (redirected && locationHeader!=null){
      tempLinks.add(locationHeader);
      accepted=false;
    }
    else
    if (contentType != null && !contentType.startsWith("text"))
      accepted=false;
    else
      accepted=true;
  }

  /**
   * Should be called when the body of the page is delivered; can be called
   * incrementally as chunks arrive.
   */
  public final void pageBody(URI currentURI, String s) throws Exception{
    if (!accepted) return;
    pageSize+=s.length();
    if (debug(2)) debugPageBody(s);
    pageParser.add(s);
  }

  /**
   * Should be called when the page is complete.
   * @return True if we have more work to do and want to keep our connection alive.
   */
  public final boolean pageComplete(URI currentURI, boolean onHead) throws Exception{
    uriInFlight=null;
    if (onHead && accepted) {
      doGet(currentURI);
      return true;
    }

    //FIXME when oracle.com redirects us twice, we record count of 2
    //and we assume there's no point in trying anymore. But we can fix
    //this and keep going because read() is our friend. Yeah and we're
    //on a separate thread BTW.
    count++;
    if (debug(1)) debugPageComplete(currentURI);
    if (count<limit || limit == -1){
      pageSize=0;
      addLinks(currentURI);
      URI nextURI = getNext();
      if (nextURI!=null)
        try {
          resetPageParser();
          read(nextURI);
          return true; //RETURN
        } catch (Exception e) {
          e.printStackTrace();
        }
    }
    resetPageParser();
    if (debug(1)) debugSiteComplete();
    return false;
  }

  ///////////
  // ETC.: //
  ///////////

  protected final boolean debug(int level) {
    return level <= debugLevel;
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private void addLinks(URI relativeTo) {
    if (scheduled.size()<limit || limit == -1)
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

    if (debug(2)) debugLinkProcessing();
  }

  private void resetPageParser() {
    tempLinks.clear();
    pageParser.reset();
  }


  private final boolean reconnectIfUnfinished() throws Exception {
    if (count==1 && lastWasRedirect && scheduled.size()==0 && elsewhere.size()>=1){
      // Redirect:
      start(elsewhere.iterator().next());
      return true;
    }
    else
    if ((count<limit || limit==-1) && scheduled.size() > 0){
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


  ///////////////////
  //               //
  //     DEBUG:    // FIXME move to another class, or simplify this is exhausting
  //               //
  ///////////////////

  private void debugDoHead(URI uri) {
    if (debug(2))
      System.out
        .append("\nSTARTING: ")
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
      .append(String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS ", new java.util.Date())) //FIXME make a datemaker thing god that is gross looking ew
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
}
