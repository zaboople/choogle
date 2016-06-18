import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.net.URI;


public class SiteReader {

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
}