package org.tmotte.choogle.clientnetty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import org.tmotte.choogle.pagecrawl.Link;

/**
 * Takes a Chreceiver input and connects it to a Channel. When Requests are sent to that
 * Channel, the response will eventually arrive at the Chreceiver.
 */
final class SiteConnector {

  public static Channel connect(EventLoopGroup elg, Chreceiver r, URI uri) throws Exception {
    return connect(elg, r, uri.getHost(), uri.getPort(), uri.getScheme().equals("https"));
  }

  public static Channel connect(EventLoopGroup elg, Chreceiver r, String host, int port, boolean ssl) throws Exception {
    ChannelInitializer<SocketChannel> initializer =
      new MyClientInitializer(
        ssl
          ?SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
          :null
        ,
        new MyClientHandler(r)
      );
    return new Bootstrap()
      .group(elg)
      .channel(NioSocketChannel.class)
      .handler(initializer)
      .connect(host, port)
      .sync()
      .channel();
  }



  private static class MyClientHandler extends SimpleChannelInboundHandler<HttpObject> {
    private Chreceiver receiver;
    public MyClientHandler(Chreceiver receiver) {
      super();
      this.receiver=receiver;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
      try {
        if (msg instanceof HttpResponse) {
          HttpResponse response = (HttpResponse) msg;
          receiver.start(response);
        }
        if (msg instanceof HttpContent) {
          HttpContent content = (HttpContent) msg;
          receiver.body(content);
          if (content instanceof LastHttpContent)
            receiver.complete();
        }
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      cause.printStackTrace();
      ctx.close();
    }
  }


  private static class MyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final SimpleChannelInboundHandler<HttpObject> handler;

    public MyClientInitializer(SslContext sslCtx, SimpleChannelInboundHandler<HttpObject> handler) {
      this.sslCtx = sslCtx;
      this.handler = handler;
    }

    public @Override void initChannel(SocketChannel ch) {
      ChannelPipeline p = ch.pipeline();
      if (sslCtx != null)
        p.addLast(sslCtx.newHandler(ch.alloc()));
      p.addLast(new HttpClientCodec());
      // Remove the following line if you don't want automatic content decompression:
      p.addLast(new HttpContentDecompressor());
      // Uncomment the following line if you don't want to handle HttpContents:
      // p.addLast(new HttpObjectAggregator(1048576));
      p.addLast(handler);
    }
  }
}
