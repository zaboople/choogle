package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Used by SiteCrawler to track URL's and how close we are to completing the crawl.
 * Should provide enough abstraction that multiple SiteCrawlers can share an instance
 * with implicit thread safety.
 */
class SiteState {
  private final int limit;
  private final boolean cacheResults;

  private final AtomicInteger maxIndex=new AtomicInteger(0);
  private final AtomicInteger count=new AtomicInteger(0);
  private final AtomicInteger connsAllowedRemaining;
  private final Queue<URI> scheduled;
  private final Set<String> scheduledSet;
  private final Set<URI>    elsewhere;

  SiteState(long limit, int connsPer, boolean cacheResults) {
    this.limit=(int)limit;
    this.connsAllowedRemaining=new AtomicInteger(connsPer);
    scheduled=new ConcurrentLinkedQueue<URI>();
    scheduledSet=ConcurrentHashMap.newKeySet(this.limit);
    elsewhere   =ConcurrentHashMap.newKeySet(this.limit);
    this.cacheResults=cacheResults;
  }


  // Simple gets:
  long getLimit() {return limit;}
  int getCount()  {return count.get();}
  int getScheduledSize() {return scheduled.size();}
  int getElsewhereSize() {return elsewhere.size();}
  int getNextIndex() {return maxIndex.incrementAndGet();}
  boolean moreConnsAllowed() {return connsAllowedRemaining.getAndDecrement()>1;}
  void moreConnsFailed() {connsAllowedRemaining.incrementAndGet();}

  // Simple adds:
  void addCount() {count.incrementAndGet();}
  void addElsewhere(URI maybe) {elsewhere.add(maybe);}


  // Various ways of asking if we've crawled enough yet:
  boolean notEnoughURLsForLimit(){
    if (!cacheResults)
      return scheduled.size() < 500;
    else
      return limit == -1 ||
        getCount() + scheduled.size() < limit;
  }
  boolean lessThanLimit() {
    return limit == -1
      || getCount() < limit;
  }
  boolean moreToCrawl() {
    return lessThanLimit() && scheduled.size()>0;
  }


  // The more sophisticated path management stuff:

  /** Add the starting path so we know not to crawl it again. */
  void addInitialPath(String path) {
    scheduledSet.add(path);
  }
  /** Add a found URI if we haven't scheduled it already. */
  void addPath(URI maybe) {
    String raw = maybe.getRawPath();
    if (!scheduledSet.contains(raw)) {
      scheduled.add(maybe);
      if (cacheResults)
        scheduledSet.add(raw);
    }
  }
  /** Call to the next scheduled URL for the current site. */
  URI getNextForConnection() {
    return scheduled.size() > 0
      ?scheduled.poll()
      :null;
  }
}
