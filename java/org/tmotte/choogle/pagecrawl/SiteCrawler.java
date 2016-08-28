package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import org.tmotte.choogle.pagecrawl.AnchorReader;
import org.tmotte.choogle.pagecrawl.Link;

/**
 * For crawling a single web site. There are two key abstract methods: read(uri) and finish().
 * A complete crawler should implement read(uri) and call the pageStart/pageBody/pageComplete()
 * methods as data arrives; it should implement finish() for synchronizing completion of the work.
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
  protected final ArrayDeque<URI> scheduled=new ArrayDeque<>(128);
  private final Set<String> alreadyCrawled=new HashSet<>();
  private final Set<URI>    elsewhere=new HashSet<>();
  private final Collection<String> tempLinks=new HashSet<>(128);
  private int count=0;
  private int pageSize=0;


  public SiteCrawler(
      long limit, int debugLevel, boolean cacheResults
    ){
    this.debugLevel=debugLevel;
    this.pageParser=new AnchorReader(
      tempLinks,
      debug(3)
        ?x -> System.out.print(x)
        :null
    );
    this.limit=limit;
    this.cacheResults=cacheResults;
  }

  ///////////////////////
  // SITE-LEVEL API's: //
  ///////////////////////

  public SiteCrawler start(String initialURI) throws Exception {
    return start(getURI(initialURI));
  }

  /**
   * Starts the crawling process. Calls read(uri).
   */
  public SiteCrawler start(URI initialURI) throws Exception {
    if (debug(1))
      System.out.println("SITE: "+initialURI);
    this.sitehost=initialURI.getHost();
    this.sitescheme=initialURI.getScheme();
    this.siteport=initialURI.getPort();
    read(initialURI);
    return this;
  }

  /**
   * Called after start() to synchronize to completion.
   */
  public abstract void finish() throws Exception;

  public URI wasSiteRedirect() {
    return count==0 && scheduled.size()==0 && elsewhere.size()>=1
      ?elsewhere.iterator().next()
      :null;
  }
  public boolean reconnectIfUnfinished() throws Exception {
    if ((count<limit || limit==-1) && scheduled.size() > 0){
      start(scheduled.removeFirst());
      return true;
    }
    return false;
  }

  ///////////////////////
  // PAGE-LEVEL API'S: //
  ///////////////////////

  /**
   * Needs to be implemented to both read the URI and call pageStart/pageBody/pageComplete()
   * as data comes in.
   */
  protected abstract void read(URI uri) throws Exception;

  /** Call to the next scheduled URL for the current site. */
  protected URI getNext() {
    if (scheduled.size()==0)
      return null;
    return scheduled.removeFirst();
  }

  /**
   * Should be called when headers from the server arrive.
   */
  public void pageStart(
      URI currentURI,
      int statusCode,
      String eTag,
      String lastModified,
      boolean closed,
      boolean redirected,
      String locationHeader
    )throws Exception{
    if (debug(2)) {
      System.out.append("  ").append(sitehost)
        .append(" RESPONSE")
        .append(" STATUS: ")
        .append(String.valueOf(statusCode));
      if (eTag!=null)
        System.out.append(" ETAG: ").append(eTag);
      if (lastModified !=null)
        System.out.append(" LAST MODIFIED: ").append(lastModified);
      if (closed)
        System.out.append(" CLOSED ");
      if (redirected)
        System.out.append(" REDIRECT: ").append(locationHeader);
    }
    if (redirected && locationHeader!=null)
      tempLinks.add(locationHeader);
    else
      count++;
    if (debug(2)) System.out.print("\n  ");
  }

  /**
   * Should be called when the body of the page is delivered; can be called
   * incrementally as chunks arrive.
   */
  public void pageBody(URI currentURI, String s) throws Exception{
    pageSize+=s.length();
    if (debug(2)) {
      if (debug(4)) {
        System.out.print("\n>>>");
        System.out.print(s);
        System.out.print("<<<\n");
      }
      else
        System.out.print(".");
    }
    pageParser.add(s);
  }

  /**
   * Should be called when the page is complete.
   * @return If we want more pages, true.
   */
  public boolean pageComplete(URI currentURI) throws Exception{
    if (debug(1))
      System.out
        .append("\n  ")
        .append(sitehost).append(" COMPLETED")
        .append(" SIZE: ").append(String.valueOf(pageSize / 1024)).append("K")
        .append(" TITLE: ").append(pageParser.getTitle())
        .append("\n")
        ;
    if (count<limit || limit == -1){
      pageSize=0;
      addLinks(currentURI);
      if (scheduled.size()>0)
        try {
          resetPageParser();
          return true; //RETURN
        } catch (Exception e) {
          e.printStackTrace();
        }
    }
    if (debug(2))
      System.out
        .append(sitehost)
        .append("  ALL LINKS READ, CLOSING\n");
    resetPageParser();
    return false;
  }

  ///////////
  // ETC.: //
  ///////////

  protected boolean debug(int level) {
    return level <= debugLevel;
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private void addLinks(URI relativeTo) {

    // 1. If we found some links, add them to our schedule
    //    of what to crawl:
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
          if (!alreadyCrawled.contains(maybe.getRawPath()))
            scheduled.add(maybe);
        }
        else elsewhere.add(maybe);
      }

    // 2. Print some helpful debugging:
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

  private void resetPageParser() {
    tempLinks.clear();
    pageParser.reset();
  }

  private static URI getURI(String uri) throws Exception {
    URI realURI=Link.getURI(uri);
    if (realURI==null)
      throw new RuntimeException("Could not interpret URI: "+uri);
    return realURI;
  }


}
