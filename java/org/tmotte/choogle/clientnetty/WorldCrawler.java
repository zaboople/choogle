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

  public void crawl(List<String> uris, int limit, int debugLevel) throws Exception {
    crawl(uris, limit, debugLevel, true);
  }

  private void crawl(List<String> uris, int limit, int debugLevel, boolean retryOnce) throws Exception {
    List<SiteCrawler> crawlers=new ArrayList<>(uris.size());
    EventLoopGroup elGroup=new NioEventLoopGroup();
    try {
      for (String u : uris) {
        SiteCrawler sc=new SiteCrawler(elGroup, u, limit, debugLevel);
        crawlers.add(sc);
        sc.start();
      }
      for (SiteCrawler sc: crawlers)
        sc.finish().sync();

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
          crawl(uris, limit, debugLevel, false);
      }
    } finally {
      elGroup.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
  }
}
