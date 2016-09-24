package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.tmotte.common.text.Outlog;

/**
 * Used by WorldCrawler to track success/fail of SiteCrawler connections.
 */
class SiteWatcher implements Runnable {

  private final long startTime=System.currentTimeMillis();
  private List<SiteCrawler> sites=new ArrayList<>(30);
  private HashSet<Object> already=new HashSet<>();
  private int siteCount=0;
  private int sitesDone=0;
  private Outlog log;
  private Runnable callOnFinish;

  public SiteWatcher(Outlog log, Runnable callOnFinish) {
    this.log=log;
    this.callOnFinish=callOnFinish;
    new Thread(this, "Choogle Watcher Thread").start();
  }
  synchronized void add(int more) {
    siteCount+=more;
  }
  synchronized void siteDone(SiteCrawler sc) {
    logOnClose(sc);
    Object hashNote=sc.getConnectionKey();
    if (!already.contains(hashNote)) {
      already.add(hashNote);
      sites.add(sc);
      this.notify();
    }
  }
  public void run() {
    List<SiteCrawler> recrawls=new ArrayList<SiteCrawler>(5);
    HashSet<String> alreadyRetried=new HashSet<String>();
    boolean allDone = false;
    while (true) {

      synchronized(this) {
        // Wait for notification from a site:
        try {this.wait();}
          catch (InterruptedException e) {
            e.printStackTrace();
          }

        // Now schedule recrawls and find out if we're done:
        for (SiteCrawler sc: sites) {
          if (log.is(1)) logReconnectCheck(sc);
          if (!alreadyRetried.contains(sc.getConnectionKey())){
            alreadyRetried.add(sc.getConnectionKey());
            if (sc.needsReconnect())
              recrawls.add(sc);
            else
              allDone=incrementDone();
          }
        }
        sites.clear();
      }

      // Recrawl as asked; if that doesn't happen, it's a failure:
      for (SiteCrawler sc: recrawls){
        boolean b=false;
        try {
          logReconnect(sc);
          b=sc.reconnectIfUnfinished();
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (!b) {
          logReconnectFail(sc);
          allDone=incrementDone();
        }
      }

      // Reset state:
      recrawls.clear();

      // Bail if all done:
      if (allDone) {
        logDone();
        callOnFinish.run();
        return;
      }
    }
  }
  private synchronized boolean incrementDone() {
    sitesDone++;
    logSiteDone(sitesDone, siteCount);
    return sitesDone==siteCount;
  }
  private void logOnClose(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc.getConnectionKey()).add(" CONNECTION CLOSE DETECTED").lf();
  }
  private void logReconnectCheck(SiteCrawler sc) {
    log.date().add(sc).add(" CHECKING FOR RECONNECT")
      .lf();
  }
  private void logReconnect(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc).add(" ATTEMPTING RECONNECT/REDIRECT")
        .lf();
  }
  private void logReconnectFail(SiteCrawler sc) {
    log.date().add("ERROR: Asked for recrawl and didn't: ").add(sc)
      .lf();
  }
  private void logSiteDone(int completed, int count) {
    if (log.is(1))
      log.date()
        .add("COMPLETED ").add(completed)
        .add(" OF ").add(count).add(" SITES")
        .lf();
  }
  private void logDone() {
    if (log.is(1))
      try {
        log.date()
          .add(
            String.format("Completed in %d ms", System.currentTimeMillis()-startTime)
          )
          .add("... cleaning up...")
          .lf();
      } catch (Exception e) {
        e.printStackTrace();
      }

  }
}
