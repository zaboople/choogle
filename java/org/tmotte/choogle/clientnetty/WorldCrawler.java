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

/**
 * Crawls a group of web sites in parallel, waiting for all to finish.
 */
public final class WorldCrawler  {

  ///////////////////////
  // STATIC FUNCTIONS: //
  ///////////////////////

  public static void crawl(
      List<String> uris, long limit, int debugLevel, boolean cacheResults
    ) throws Exception {
    WorldCrawler wc=new WorldCrawler(limit, debugLevel, cacheResults);
    wc.crawl(uris);
  }

  //////////////////////////////////
  // PRIVATE STATE & CONSTRUCTOR: //
  //////////////////////////////////

  private final EventLoopGroup elGroup=new NioEventLoopGroup();
  private final long limit;
  private final int debugLevel;
  private final boolean cacheResults;
  public WorldCrawler(long limit, int debugLevel, boolean cacheResults){
    this.limit=limit;
    this.debugLevel=debugLevel;
    this.cacheResults=cacheResults;
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public void crawl(List<String> uris) throws Exception {
    try {
      crawl(uris, true);
    } finally {
      elGroup.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private List<SiteCrawler> crawl(List<String> uris, boolean retryOnce) throws Exception {
    List<SiteCrawler> crawlers=start(uris);
    for (SiteCrawler sc: crawlers) sc.finish().sync(); //FIXME don't wait until all are finished to recrawl

    // This handles the case where the initial page caused a redirect, from foo.com to www.foo.com,
    // or maybe from http to https, etc. Note the once-only recursion.
    if (retryOnce) {
      uris=new ArrayList<>(uris.size());
      for (SiteCrawler sc: crawlers){
        URI newURI=sc.wasSiteRedirect();
        if (newURI!=null)
          uris.add(newURI.toString());
      }
      if (uris.size()>0)
        crawl(uris, false);
    }


    do {
      for (int i=crawlers.size()-1; i>=0; i--)
        if (!crawlers.get(i).reconnectIfUnfinished())
          crawlers.remove(i);
      for (SiteCrawler sc: crawlers) sc.finish().sync();
    } while (crawlers.size()>0);
    return crawlers;
  }

  private List<SiteCrawler> start(List<String> uris) throws Exception {
    List<SiteCrawler> crawlers=new ArrayList<>(uris.size());
    for (String u : uris) {
      if (!u.startsWith("http"))
        u="http://"+u;
      SiteCrawler sc=new SiteCrawler(elGroup, u, limit, debugLevel, cacheResults);
      crawlers.add(sc);
      sc.start();
    }
    return crawlers;
  }
}
