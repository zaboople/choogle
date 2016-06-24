package org.tmotte.choogle.clientnetty;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.ssl.SslContext;

public class ChClientInitializer extends ChannelInitializer<SocketChannel> {

  private final SslContext sslCtx;
  private final SimpleChannelInboundHandler<HttpObject> handler;

  public ChClientInitializer(SslContext sslCtx, SimpleChannelInboundHandler<HttpObject> handler) {
    this.sslCtx = sslCtx;
    this.handler = handler;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();

    if (sslCtx != null)
      p.addLast(sslCtx.newHandler(ch.alloc()));

    p.addLast(new HttpClientCodec());

    // Remove the following line if you don't want automatic content decompression.
    p.addLast(new HttpContentDecompressor());

    // Uncomment the following line if you don't want to handle HttpContents.
    //p.addLast(new HttpObjectAggregator(1048576));

    p.addLast(handler);
  }
}