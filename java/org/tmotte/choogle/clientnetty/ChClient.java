package org.tmotte.choogle.clientnetty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import org.tmotte.choogle.chug.Link;

/**
 * A simple HTTP client that prints out the content of the HTTP response to
 * {@link System#out} to test {@link HttpSnoopServer}.
 */
public final class ChClient {
  //FIXME DON'T NEED TO CREATE SO MANY NEW INSTANCES
  private EventLoopGroup group;
  private ChannelInitializer<SocketChannel> initializer;

  public ChClient(EventLoopGroup elg, ChannelInitializer<SocketChannel> ci) {
    this.group=elg;
    this.initializer=ci;
  }
  public ChClient(EventLoopGroup elg, boolean ssl, SimpleChannelInboundHandler<HttpObject> handler) throws Exception {
    this(
      elg,
      new ChClientInitializer(
        ssl
          ?SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
          :null
        ,
        handler
      )
    );
  }
  public ChClient(EventLoopGroup elg, boolean ssl, Chreceiver r) throws Exception {
    this(elg, ssl, new ChClientHandler(r));
  }

  public Channel connect(String host, int port) throws Exception {
    return new Bootstrap()
      .group(group)
      .channel(NioSocketChannel.class)
      .handler(initializer)
      .connect(host, port)
      .sync()
      .channel();
  }
  public Channel connect(URI uri) throws Exception {
    String host = uri.getHost();
    int port = uri.getPort();
    if (port == -1) {
      String scheme = uri.getScheme();
      if (scheme==null) scheme="http";
      if ("http".equalsIgnoreCase(scheme))
        port = 80;
      else
      if ("https".equalsIgnoreCase(scheme))
        port = 443;
      else
        throw new Exception("Can't derive port");
    }
    return connect(host, port);
  }

  public ChannelFuture read(String uriStr) throws Exception {
    return read(Link.getURI(uriStr));
  }
  public ChannelFuture read(URI uri) throws Exception {
    Channel c=connect(uri);
    read(uri, c);
    return c.closeFuture();
  }

  public static void read(URI uri, Channel channel) throws Exception {
    try {

      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(
        HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath()
      );
      request.headers().set(HttpHeaders.Names.HOST, uri.getHost());
      request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
      //setFakeCookie(request);

      // Send the HTTP request.
      System.out.println("READING "+uri);
      channel.writeAndFlush(request);
      System.out.println("AND? "+uri);
      //return channel.closeFuture();//.sync();

    } finally {
    }
  }


  private static void setFakeCookie(HttpRequest request) throws Exception {
    request.headers().set(
     HttpHeaders.Names.COOKIE,
      ClientCookieEncoder.LAX.encode(
        new DefaultCookie("my-cookie", "foo"),
        new DefaultCookie("another-cookie", "bar")
      )
    );
  }
}