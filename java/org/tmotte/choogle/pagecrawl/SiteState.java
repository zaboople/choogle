package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Used by SiteCrawler to track URL's and how close we are to completing the crawl.
 * Should provide enough abstraction that multiple SiteCrawlers can share an instance
 * with implicit thread safety.
 */
class SiteState {
  private final String siteName;
  private final MyDB myDB;
  private final int limit;
  private final boolean cacheResults;

  private final AtomicInteger maxConnIndex=new AtomicInteger(0);
  private final AtomicInteger count=new AtomicInteger(0);
  private final AtomicInteger connsAllowedRemaining;
  private final Queue<URI> scheduled;
  private final Set<String> scheduledSet;
  private final Set<URI>    elsewhere;

  SiteState(String siteName, MyDB db, long limit, int connsPer, boolean cacheResults) throws Exception {
    this.siteName=siteName;
    this.myDB=db;
    this.limit=(int)limit;
    this.connsAllowedRemaining=new AtomicInteger(connsPer);
    int bufferSize=Math.min(1000, this.limit);
    if (myDB!=null) {
      scheduled=null;
      scheduledSet=null;
      elsewhere=null;
      myDB.establish(siteName);
    }
    else {
      scheduled=new ConcurrentLinkedQueue<>();
      scheduledSet=ConcurrentHashMap.newKeySet(bufferSize);
      elsewhere   =ConcurrentHashMap.newKeySet(bufferSize);
    }
    this.cacheResults=cacheResults;
  }


  // Simple gets:
  long getLimit() {return limit;}
  int getCount()  {return count.get();}
  int getElsewhereSize() {return elsewhere.size();}
  int getNextConnIndex() {return maxConnIndex.incrementAndGet();}
  boolean moreConnsAllowed() {return connsAllowedRemaining.getAndDecrement()>1;}
  void moreConnsFailed() {connsAllowedRemaining.incrementAndGet();}

  // Simple adds:
  void addCount() {count.incrementAndGet();}


  ////////////////////
  // DATABASE TIME: //
  ////////////////////


  final int getScheduledSize() throws Exception {
    return myDB==null ?scheduled.size() :myDB.getScheduledSize(siteName);
  }
  final boolean hasScheduled() throws Exception {
    return getScheduledSize()>0;
  }

  // Various ways of asking if we've crawled enough yet:
  boolean notEnoughURLsForLimit() throws Exception {
    if (!cacheResults)
      return getScheduledSize() < 500;
    else
      return limit == -1 ||
        getCount() + getScheduledSize() < limit;
  }
  boolean lessThanLimit() {
    return limit == -1
      || getCount() < limit;
  }
  boolean moreToCrawl() throws Exception {
    return lessThanLimit() && hasScheduled();
  }


  // The more sophisticated path management stuff:

  /** Add the starting path so we know not to crawl it again. */
  void addInitialPath(URI uri) throws Exception {
    if (myDB==null)
      scheduledSet.add(uri.getRawPath());
    else {
      myDB.addURI(siteName, uri.toString());
      myDB.complete(siteName, uri.toString());
    }
  }
  /** Add a found URI if we haven't scheduled it already. */
  void addURIs(Stream<URI> maybes) throws Exception {
    if (myDB==null)
      maybes.forEach(maybe->{
        String raw=maybe.getRawPath();
        if (!scheduledSet.contains(raw)) {
          scheduled.add(maybe);
          if (cacheResults)
            scheduledSet.add(raw);
        }
      });
    else
      myDB.addURIs(siteName, maybes.map(m -> m.toString()));
  }
  void addElsewhereURI(URI maybe) throws Exception {
    if (myDB==null)
      elsewhere.add(maybe);
    else
      myDB.addURI(
        new URI(maybe.getScheme(), null, maybe.getHost(), maybe.getPort(), null, null, null).toString(),
        maybe.toString()
      );
  }
  /** Call to the next scheduled URL for the current site. */
  URI getNextForConnection() throws Exception {
    if (myDB==null)
      return getScheduledSize() > 0
        ?scheduled.poll()
        :null;
    else {
      String raw=myDB.getNextURI(siteName);
      if (raw==null) return null;
      try {
        return new URI(raw);
      } catch (Exception e) {
        throw new RuntimeException("Could not parse: "+raw);
      }
    }
  }
}
