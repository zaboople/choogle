package org.tmotte.choogle.servenetty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.util.function.Supplier;

/**
  * An HTTP server that sends back the content of the received HTTP request
  * in a pretty plaintext form.
  */
public final class MyServer {


  public static void serve(Supplier<SimpleChannelInboundHandler<Object>> handlerFactory) throws Exception {

    final boolean SSL = System.getProperty("ssl") != null;
    final int PORT = Integer.parseInt(
      System.getProperty("port", SSL? "8443" : "8080")
    );

    // Configure SSL.
    final SslContext sslCtx;
    if (SSL) {
      SelfSignedCertificate ssc = new SelfSignedCertificate();
      sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    } else
      sslCtx = null;

    // Configure the server.
    EventLoopGroup
      bossGroup = new NioEventLoopGroup(1),
      workerGroup = new NioEventLoopGroup();
    try {
      Channel ch =
        new ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new MyInitializer(sslCtx, handlerFactory))
        .bind(PORT).sync()
        .channel();
      System.out.println(
        "Listening for "
        +(SSL? "https" : "http") + "://127.0.0.1:" + PORT + '/'
      );
      ch.closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }
}
