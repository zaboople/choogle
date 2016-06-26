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
import java.util.HashMap;
import java.util.Map;
import org.tmotte.choogle.chug.AnchorReader;
import org.tmotte.choogle.chug.Link;
import org.tmotte.common.text.HTMLParser;

/**
 * Crawls a group of web sites in parallel, waiting for all to finish.
 */
public final class WorldCrawler  {

  public static void crawl(List<String> uris, int limit, int debugLevel) throws Exception {
    WorldCrawler wc=new WorldCrawler(limit, debugLevel);
    wc.crawl(uris);
  }

  private final EventLoopGroup elGroup=new NioEventLoopGroup();
  private final int limit;
  private final int debugLevel;

  public WorldCrawler(int limit, int debugLevel){
    this.limit=limit;
    this.debugLevel=debugLevel;
  }
  public void crawl(List<String> uris) throws Exception {
    try {
      crawl(uris, true);
    } finally {
      elGroup.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
  }

  private List<SiteCrawler> crawl(List<String> uris, boolean retryOnce) throws Exception {
    List<SiteCrawler> crawlers=new ArrayList<>(uris.size());
    for (String u : uris) {
      SiteCrawler sc=new SiteCrawler(elGroup, u, limit, debugLevel);
      crawlers.add(sc);
      sc.start();
    }
    for (SiteCrawler sc: crawlers) sc.finish().sync(); //FIXME don't wait until all are finished to recrawl

    // This handles the case where the initial page caused a redirect, from foo.com to www.foo.com,
    // or maybe from http to https, etc. Note the once-only recursion.
    if (retryOnce) {
      uris=null;
      for (SiteCrawler sc: crawlers){
        URI newURI=sc.wasSiteRedirect();
        if (newURI!=null){
          if (uris==null) uris=new ArrayList<String>();
          uris.add(newURI.toString());
        }
      }
      if (uris!=null)
        crawl(uris, false);
    }
    return crawlers;
  }
}
