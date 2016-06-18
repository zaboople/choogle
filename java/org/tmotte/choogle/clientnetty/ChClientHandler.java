package org.tmotte.choogle.clientnetty;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;


/**
 * Implements Sharable so we don't have to create numerous instances.
 */
@io.netty.channel.ChannelHandler.Sharable
public class ChClientHandler extends SimpleChannelInboundHandler<HttpObject> {
  private Chreceiver receiver;
  public ChClientHandler(Chreceiver receiver) {
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
