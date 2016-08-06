package org.tmotte.choogle.clientnetty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import org.tmotte.choogle.chug.AnchorReader;
import org.tmotte.choogle.chug.Link;

/**
 * Crawls a single web site. Also makes a list of external links but doesn't do anything with them;
 * in fact it <b>can't</b> even if it wanted to, because SiteCrawler is tied to a single I/O Channel,
 * which means a specific domain/port/protocol. Most importantly, If SiteCrawler immediately gets a
 * redirect because, say, http://foo.com always redirects to https://www.foo.com, a new SiteCrawler
 * must be created. WorldCrawler handles this responsibility and uses SiteCrawler.wasSiteRedirect()
 * to find out about it.
 */
public final class SiteCrawler {

  // NON-CHANGING STATE:
  private final int debugLevel;
  private final long limit;
  private final boolean cacheResults;
  private final EventLoopGroup elGroup;

  // CHANGE-ONCE STATE:
  private Channel channel;
  private String sitehost;
  private String sitescheme;
  private int siteport;

  // INTERNALLY CHANGING STATE:
  private final Set<String> alreadyCrawled=new HashSet<>();
  private final Set<URI>    elsewhere=new HashSet<>();
  private final ArrayDeque<URI> scheduled=new ArrayDeque<>(128);
  private final Collection<String> tempLinks=new HashSet<>(128);
  private final AnchorReader pageParser;

  // RAPIDLY CHANGING STATE:
  private URI currentURI;
  private int count=0;
  private int pageSize=0;
  private boolean earlyClose=false;


  public SiteCrawler(
      EventLoopGroup elGroup, URI uri, long limit, int debugLevel, boolean cacheResults
    ){
    this.debugLevel=debugLevel;
    if (debug(1))
      System.out.println("SITE: "+uri);
    this.pageParser=new AnchorReader(
      tempLinks,
      debug(3)
        ?x -> System.out.print(x)
        :null
    );
    this.elGroup=elGroup;
    this.currentURI=uri;
    this.limit=limit;
    this.cacheResults=cacheResults;
  }
  public SiteCrawler(
      EventLoopGroup elGroup, String uri, long limit, int debugLevel, boolean cacheResults
    ) throws Exception{
    this(elGroup, getURI(uri), limit, debugLevel, cacheResults);
  }

  private static URI getURI(String uri) throws Exception {
    URI realURI=Link.getURI(uri);
    if (realURI==null)
      throw new RuntimeException("Could not interpret URI: "+uri);
    return realURI;
  }


  public SiteCrawler start() throws Exception {
    this.sitehost=currentURI.getHost();
    this.sitescheme=currentURI.getScheme();
    this.siteport=currentURI.getPort();

    this.channel=SiteConnector.create(elGroup, myReceiver, currentURI).connect();
    read(currentURI);
    return this;
  }
  public ChannelFuture finish() throws Exception {
    return channel==null ?null :channel.closeFuture();
  }
  public URI wasSiteRedirect() {
    return count==0 && scheduled.size()==0 && elsewhere.size()>=1
      ?elsewhere.iterator().next()
      :null;
  }
  public boolean reconnectIfUnfinished() throws Exception {
    if ((earlyClose || count<limit || limit==-1) && scheduled.size() > 0){
      currentURI=scheduled.removeFirst();
      start();
      return true;
    }
    return false;
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private boolean debug(int level) {
    return level <= debugLevel;
  }

  private void read(URI uri) throws Exception {
    if (!channel.isOpen() || !channel.isActive()) {
      scheduled.add(uri);
      earlyClose=true;
      return;
    }
    currentURI=uri;
    earlyClose=false;
    String rawPath=uri.getRawPath();
    if (debug(2)) System.out.append("\nSTARTING: ").append(rawPath);
    HttpRequest request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1, HttpMethod.GET, rawPath
    );
    request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
    request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
    channel.writeAndFlush(request);
  }
  private void addLinks() {
    if (scheduled.size()<limit || limit == -1)
      for (String maybeStr: tempLinks) {
        URI maybe=null;
        try {
          maybe=Link.getURI(currentURI, maybeStr);
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
    if (debug(2))
      System.out
        .append("LINK COUNT: "+tempLinks.size())
        .append(" SCHEDULED: ").append(String.valueOf(scheduled.size()))
        .append(" ELSEWHERE: ").append(String.valueOf(elsewhere.size()))
        .append("\n");
    tempLinks.clear();
  }

  ////////////////////////////////
  // INTERNAL RECEIVER OF DATA: //
  ////////////////////////////////

  private Chreceiver myReceiver = new Chreceiver() {
    @Override public void start(HttpResponse resp){
      if (cacheResults)
        alreadyCrawled.add(currentURI.getRawPath());
      pageParser.reset();
      int statusCode=resp.getStatus().code();
      HttpHeaders headers = resp.headers();
      String connectionStatus=headers.get(HttpHeaders.Names.CONNECTION);
      String eTag=headers.get(HttpHeaders.Names.ETAG);
      if ("CLOSE".equals(connectionStatus) || "close".equals(connectionStatus))
        earlyClose=true;
      if (debug(2))
        System.out.append("\nRESPONDED: ")
        .append(currentURI.toString())
        .append(" STATUS: ")
        .append(String.valueOf(statusCode))
        .append(" ETAG: ")
        .append(eTag)
        .append(" ")
        .append(connectionStatus);
      if (statusCode==HttpResponseStatus.FOUND.code() ||
          statusCode==HttpResponseStatus.MOVED_PERMANENTLY.code()) {
        String location=headers.get(HttpHeaders.Names.LOCATION);
        if (location!=null) {
          tempLinks.add(location);
          if (debug(2)) System.out.append("\nREDIRECT: ").append(location);
        }
      }
      else
        count++;
      if (debug(2)) System.out.println();
    }
    @Override public void body(HttpContent body){
      String s=body.content().toString(CharsetUtil.UTF_8);
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
    @Override public void complete(){
      if (debug(1))
        System.out
          .append("\nCOMPLETED: ").append(currentURI.toString())
          .append(" COUNT: ").append(String.valueOf(count))
          .append(" SIZE: ").append(String.valueOf(pageSize / 1024)).append("K")
          .append(" TITLE: ").append(pageParser.getTitle())
          .append("\n")
          ;
      if (count<limit || limit == -1){
        pageSize=0;
        addLinks();
        if (scheduled.size()>0)
          try {
            read(scheduled.removeFirst());
            return; //RETURN
          } catch (Exception e) {
            e.printStackTrace();
          }
      }
      if (debug(2)) System.out.println("ALL LINKS READ, CLOSING");
      channel.close();
    }
  };

}
