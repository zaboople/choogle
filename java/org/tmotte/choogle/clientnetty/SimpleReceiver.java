package org.tmotte.choogle.clientnetty;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import org.tmotte.choogle.chug.AnchorReader;
import org.tmotte.choogle.chug.Link;

public final class SimpleReceiver implements Chreceiver {


  public static void read(List<String> uris) throws Exception {
    EventLoopGroup elGroup=new NioEventLoopGroup();
    try {
      List<ChannelFuture> futures=new ArrayList<>(uris.size());
      for (String u : uris)
        futures.add(
          new ChClient(
            elGroup,
            u.startsWith("https"),
            new SimpleReceiver(u)
          ).read(u)
        );
      for (ChannelFuture f : futures){
        f.sync();
        System.out.flush();
      }
    } finally {
      elGroup.shutdownGracefully();
    }
  }

  private List<Link> urls=new ArrayList<>(100);
  private AnchorReader collector=new AnchorReader(urls);
  private String uri;

  public SimpleReceiver(String uri) {
    this.uri=uri;
  }
  public void start(HttpResponse headers){
    System.out.print(uri);
    System.out.println(" STARTING...");
  }
  public void body(HttpContent body){
    String s=body.content().toString(CharsetUtil.UTF_8);
    collector.add(s);
  }
  public void complete(){
    System.out.print(uri);
    System.out.println(" COMPLETE");
    for (Link v : urls)
      System.out.println(v);
  }

}
