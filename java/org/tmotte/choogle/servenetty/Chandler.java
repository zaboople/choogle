package org.tmotte.choogle.servenetty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.CharsetUtil;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import static io.netty.handler.codec.http.HttpVersion.*; //FIXME

public class Chandler extends SimpleChannelInboundHandler<Object> {

  // Everything is single-threaded so we're cool with holding stuff
  // in variables like this:
  private final StringBuilder buf = new StringBuilder();
  private HttpRequest request;

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpRequest) {
      this.request = (HttpRequest) msg;
      buf.append("<html><body>");

      // Pipelining:
      send100Continue(ctx);

      // Basics:
      buf.setLength(0);

      // Headers:
      HttpHeaders headers = request.headers();

      buf.append(request.getUri());
      buf.append("<br>");

      // Query string:
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      Map<String, List<String>> params = queryStringDecoder.parameters();

      appendDecoderResult(buf, request);
    }

    if (msg instanceof HttpContent) {
      HttpContent httpContent = (HttpContent) msg;

      ByteBuf content = httpContent.content();
      if (content.isReadable()) {
        buf.append("CONTENT: ");
        buf.append(content.toString(CharsetUtil.UTF_8));
        buf.append("\r\n");
        appendDecoderResult(buf, request);
      }

      if (msg instanceof LastHttpContent) {
        buf.append("END OF CONTENT\r\n");
        buf.append("</body></html>");

        LastHttpContent trailer = (LastHttpContent) msg;
        HttpHeaders headers=trailer.trailingHeaders();

        if (!writeResponse(trailer, ctx)) {
          // If keep-alive is off, close the connection once the content is fully written.
          System.out.println("No keepalive");
          ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
      }
    }
  }

  private static void appendDecoderResult(StringBuilder buf, HttpObject o) {
    if (!o.getDecoderResult().isSuccess())
      throw new RuntimeException(o.getDecoderResult().cause());
  }


  private boolean writeResponse(HttpObject currentObj, ChannelHandlerContext ctx) {
    boolean keepAlive = HttpHeaders.isKeepAlive(request);
    FullHttpResponse response = new DefaultFullHttpResponse(
      HTTP_1_1,
      currentObj.getDecoderResult().isSuccess()
        ? HttpResponseStatus.OK
        : HttpResponseStatus.BAD_REQUEST,
      Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8)
    );
    response.headers().set(Names.CONTENT_TYPE, "text/html; charset=UTF-8");

    if (keepAlive) {
      response.headers().set(Names.CONTENT_LENGTH, response.content().readableBytes());
      response.headers().set(Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    }

    // Write the response.
    ctx.write(response);
    return keepAlive;
  }

  private void send100Continue(ChannelHandlerContext ctx) {
    if (HttpHeaders.is100ContinueExpected(request)) {
      FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.CONTINUE);
      ctx.write(response);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
