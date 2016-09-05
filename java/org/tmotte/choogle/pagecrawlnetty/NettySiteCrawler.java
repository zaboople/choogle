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

/**
 * Test content type acceptance with apache.org, which has lots of PDF's.
 */
public final class NettySiteCrawler extends SiteCrawler {

  private final EventLoopGroup elGroup;
  private Channel channel;
  private URI currentURI;
  private boolean accepted=true, onHead=true;

  public NettySiteCrawler(
      EventLoopGroup elGroup, long limit, int debugLevel, boolean cacheResults
    ) throws Exception{
    super(limit, debugLevel, cacheResults);
    this.elGroup=elGroup;
  }

  public @Override void close() throws Exception {
    if (channel!=null) channel.closeFuture().sync();
    channel = null;
  }


  protected @Override void read(URI uri) throws Exception {
    accepted=true;
    onHead=true;
    startRequest(uri);
  }

  private void startRequest(URI uri) throws Exception {
    if (channel==null){
      if (debug(1))
        System.out.append("CONNECT: ").append(uri.toString()).append("\n");
      channel=MySiteConnector.connect(elGroup, myReceiver, uri);
    }
    else
    if (!channel.isOpen() || !channel.isActive()) {
      channel = null;
      startRequest(uri);
      return;
    }
    currentURI=uri;
    String rawPath=uri.getRawPath();
    HttpRequest request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1,
      onHead ? HttpMethod.HEAD :HttpMethod.GET,
      rawPath
    );
    request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
    request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    request.headers().set("Accept-Encoding", "gzip, deflate");
    // This does no good:
    //request.headers().set("Accept", "text/*");
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
      String contentType=headers.get(HttpHeaders.Names.CONTENT_TYPE);
      String connectionStatus=headers.get(HttpHeaders.Names.CONNECTION);
      String location=headers.get(HttpHeaders.Names.LOCATION);
      String eTag=redirected ?null :headers.get(HttpHeaders.Names.ETAG);
      String lastModified=redirected ?null :headers.get(HttpHeaders.Names.LAST_MODIFIED);
      boolean closed =
        "CLOSE".equals(connectionStatus) || "close".equals(connectionStatus);
      accepted =
        pageStart(
          currentURI,
          statusCode, contentType,
          eTag, lastModified,
          closed, redirected,
          location
        );
    }
    @Override public void body(HttpContent body) throws Exception {
      if (accepted && !onHead)
        pageBody(currentURI, body.content().toString(CharsetUtil.UTF_8));
    }
    @Override public void complete(HttpHeaders trailingHeaders) throws Exception {
      if (accepted && onHead){
        // Head succeeded, now do Get:
        onHead=false;
        startRequest(currentURI);
      }
      else {
        // 1. Tell crawler page is done (accepted or not)
        // 2. Close the connection if it says we have nothing left to read:
        URI temp = currentURI;
        currentURI = null;
        if (!pageComplete(temp))
          channel.close();
      }
    }
  };

}
