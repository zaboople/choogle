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


/**
 * A simple HTTP client that prints out the content of the HTTP response to
 * {@link System#out} to test {@link HttpSnoopServer}.
 */
public final class ChClient {
  private EventLoopGroup group;
  private ChannelInitializer<SocketChannel> initializer;
  private Bootstrap bootstrap;
  private Channel channel;

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

  //FIXME DON'T NEED TO CREATE SO MANY NEW INSTANCES
  public ChannelFuture read(String uriStr) throws Exception {
    URI uri = new URI(uriStr);
    String scheme = uri.getScheme() == null? "http" : uri.getScheme();
    String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
    int port = uri.getPort();
    if (port != -1)
      {}
    else
    if ("http".equalsIgnoreCase(scheme))
      port = 80;
    else
    if ("https".equalsIgnoreCase(scheme))
      port = 443;
    else
      throw new Exception("Can't derive port");


    // Configure the client.
    try {

      // Make the connection attempt.
      channel=new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(initializer)
        .connect(host, port)
        .sync()
        .channel();

      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(
        HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath()
      );
      request.headers().set(HttpHeaders.Names.HOST, host);
      request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
      request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
      //request.headers().set(
      // HttpHeaders.Names.COOKIE,
      //  ClientCookieEncoder.LAX.encode(
      //    new DefaultCookie("my-cookie", "foo"),
      //    new DefaultCookie("another-cookie", "bar")
      //  )
      //);

      // Send the HTTP request.
      channel.writeAndFlush(request);
      return channel.closeFuture();//.sync();

    } finally {
    }
  }
}