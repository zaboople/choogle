package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.tmotte.common.text.Outlog;

/**
 * Does most of the work that WorldCrawler should be doing.
 * Hides out in the background and lets WorldCrawler have the glory.
 */
class SiteWatcher {

  // Immutable inputs:
  private final Outlog log;
  private final SiteConnectionFactory connFactory;
  private final boolean cacheResults;
  private final Runnable callOnAllDone;

  // Just for logging:
  private final long startTime=System.currentTimeMillis();
  private final SiteWatcherDebug debug;

  // This is all the synchronous state that we have to be careful with:
  private final List<SiteCrawler> sitesClosed=new ArrayList<>(30);
  private final List<SiteStarter> siteStarts=new ArrayList<>(30);
  private int siteCount=0;
  private int sitesDone=0;

  /**
   * @param callOnAllDone This is really just a reference back to WorldCrawler.
   */
  SiteWatcher(
      Outlog log,
      SiteConnectionFactory factory,
      boolean cacheResults,
      Runnable callOnAllDone
    ) {
    this.log=log;
    this.connFactory=factory;
    this.cacheResults=cacheResults;
    this.callOnAllDone=callOnAllDone;

    debug=new SiteWatcherDebug(log);
    new Thread(
      ()->checkCrawlers(), "Choogle Watcher Thread"
    ).start();
  }
  void crawl(List<String> uris, long limit) throws Exception {
    addToCount(uris.size());
    for (String uri : uris){
      if (!uri.startsWith("http"))
        uri="http://"+uri;
      new SiteStarter(log, connFactory, limit, this::siteStartStopped).start(uri);
    }
  }


  //////////////////////
  // PRIVATE METHODS: //
  //////////////////////

  /**
   * Call this *before* starting crawlers up. This tells us
   * how many sites need to complete before we can exit.
   */
  private synchronized void addToCount(int more) {
    siteCount+=more;
  }
  private synchronized void siteStartStopped(SiteStarter ss) { //FIXME make it private, pull the
    siteStarts.add(ss);
    this.notify();
  }
  private synchronized void siteClosed(SiteCrawler sc) {
    debug.onClose(sc);
    sitesClosed.add(sc);
    this.notify();
  }
  private synchronized boolean incrementDone() {
    sitesDone++;
    debug.siteDone(sitesDone, siteCount);
    return sitesDone==siteCount;
  }

  private void checkCrawlers() {
    // We will just about but not necessarily always get 2 calls
    // on connection close, so we store a reference to keep us
    // clued in.
    HashSet<String> alreadyRetried=new HashSet<String>();
    List<SiteData>
      redirects=new ArrayList<>(5),
      recrawls=new ArrayList<>(5);
    boolean allDone = false;
    while (true) {

      synchronized(this) {
        // Wait for notification from a site:
        try {this.wait();}
          catch (InterruptedException e) {
            e.printStackTrace();
          }

        // Now schedule redirects and find out if we're done:
        for (SiteStarter ss: siteStarts) {
          String key=ss.getConnectionKey();
          if (!alreadyRetried.contains(key)){
            alreadyRetried.add(key);
            URI
              redirectURI=ss.getRedirectURI(),
              currentURI=ss.getCurrentURI();
            if (redirectURI!=null)
              redirects.add(new SiteData(redirectURI, ss.getLimit()));
            else
            if (currentURI!=null)
              recrawls.add(new SiteData(currentURI, ss.getLimit()));
            else {
              System.out.println("COULD NOT FIND SITE FOR "+key);
              allDone=incrementDone();
            }
          }
        }
        siteStarts.clear();

        // Now schedule recrawls and find out if we're done:
        for (SiteCrawler sc: sitesClosed) {
          String key=sc.getConnectionKey();
          URI uri=sc.getReconnectURI();
          debug.reconnectCheck(sc);
          if (!alreadyRetried.contains(key)){
            alreadyRetried.add(key);
            if (uri!=null)
              recrawls.add(new SiteData(uri, sc.getLimit(), sc));
            else
              allDone=incrementDone();
          }
        }
        sitesClosed.clear();
      }


      // Now run our retries, or if we're all done, bail.
      // Note that all of this can be done non-synchronously
      // because we're leaving class state alone.
      retry(redirects, recrawls);
      if (allDone) {
        debug.done(startTime);
        callOnAllDone.run();
        return;
      }
    }
  }

  /** This does not need to be synchronous */
  private void retry(List<SiteData> redirects, List<SiteData> recrawls) {
    for (SiteData redirect: redirects)
      try {
        debug.redirect(redirect.uri);
        new SiteStarter(
          log, connFactory, redirect.limit, this::siteStartStopped
        ).start(redirect.uri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    for (SiteData recrawl: recrawls)
      try {
        new SiteCrawler(
          log, connFactory, recrawl.limit, this::siteClosed, cacheResults, recrawl.oldCrawler
        ).start(recrawl.uri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    // Reset state:
    redirects.clear();
    recrawls.clear();
  }

  private static class SiteData {
    URI uri;
    long limit;
    SiteCrawler oldCrawler;
    public SiteData(URI uri, long limit) {
      this(uri, limit, null);
    }
    public SiteData(URI uri, long limit, SiteCrawler oldCrawler) {
      this.uri=uri;
      this.limit=limit;
      this.oldCrawler=oldCrawler;
    }
  }

}
