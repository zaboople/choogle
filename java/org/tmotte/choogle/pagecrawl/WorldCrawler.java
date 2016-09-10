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
      //FIXME don't wait until all are finished to recrawl
      List<SiteCrawler> crawlers=start(uris);
      do {
        for (int i=crawlers.size()-1; i>=0; i--) {
          SiteCrawler sc = crawlers.get(i);
          sc.close();
          if (!crawlers.get(i).reconnectIfUnfinished())
            crawlers.remove(i);
        }
      } while (crawlers.size()>0);
    } finally {
      finish();
    }
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
