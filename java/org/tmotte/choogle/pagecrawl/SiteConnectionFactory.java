package org.tmotte.choogle.pagecrawl;
import java.net.URI;

public interface SiteConnectionFactory {
  public SiteConnection get(SiteCrawler sc, URI uri) throws Exception ;
  public void finish() throws Exception ;
}
