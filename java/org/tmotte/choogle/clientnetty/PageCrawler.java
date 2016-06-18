package org.tmotte.choogle.clientnetty;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.channel.Channel;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.HashSet;
import org.tmotte.choogle.chug.AnchorReader;
import org.tmotte.choogle.chug.Link;
import org.tmotte.common.text.HTMLParser;

/** FIXME handle redirects */
/** FIXME It is important to call close() or close(ChannelPromise) to release all resources once you are done with the Channel. This ensures all resources are released in a proper way, i.e. filehandles.*/

public final class PageCrawler implements Chreceiver {
  Channel channel;
  public void read(List<String> uris) throws Exception {
    System.out.println("WE are gonna read "+uris);
    EventLoopGroup elGroup=new NioEventLoopGroup();
    try {
      List<ChannelFuture> futures=new ArrayList<>(uris.size());
      for (String u : uris){
        ChClient client=new ChClient(
          elGroup,
          u.startsWith("https"),
          this
        );
        System.out.println("PageCrawler getting channel...");
        channel=client.connect(uri);
        System.out.println("PageCrawler telling client to read...");
        ChClient.read(uri, channel);
        System.out.println("PageCrawler is done with.");
        System.out.println("SYNC");
        //ChannelFuture cf=channel.closeFuture().sync();
        System.out.println("Synced");
        //channel.close();
        System.out.println("Closed");
      }
    } finally {
      //elGroup.shutdownGracefully();
    }
  }

  private Collection<Link> urls=new HashSet<>(100);
  private AnchorReader anchorBP=new AnchorReader(urls);
  private URI uri;
  private int count=0;

  public PageCrawler(String uri) {
    this.uri=Link.getURI(uri);
  }
  @Override public void start(HttpResponse resp){
    System.out.println(" STARTING...");
    HttpHeaders headers = resp.headers();
    System.out.print(headers.entries());
  }
  @Override public void body(HttpContent body){
    String s=body.content().toString(CharsetUtil.UTF_8);
    if (s.length()<20)
      System.out.println(s);
    else
      System.out.println(s.substring(s.length()-20));
    anchorBP.add(
      body.content().toString(CharsetUtil.UTF_8)
    );
  }
  URI extra=null;//Link.getURI("http://www.npr.org/music/");

  @Override public void complete(){
    System.out.println("COMPLETE"+count+ " "+uri);
    count++;
    if (count==1)
      try {
        ChClient.read(uri.resolve("/music/"), channel);
      } catch (Exception e) {
        e.printStackTrace();
      }
  }
}
