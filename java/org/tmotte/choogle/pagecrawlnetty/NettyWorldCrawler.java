package org.tmotte.choogle.pagecrawlnetty;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.tmotte.choogle.pagecrawl.SiteCrawler;
import org.tmotte.choogle.pagecrawl.WorldCrawler;

public final class NettyWorldCrawler extends WorldCrawler  {

  private final EventLoopGroup elGroup=new NioEventLoopGroup();

  public static void crawl(
      List<String> uris, long limit, int debugLevel, boolean cacheResults
    ) throws Exception {
    new NettyWorldCrawler(limit, debugLevel, cacheResults).crawl(uris);
  }

  public NettyWorldCrawler(long limit, int debugLevel, boolean cacheResults){
    super(limit, debugLevel, cacheResults);
  }

  protected @Override SiteCrawler createSiteCrawler(
      Consumer<SiteCrawler> whenComplete, long limit, int debugLevel, boolean cacheResults
    ) throws Exception {
    return new NettySiteCrawler(elGroup, whenComplete, limit, debugLevel, cacheResults);
  }

  protected @Override void finish(){
    elGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
  }

}
