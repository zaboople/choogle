package org.tmotte.choogle.pagecrawlnetty;
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
import org.tmotte.choogle.pagecrawl.SiteCrawler;
import org.tmotte.common.nettyclient.MySiteConnector;
import org.tmotte.common.nettyclient.MyResponseReceiver;

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
      channel=MySiteConnector.connect(elGroup, myReceiver, uri);
    else
    if (!channel.isOpen() || !channel.isActive()) {
      channel = null;
      read(uri);
      return;
    }
    currentURI=uri;
    String rawPath=uri.getRawPath();
    if (debug(2))
      System.out
        .append("\nSTARTING: ")
        .append(uri.toString())
        .append("\n");
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

  private MyResponseReceiver myReceiver = new MyResponseReceiver() {
    @Override public void start(HttpResponse resp)throws Exception {
      HttpHeaders headers = resp.headers();
      int statusCode=resp.getStatus().code();
      boolean redirected =
        statusCode==HttpResponseStatus.FOUND.code() ||
        statusCode==HttpResponseStatus.MOVED_PERMANENTLY.code();
      String connectionStatus=headers.get(HttpHeaders.Names.CONNECTION);
      String location=headers.get(HttpHeaders.Names.LOCATION);
      String eTag=redirected ?null :headers.get(HttpHeaders.Names.ETAG);
      String lastModified=redirected ?null :headers.get(HttpHeaders.Names.LAST_MODIFIED);
      boolean closed =
        "CLOSE".equals(connectionStatus) || "close".equals(connectionStatus);
      pageStart(currentURI, statusCode, eTag, lastModified, closed, redirected, location);
    }
    @Override public void body(HttpContent body) throws Exception {
      pageBody(currentURI, body.content().toString(CharsetUtil.UTF_8));
    }
    @Override public void complete(HttpHeaders trailingHeaders) throws Exception {
      URI temp = currentURI;
      currentURI = null;
      if (pageComplete(temp)){
        URI u = getNext();
        if (u!=null) read(u);
      }
      else
        channel.close();
    }
  };

}
