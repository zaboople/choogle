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
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import org.tmotte.choogle.pagecrawl.AnchorReader;
import org.tmotte.choogle.pagecrawl.Link;
import org.tmotte.choogle.pagecrawl.SiteCrawler;

/**
 * Crawls a single web site. Also makes a list of external links but doesn't do anything with them;
 * in fact it <b>can't</b> even if it wanted to, because SiteCrawler is tied to a single I/O Channel,
 * which means a specific domain/port/protocol. Most importantly, If SiteCrawler immediately gets a
 * redirect because, say, http://foo.com always redirects to https://www.foo.com, a new SiteCrawler
 * must be created. WorldCrawler handles this responsibility and uses SiteCrawler.wasSiteRedirect()
 * to find out about it.
 */
public final class NettySiteCrawler extends SiteCrawler {

  private final EventLoopGroup elGroup;
  private Channel channel;
  private URI currentURI;

  public NettySiteCrawler(
      EventLoopGroup elGroup, long limit, int debugLevel, boolean cacheResults
    ) throws Exception{
    super(limit, debugLevel, cacheResults);
    this.elGroup=elGroup;
  }

  public @Override void finish() throws Exception {
    if (channel!=null) channel.closeFuture().sync();
    channel = null;
  }

  protected @Override void read(URI uri) throws Exception {
    if (channel==null)
      channel=SiteConnector.connect(elGroup, myReceiver, uri);
    else
    if (!channel.isOpen() || !channel.isActive()) {
      channel = null;
      read(uri);
      return;
    }
    currentURI=uri;
    String rawPath=uri.getRawPath();
    if (debug(2)) System.out.append("\nSTARTING: ").append(rawPath);
    HttpRequest request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1, HttpMethod.GET, rawPath
    );
    request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
    request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    //request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
    request.headers().set("Accept-Encoding", "gzip, deflate");
    channel.writeAndFlush(request);
  }

  ////////////////////////////////
  // INTERNAL RECEIVER OF DATA: //
  ////////////////////////////////

  private Chreceiver myReceiver = new Chreceiver() {
    @Override public void start(HttpResponse resp)throws Exception {
      HttpHeaders headers = resp.headers();
      int statusCode=resp.getStatus().code();
      boolean redirected =
        statusCode==HttpResponseStatus.FOUND.code() ||
        statusCode==HttpResponseStatus.MOVED_PERMANENTLY.code();
      String connectionStatus=headers.get(HttpHeaders.Names.CONNECTION);
      String eTag=headers.get(HttpHeaders.Names.ETAG);
      String location=headers.get(HttpHeaders.Names.LOCATION);
      boolean closed =
        "CLOSE".equals(connectionStatus) || "close".equals(connectionStatus);
      if (closed && debug(1))
        System.out.append(currentURI.toString()).append(" WANTS TO CLOSE CONNECTION\n");
      pageStart(currentURI, statusCode, eTag, redirected, location);
    }
    @Override public void body(HttpContent body) throws Exception {
      pageBody(currentURI, body.content().toString(CharsetUtil.UTF_8));
    }
    @Override public void complete() throws Exception {
      URI temp = currentURI;
      currentURI = null;
      URI next = pageComplete(temp);
      if (next != null)
        read(next);
      else
        channel.close();
    }
  };

}
