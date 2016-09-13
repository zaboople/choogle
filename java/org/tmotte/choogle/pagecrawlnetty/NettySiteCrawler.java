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
import org.tmotte.choogle.pagecrawl.SiteCrawler;
import org.tmotte.common.nettyclient.MyResponseReceiver;
import org.tmotte.common.nettyclient.MySiteConnector;

/**
 * Note: Test content type acceptance with apache.org, which has lots of PDF's.
 */
public final class NettySiteCrawler extends SiteCrawler {

  private final EventLoopGroup elGroup;
  private volatile InsaneChannel currChan=new InsaneChannel();
  private URI currentURI;
  private boolean onHead=true;

  public NettySiteCrawler(
      EventLoopGroup elGroup,
      Consumer<SiteCrawler> whenComplete,
      long limit,
      int debugLevel,
      boolean cacheResults
    ) throws Exception{
    super(whenComplete, limit, debugLevel, cacheResults);
    this.elGroup=elGroup;
  }

  protected @Override void doHead(URI uri) throws Exception {
    onHead=true;
    startRequest(uri);
  }
  protected @Override void doGet(URI uri) throws Exception {
    onHead=false;
    startRequest(uri);
  }

  //////////////////////
  // PRIVATE METHODS: //
  //////////////////////

  private static class InsaneChannel {
    private Channel c;
    private boolean hasChannel=false;
    public synchronized Channel getChannel() {
      return c;
    }
    public synchronized Channel setChannel(Channel newChan) {
      c=newChan;
      hasChannel=c!=null;
      return c;
    }
    public synchronized String toString() {
      return String.valueOf(hasChannel)+String.valueOf(c);
    }
  }

  private void startRequest(URI uri) throws Exception {
    //FIXME I think chan should be combined with currentURI and MyReceiver
    //into one big chunk of state. In here we update it as necessary.
    currentURI=uri;
    Channel chan = currChan.getChannel();
    if (chan==null) {
      chan=connect(uri);
    }
    else
    if (!chan.isOpen() || !chan.isActive()) {
      // wHEN THIS HAPPENS
      currChan.setChannel(null);
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
    // This does no good:
    //request.headers().set("Accept", "text/*");
    chan.writeAndFlush(request);
  }

  private Channel connect(URI uri) throws Exception {
    if (debug(1))
      System.out.append("CONNECT: ").append(uri.toString()).append("\n");
    Channel chan = MySiteConnector.connect(elGroup, myReceiver, uri);
    // FIXME the problem is this doesn't get called when the site closes us on its own will.
    // Really no I'm not kidding.
    chan.closeFuture().addListener(future -> onClose());
    currChan.setChannel(chan);
    return chan;
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
      pageStart(
        currentURI,
        statusCode, contentType,
        eTag, lastModified,
        closed, redirected,
        location
      );
    }
    @Override public void body(HttpContent body) throws Exception {
      if (!onHead)
        pageBody(currentURI, body.content().toString(CharsetUtil.UTF_8));
    }
    /**
     * The problem here is that we're closing a channel when we may have already
     * obtained another. This method is on a different thread from other things.
     */
    @Override public void complete(HttpHeaders trailingHeaders) throws Exception {
      URI temp = currentURI;
      currentURI = null;
      System.out.flush();
      if (!pageComplete(temp, onHead)) {
        Channel chan = currChan.getChannel();
        if (chan != null)
          chan.close();
        else
          currChan.setChannel(null);
      }
    }
  };
  private static void flushln(String s) {
    System.out.println(Thread.currentThread()+s);
    System.out.flush();
  }
}
