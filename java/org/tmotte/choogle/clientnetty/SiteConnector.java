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
import org.tmotte.choogle.pagecrawl.Link;


/** Only used by SiteCrawler. */
public final class SiteConnector {
  private EventLoopGroup group;
  private ChannelInitializer<SocketChannel> initializer;
  private String host;
  private int port;

  public SiteConnector(EventLoopGroup elg, Chreceiver r, String host, int port, boolean ssl) throws Exception {
    this.group=elg;
    this.initializer=
      new ChClientInitializer(
        ssl
          ?SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
          :null
        ,
        new ChClientHandler(r)
      );
    this.host=host;
    this.port=port;
  }
  public static SiteConnector create(EventLoopGroup elg, Chreceiver r, URI uri) throws Exception {
    return new SiteConnector(elg, r, uri.getHost(), uri.getPort(), uri.getScheme().equals("https"));
  }

  public Channel connect() throws Exception {
    return new Bootstrap()
      .group(group)
      .channel(NioSocketChannel.class)
      .handler(initializer)
      .connect(host, port)
      .sync()
      .channel();
  }

}
