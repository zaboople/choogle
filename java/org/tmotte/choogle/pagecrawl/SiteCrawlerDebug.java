package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import org.tmotte.common.text.Outlog;

class SiteCrawlerDebug {

  Outlog log;
  String sitename;

  void doHead(URI uri) {
    if (log.is(2))
      log.lf().date().add(sitename).add(" HEAD: ").add(uri).lf();
  }

  void headers(
      URI currentURI,
      int statusCode,
      String contentType,
      String eTag,
      String lastModified,
      boolean closed,
      boolean redirected,
      String locationHeader
    ){
    log.add("  ").add(sitename)
      .add(" RESPONSE")
      .add(" STATUS: ").add(statusCode)
      .add(" CONTENT TYPE: ").add(contentType);
    if (eTag!=null)
      log.add(" ETAG: ").add(eTag);
    if (lastModified !=null)
      log.add(" LAST MODIFIED: ").add(lastModified);
    if (closed)
      log.lf()
        .add("  ").add(sitename).add(" CLOSED");
    if (redirected)
      log.lf()
        .add("  ").add(sitename).add(" REDIRECT: ").add(locationHeader);
    log.lf();
    if (log.is(2)) log.add("  ");
  }

  void pageBody(String s) {
    if (log.is(4))
      log.lf().add(">>>").add(s).add("<<<").lf();
    else
      log.add(".");
  }

  void pageComplete(
      URI currentURI, int count, int pageSize, String title
    ) {
    if (log.is(2)) log.lf().add("  ");
    else log.date();
    log.add(sitename).add(" COMPLETE #").add(count)
      .add(" SIZE: ").add(pageSize / 1024).add("K")
      .add(" URI: ").add(currentURI)
      .add(" TITLE: ").add(title)
      .lf()
      ;
  }

  void linkProcessing(
      int count, int templinks, int scheduled, int elsewhere
    ) {
    if (log.is(2))
      log
        .add("  ").add(sitename)
        .add(" RESPONSE COUNT: ").add(count);
    if (log.is(3))
      log
        .add(" LINKS FOUND: ").add(templinks)
        .add(" SCHEDULED: ").add(scheduled)
        .add(" ELSEWHERE: ").add(elsewhere);
    if (log.is(2))
      log.lf();
  }

  void closing() {
    log.date().add(sitename).add(" CLOSING").lf();
  }

  void redirecting(URI newURI) {
    log.date().add(sitename).add(" REDIRECTING SITE TO ").add(newURI).lf();
  }

  void siteComplete(int count) {
    log.date()
      .add(sitename).add(" ALL LINKS READ, CLOSING, COUNT: ").add(count)
      .lf();
  }
}