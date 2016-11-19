package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import org.tmotte.common.text.Outlog;

class WorldWatcherDebug {
  private final Outlog log;

  WorldWatcherDebug(Outlog log) {
    this.log=log;
  }

  void onClose(SiteCrawler sc) {
    if (log.is(1)) {
      int outstanding=0;
      try {
        outstanding = sc.getSiteState().getScheduledSize();
      } catch (Exception e) {}
      log.date()
        .add(sc.getConnectionKey())
        .add(" CONNECTION CLOSE DETECTED; OUTSTANDING: ")
        .add(outstanding)
        .lf();
    }
  }
  void redirect(URI uri) {
    if (log.is(1))
      log.date().add("REDIRECT TO: ").add(uri).lf();
  }
  void reconnectCheck(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc).add(" CHECKING FOR RECONNECT")
        .lf();
  }
  void reconnect(SiteCrawler sc) {
    if (log.is(1))
      log.date().add(sc).add(" ATTEMPTING RECONNECT/REDIRECT")
        .lf();
  }
  void reconnectFail(SiteCrawler sc) {
    log.date().add("ERROR: Asked for recrawl and didn't: ").add(sc)
      .lf();
  }
  void siteDone(int completed, int count) {
    if (log.is(1))
      log.date()
        .add("COMPLETED ").add(completed)
        .add(" OF ").add(count).add(" SITES")
        .lf();
  }
  void done(long startTime) {
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