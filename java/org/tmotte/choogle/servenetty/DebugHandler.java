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
import io.netty.handler.codec.http.HttpVersion;
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

public class DebugHandler extends SimpleChannelInboundHandler<Object> {

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

      // Pipelining:
      send100Continue(ctx);

      // Basics:
      buf.setLength(0);
      buf.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
      buf.append("===================================\r\n");
      buf.append("VERSION: ").append(request.getProtocolVersion()).append("\r\n");
      buf.append("REQUEST_URI: ").append(request.getUri()).append("\r\n\r\n");
      //buf.append("HOSTNAME: ").append(HttpHeaders.getHost(request, "unknown")).append("\r\n");

      // Headers:
      HttpHeaders headers = request.headers();
      if (!headers.isEmpty()) {
        for (Map.Entry<String, String> h: headers)
          buf
            .append("HEADER: ")
            .append(h.getKey())
            .append(" = ")
            .append(h.getValue())
            .append("\r\n");
        buf.append("\r\n");
      }

      // Query string:
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
      Map<String, List<String>> params = queryStringDecoder.parameters();
      if (!params.isEmpty()) {
        for (Entry<String, List<String>> p: params.entrySet()) {
          String key = p.getKey();
          for (String val : p.getValue())
            buf.append("PARAM: ").append(key).append(" = ").append(val).append("\r\n");
        }
        buf.append("\r\n");
      }

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

        LastHttpContent trailer = (LastHttpContent) msg;
        if (!trailer.trailingHeaders().isEmpty()) {
          buf.append("\r\n");
          for (String name: trailer.trailingHeaders().names()) {
            for (String value: trailer.trailingHeaders().getAll(name)) {
              buf.append("TRAILING HEADER: ");
              buf.append(name).append(" = ").append(value).append("\r\n");
            }
          }
          buf.append("\r\n");
        }

        if (!writeResponse(trailer, ctx)) {
          // If keep-alive is off, close the connection once the content is fully written.
          System.out.println("No keepalive");
          ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
      }
    }
  }

  private static void appendDecoderResult(StringBuilder buf, HttpObject o) {
    DecoderResult result = o.getDecoderResult();
    if (result.isSuccess())
      return;
    buf.append(".. WITH DECODER FAILURE: ");
    buf.append(result.cause());
    buf.append("\r\n");
  }


  private boolean writeResponse(HttpObject currentObj, ChannelHandlerContext ctx) {
    boolean keepAlive = HttpHeaders.isKeepAlive(request);
    FullHttpResponse response = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1,
      currentObj.getDecoderResult().isSuccess()
        ? HttpResponseStatus.OK
        : HttpResponseStatus.BAD_REQUEST,
      Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8)
    );
    response.headers().set(Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

    if (keepAlive) {
      response.headers().set(Names.CONTENT_LENGTH, response.content().readableBytes());
      response.headers().set(Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    }

    // If the browser sent us cookies, keep them. Else add some.
    String cookieString = request.headers().get(Names.COOKIE);
    if (cookieString != null) {
      Set<Cookie> cookies=ServerCookieDecoder.LAX.decode(cookieString);
      if (!cookies.isEmpty())
        for (Cookie cookie: cookies)
          response.headers().add(Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
    } else {
      response.headers().add(Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode("key1", "value1"));
      response.headers().add(Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode("key2", "value2"));
    }

    // Write the response.
    ctx.write(response);
    return keepAlive;
  }

  private void send100Continue(ChannelHandlerContext ctx) {
    if (HttpHeaders.is100ContinueExpected(request))
      ctx.write(
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
      );
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
