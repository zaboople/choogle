package org.tmotte.choogle.clientnetty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
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


public final class SiteCrawler {
  private final int debugLevel=1;
  private final int limit;
  private final EventLoopGroup elGroup;

  private final Set<URI>
    siteURIs=new HashSet<>(),
    elsewhere=new HashSet<>();
  private final ArrayDeque<URI> scheduled=new ArrayDeque<>(128);
  private final Collection<String> tempLinks=new HashSet<>(128);
  private final AnchorReader anchorBP=new AnchorReader(tempLinks);

  private URI uri;
  private Channel channel;
  private int count=0;
  private int pageSize=0;


  public SiteCrawler(EventLoopGroup elGroup, URI uri, int limit){
    if (debug(1))
      System.out.println("SITE: "+uri);
    this.elGroup=elGroup;
    this.uri=uri;
    this.limit=limit;
  }
  public SiteCrawler(EventLoopGroup elGroup, String uri, int limit) throws Exception{
    this(elGroup, Link.getURI(uri), limit);
  }
  public SiteCrawler start() throws Exception {
    this.channel=SiteConnector.create(elGroup, myReceiver, uri).connect();
    read(uri);
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


  // INTERNAL FUNCTIONS: //

  private boolean debug(int level) {
    return level <= debugLevel;
  }

  private void read(URI uri) throws Exception {
    HttpRequest request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath()
    );
    request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
    request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
    channel.writeAndFlush(request);
  }
  private void addLinks() {
    if (debug(1))
      System.out.println("LINK COUNT: "+tempLinks.size());
    String host=uri.getHost();
    String scheme=uri.getScheme();
    int port=uri.getPort();
    if (scheduled.size()<limit)
      for (String maybeStr: tempLinks) {
        URI maybe=null;
        try {
          maybe=Link.getURI(uri, maybeStr);
        } catch (Exception e) {
          e.printStackTrace();//FIXME add to list
          continue;
        }
        if (maybe==null)
          continue;

        // Only crawl it if it's the same host/scheme/port,
        // otherwise you need to go to a different channel.
        if (maybe.getHost().equals(host) &&
            maybe.getScheme().equals(scheme) &&
            maybe.getPort()==port){
          if (!siteURIs.contains(maybe)){
            scheduled.add(maybe);
            siteURIs.add(maybe);
          }
        }
        else elsewhere.add(maybe);
      }
    tempLinks.clear();
    if (debug(1))
      System.out.append("SCHEDULED: "+scheduled.size()+" ELSEWHERE "+elsewhere.size());
  }

  // INTERNAL RECEIVER OF DATA:

  private Chreceiver myReceiver = new Chreceiver() {
    @Override public void start(HttpResponse resp){
      if (debug(1)) System.out.append("\nSTARTING: ").append(uri.toString()).append(" ");
      anchorBP.reset();
      int statusCode=resp.getStatus().code();
      if (debug(1)) System.out.print(statusCode);
      if (statusCode==HttpResponseStatus.FOUND.code() ||
          statusCode==HttpResponseStatus.MOVED_PERMANENTLY.code()) {
        HttpHeaders headers = resp.headers();
        String location=headers.get(HttpHeaders.Names.LOCATION);
        if (location!=null) {
          tempLinks.add(location);
          if (debug(1)) System.out.append(" ").append(location);
        }
      }
      else
        count++;
      if (debug(1)) System.out.println();
    }
    @Override public void body(HttpContent body){
      String s=body.content().toString(CharsetUtil.UTF_8);
      pageSize+=s.length();
      if (debug(1))
        if (debug(2)) {
          System.out.print("...");
          if (s.length()<20)
            System.out.print(s);
          else
            System.out.print(s.substring(s.length()-20));
        }
        else
          System.out.print(".");

      if (true || scheduled.size()<limit)
        anchorBP.add(s);
    }
    @Override public void complete(){
      if (debug(1))
        System.out.append("\nCOMPLETE ")
          .append(String.valueOf(count)).append(" ")
          .append(String.valueOf(pageSize / 1024)).append("K")
          .append(" ").append(uri.toString()).append("\n")
          ;
      if (count<limit){
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
      channel.close();
    }
  };

}
