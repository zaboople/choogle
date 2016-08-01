package org.tmotte.choogle.servenetty;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import java.util.function.Supplier;

/**
 * This puts our designated handler in the chain. It is only used by MyServer.
 * FIXME move to MyServer and also make the "real" handler a constructor input.
 */
public class MyInitializer extends ChannelInitializer<SocketChannel> {

  private final SslContext sslCtx;
  private final Supplier<SimpleChannelInboundHandler<Object>> handlerFactory;

  public MyInitializer(SslContext sslCtx, Supplier<SimpleChannelInboundHandler<Object>> handlerFactory) {
    this.sslCtx=sslCtx;
    this.handlerFactory=handlerFactory;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();
    if (sslCtx != null)
      p.addLast(sslCtx.newHandler(ch.alloc()));

    p.addLast(new HttpRequestDecoder());

    // Uncomment the following line if you don't want to handle HttpChunks.
    //p.addLast(new HttpObjectAggregator(1048576));

    p.addLast(new HttpResponseEncoder());

    // Remove the following line if you don't want automatic content compression.
    p.addLast(new HttpContentCompressor());

    p.addLast(handlerFactory.get());
  }
}
