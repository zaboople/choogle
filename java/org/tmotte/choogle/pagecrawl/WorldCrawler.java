package org.tmotte.choogle.pagecrawl;
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
public abstract class WorldCrawler  {

  //////////////////////////////////
  // PRIVATE STATE & CONSTRUCTOR: //
  //////////////////////////////////

  private final long limit;
  private final int debugLevel;
  private final boolean cacheResults;
  public WorldCrawler(long limit, int debugLevel, boolean cacheResults){
    this.limit=limit;
    this.debugLevel=debugLevel;
    this.cacheResults=cacheResults;
  }

  ///////////////////////////////////
  // Required protected functions: //
  ///////////////////////////////////

  protected abstract SiteCrawler createSiteCrawler(
    long limit, int debugLevel, boolean cacheResults
  ) throws Exception;
  protected abstract void finish();

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public void crawl(List<String> uris) throws Exception {
    try {
      crawl(uris, true);
    } finally {
      finish();
    }
  }

  /////////////////////////
  // INTERNAL FUNCTIONS: //
  /////////////////////////

  private List<SiteCrawler> crawl(List<String> uris, boolean retryOnce) throws Exception {
    List<SiteCrawler> crawlers=start(uris);
    for (SiteCrawler sc: crawlers) sc.finish(); //FIXME don't wait until all are finished to recrawl

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
      for (SiteCrawler sc: crawlers) sc.finish();
    } while (crawlers.size()>0);
    return crawlers;
  }

  private List<SiteCrawler> start(List<String> uris) throws Exception {
    List<SiteCrawler> crawlers=new ArrayList<>(uris.size());
    for (String uri : uris) {
      if (!uri.startsWith("http"))
        uri="http://"+uri;
      SiteCrawler sc=createSiteCrawler(limit, debugLevel, cacheResults);
      crawlers.add(sc);
      sc.start(uri);
    }
    return crawlers;
  }
}
