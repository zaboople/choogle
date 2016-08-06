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
import java.util.Random;
import java.util.Set;

public class LoadTest extends SimpleChannelInboundHandler<Object> {

  // Everything is single-threaded so we're cool with holding stuff
  // in variables like this:
  private final StringBuilder buf = new StringBuilder();
  private HttpRequest request;
  private Random random=new java.util.Random(System.currentTimeMillis());

  private void makeContent(Appendable buffer, String indexStr) {
    try {
      // Create the next lowest index:
      long index=1;
      if (indexStr.length()>1)
        try {
          index=Long.parseLong(indexStr.substring(1))-1;
        } catch (Exception e) {
          buffer
            .append("Your input was supposed to be number and it failed to parse: ")
            .append(e.getMessage());
        }
      indexStr=String.valueOf(index);

      //Render HTML
      buffer.append("<html>\r\n");
      buffer.append("<head><title>")
        .append(String.valueOf(index))
        .append("</title></head>\r\n");
      buffer.append("<body>\r\n");
      buffer.append("<p>Random number: ");
      for (int i=0; i<100; i++)
        buffer.append(String.valueOf(Math.abs(random.nextInt())))
          .append(" ");
      buffer.append("</p>\n");
      if (index > 0)
        buffer.append("<a href=\"/")
          .append(indexStr)
          .append("\">Next link</a>");
      buffer.append("<br>");
      buffer.append(indexStr);
      buffer.append("<br></body></html>");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpRequest) {
      buf.setLength(0);
      this.request = (HttpRequest) msg;

      // Pipelining:
      send100Continue(ctx);

      HttpHeaders headers = request.headers();
      String uri=request.getUri();
      QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
      String path=qsd.path();

      //Get index from URI:
      int last=path.lastIndexOf("/");
      makeContent(buf, path.substring(last));

    }

    if (msg instanceof HttpContent) {
      HttpContent httpContent = (HttpContent) msg;
      ByteBuf content = httpContent.content();

      if (msg instanceof LastHttpContent) {

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

  private boolean writeResponse(HttpObject currentObj, ChannelHandlerContext ctx) {
    boolean keepAlive = HttpHeaders.isKeepAlive(request);
    FullHttpResponse response = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1,
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
    if (false) // I don't know how you write more than one thing:
      try {
        ByteBuf bb=new io.netty.buffer.UnpooledByteBufAllocator(false).buffer();
        bb.writeBytes("bullshit".getBytes("utf-8"));
        ctx.write(bb);
      } catch (Exception e) {
        e.printStackTrace();
      }
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
