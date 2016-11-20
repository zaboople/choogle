package org.tmotte.choogle.pagecrawl;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.tmotte.common.text.Outlog;

/**
 * Used by SiteCrawler to track URL's and how close we are to completing the crawl.
 * Should provide enough abstraction that multiple SiteCrawlers can share an instance
 * with implicit thread safety.
 */
class SiteState {
  private final Outlog log;
  private final String siteName;
  private final MyDB myDB;
  private final int limit;
  private final boolean cacheResults;

  private final AtomicInteger maxConnIndex=new AtomicInteger(0);
  private final AtomicInteger count=new AtomicInteger(0);
  private final AtomicLong scheduledCount=new AtomicLong(0);
  private final AtomicInteger connsAllowedRemaining;
  private final Queue<URI> scheduled;
  private final Set<String> scheduledSet;
  private final Set<URI>    elsewhere;


  SiteState(Outlog log, String siteName, MyDB db, long limit, int connsPer, boolean cacheResults) throws Exception {
    this.log=log;
    this.siteName=siteName;
    this.myDB=db;
    this.limit=(int)limit;
    this.connsAllowedRemaining=new AtomicInteger(connsPer);
    int bufferSize=Math.min(1000, this.limit);
    scheduled=new ConcurrentLinkedQueue<>();
    scheduledSet=myDB!=null ?null :ConcurrentHashMap.newKeySet(bufferSize);
    elsewhere   =myDB!=null ?null :ConcurrentHashMap.newKeySet(bufferSize);
    this.cacheResults=cacheResults;
    if (myDB!=null){
      myDB.establish(siteName);
      scheduledCount.set(myDB.getScheduledSize(siteName));
    }
  }


  // Simple gets:
  long getLimit() {return limit;}
  int getCount()  {return count.get();}
  int getNextConnIndex() {return maxConnIndex.incrementAndGet();}
  boolean moreConnsAllowed() {return connsAllowedRemaining.getAndDecrement()>1;}
  void moreConnsFailed() {connsAllowedRemaining.incrementAndGet();}

  // Simple adds:
  void addCount() {count.incrementAndGet();}


  ////////////////////
  // DATABASE TIME: //
  ////////////////////


  final int getScheduledSize() throws Exception {
    return (int) scheduledCount.get();
  }
  final int getElsewhereSize() throws Exception {
    return myDB==null ?elsewhere.size() :myDB.getElsewhereSize(siteName);
  }

  final boolean hasScheduled() throws Exception {
    return scheduledCount.get() > 0;
  }

  // Various ways of asking if we've crawled enough yet:
  boolean notEnoughURLsForLimit() throws Exception {
    if (!cacheResults)
      return scheduledCount.get() < 500;
    else
      return limit == -1 ||
        getCount() + scheduledCount.get() < limit;
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
    else
      myDB.addURI(siteName, uri.toString(), true);
  }
  /** Add a found URI if we haven't scheduled it already. */
  void addURIs(Stream<URI> maybes) throws Exception {
    if (myDB==null) {
      maybes = maybes.filter(maybe->{
        String raw=maybe.getRawPath();
        if (!scheduledSet.contains(raw)) {
          scheduled.add(maybe);
          if (cacheResults) scheduledSet.add(raw);
          return true;
        }
        return false;
      });
      scheduledCount.addAndGet(maybes.count());
    }
    else
      scheduledCount.addAndGet(
        myDB.addURIs(siteName, maybes.map(m -> m.toString()))
      );
  }
  void addElsewhereURI(URI maybe) throws Exception {
    if (myDB==null)
      elsewhere.add(maybe);
    else
      myDB.addURI(
        new URI(maybe.getScheme(), null, maybe.getHost(), maybe.getPort(), null, null, null).toString(),
        maybe.toString(),
        false
      );
  }
  /** Call to the next scheduled URL for the current site. */
  URI getNextForConnection() throws Exception {
    if (scheduled.isEmpty() && myDB!=null) {
      myDB.getNextURIs(
        siteName, 256,
        raw -> {
          try {
            scheduled.add(new URI(raw));
          } catch (Exception e) {
            throw new RuntimeException("Could not parse: "+raw);
          }
        }
      );
    }
    URI u=scheduled.poll();
    if (u!=null) scheduledCount.decrementAndGet();
    return u;
  }
}
