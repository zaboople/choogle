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

        //output.append("\nSTATUS: " + response.getStatus());
        //output.append("\nVERSION: " + response.getProtocolVersion());
        //output.append("\n");

        //if (!response.headers().isEmpty()) {
        //  for (String name: response.headers().names())
        //    for (String value: response.headers().getAll(name))
        //      output.append("\nHEADER: " + name + " = " + value);
        //  output.append("\n");
        //}

        //if (HttpHeaders.isTransferEncodingChunked(response))
        //  output.append("\nCHUNKED CONTENT {");
        //else
        //  output.append("\nCONTENT {");
        //
      }
      if (msg instanceof HttpContent) {
        HttpContent content = (HttpContent) msg;
        receiver.body(content);
        //String s=content.content().toString(CharsetUtil.UTF_8);
        //output.append(s.substring(0, s.length()>200 ?200 :s.length()));

        if (content instanceof LastHttpContent) {
          receiver.complete();
          ctx.close();
        }
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
