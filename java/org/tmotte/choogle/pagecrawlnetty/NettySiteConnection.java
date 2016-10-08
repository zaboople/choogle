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
import java.util.function.Consumer;
import org.tmotte.choogle.pagecrawl.SiteReader;
import org.tmotte.choogle.pagecrawl.SiteConnection;
import org.tmotte.common.nettyclient.MyResponseReceiver;
import org.tmotte.common.nettyclient.MySiteConnector;
import org.tmotte.common.text.Outlog;

public final class NettySiteConnection implements SiteConnection {

  // Rather static:
  private final EventLoopGroup elGroup;
  private final Outlog log;
  private SiteReader crawler;

  // Transient:
  private Channel currChannel;
  private URI currentURI;
  private boolean onHead=true;


  public NettySiteConnection(EventLoopGroup elGroup, SiteReader crawler, Outlog log) throws Exception{
    this.elGroup=elGroup;
    this.crawler=crawler;
    this.log=log;
  }

  public @Override void doHead(URI uri) throws Exception {
    onHead=true;
    startRequest(uri);
  }
  public @Override void doGet(URI uri) throws Exception {
    onHead=false;
    startRequest(uri);
  }
  public @Override void close() throws Exception {
    if (currChannel != null)
      currChannel.close();
  }

  //////////////////////
  // PRIVATE METHODS: //
  //////////////////////

  private void startRequest(URI uri) throws Exception {
    currentURI=uri;
    if (currChannel==null) {
      connect(uri);
    }
    else
    if (!currChannel.isOpen() || !currChannel.isActive()) {
      currChannel=null;
      startRequest(uri);
      return;
    }
    String rawPath=uri.getRawPath();
    HttpRequest request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1,
      onHead ? HttpMethod.HEAD :HttpMethod.GET,
      rawPath
    );
    request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
    request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    request.headers().set("Accept-Encoding", "gzip, deflate");
    request.headers().set("User-Agent", "choogle");
    // This does no good:
    //request.headers().set("Accept", "text/*");
    currChannel.writeAndFlush(request);
  }

  private void connect(URI uri) throws Exception {
    if (log.is(1)) log.date().add("CONNECT: ").add(uri).lf();
    currChannel = MySiteConnector.connect(elGroup, myReceiver, uri);
    currChannel.closeFuture().addListener(
      future -> {if (crawler!=null) crawler.onClose();}
    );
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
      String eTag=
        redirected ?null :headers.get(HttpHeaders.Names.ETAG);
      String lastModified=
        redirected ?null :headers.get(HttpHeaders.Names.LAST_MODIFIED);
      boolean closed =
        "CLOSE".equals(connectionStatus) || "close".equals(connectionStatus);
      crawler.pageStart(
        currentURI, onHead,
        statusCode, contentType,
        eTag, lastModified,
        closed, redirected,
        location
      );
    }
    @Override public void body(HttpContent body) throws Exception {
      if (!onHead)
        crawler.pageBody(currentURI, body.content().toString(CharsetUtil.UTF_8));
    }
    @Override public void complete(HttpHeaders trailingHeaders) throws Exception {
      URI temp=currentURI;
      currentURI=null;
      System.out.flush();
      crawler.pageComplete(temp, onHead);
    }
  };
  private static void flushln(String s) {
    System.out.println(Thread.currentThread()+s);
    System.out.flush();
  }
}
