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
import org.tmotte.common.text.HTMLParser;

public final class PageCrawler implements Chreceiver {

  public static void read(List<String> uris) throws Exception {
    EventLoopGroup elGroup=new NioEventLoopGroup();
    try {
      List<ChannelFuture> futures=new ArrayList<>(uris.size());
      for (String u : uris)
        futures.add(
          new ChClient(
            elGroup,
            u.startsWith("https"),
            new PageCrawler(u)
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
  private HTMLParser anchorBP=AnchorReader.withParser(urls);
  private String uri;

  public PageCrawler(String uri) {
    this.uri=uri;
  }
  public void start(HttpResponse headers){
    System.out.print(uri);
    System.out.println(" STARTING...");
  }
  public void body(HttpContent body){
    String s=body.content().toString(CharsetUtil.UTF_8);
    int len=s.length();
    for (int i=0; i<len; i++)
      anchorBP.add(s.charAt(i));
  }
  public void complete(){
    System.out.print(uri);
    System.out.println(" COMPLETE");
    for (Link v : urls)
      System.out.println(v);
  }
}
