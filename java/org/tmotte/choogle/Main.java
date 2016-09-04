package org.tmotte.choogle;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;
import java.util.stream.Collectors;
import org.tmotte.choogle.pagecrawlnetty.NettyWorldCrawler;
import org.tmotte.choogle.service.LoadTest;
import org.tmotte.common.jettyserver.MyJettyServer;

public class Main {
  public static void main(String[] args) throws Exception  {
    String arg0=args.length==0 ?null :args[0];
    if (args.length==0)
      help();
    for (int i=0; i<args.length; i++) {
      if (args[i].equals("--help") || args[i].startsWith("-h"))
        help();
      else
      if (args[i].equals("--server") || args[i].startsWith("-s"))
        runServer(args);
      else
      if (args[i].equals("--client") || args[i].startsWith("-c"))
        runClient(args);
    }
    help("Expected --server, --client or --help");
  }

  private static void runServer(String[] args) throws Exception {
    boolean db=false;
    int debugLevel=0;
    for (int i=0; i<args.length; i++)
      if (args[i].equals("--server") || args[i].startsWith("-s")){
        //no-op
      }
      else
      if (args[i].equals("--verbose") || args[i].startsWith("-v"))
        debugLevel++;
      else
      if (args[i].equals("--db") || args[i].startsWith("-d"))
        db=true;
      else
      if (args[i].startsWith("-")){
        help("Unrecognized argument: "+args[i]);
        return;
      }

    // The async functionality worked great until we started answering HEAD
    // requests, and then jetty started failing like:
    //   2016-09-04 14:30:28.661:WARN:oejs.HttpChannel:qtp471910020-15: //localhost/630701
    //   java.lang.IllegalStateException: cannot reset buffer on committed response
    //   java.lang.IllegalStateException: s=ASYNC_WOKEN i=false a=EXPIRED
    // So I'm setting the async pool size to -1 to turn it off.
    // Not so good, jetty.
    MyJettyServer.serve(
      new LoadTest(debugLevel, db),
      8080,
      -1
    );
  }
  private static void runClient(String[] args) throws Exception {
    int debugLevel=0;
    long depth=1;
    boolean cacheResults=true;

    int startURLs=0;
    for (int i=0; i<args.length; i++)
      if (args[i].equals("--client") || args[i].startsWith("-c")){
        //no-op
      }
      else
      if (args[i].equals("--depth") || args[i].startsWith("-d"))
        try {
          depth=Long.parseLong(args[++i]);
        } catch (Exception e) {
          help("Not an integer: "+args[i]);
          return;
        }
      else
      if (args[i].equals("--verbose") || args[i].startsWith("-v"))
        debugLevel++;
      else
      if (args[i].equals("--no-cache") || args[i].startsWith("-n"))
        cacheResults=false;
      else
      if (args[i].startsWith("-")){
        help("Unrecognized argument: "+args[i]);
        return;
      }
      else {
        startURLs=i;
        break;
      }

    List<String> urls=java.util.Arrays.asList(args).subList(startURLs, args.length);
    if (urls.size()==0){
      help("Missing list of URLs");
      return;
    }
    System.out.println(String.format(
      "\nCRAWLING URL(S): %s\n",
      urls.stream().collect(Collectors.joining(", ")))
    );
    long s1=System.currentTimeMillis();
    NettyWorldCrawler.crawl(urls, depth, debugLevel, cacheResults);
    long s2=System.currentTimeMillis();
    System.out.println(String.format("Completed in %d ms", s2-s1));
  }


  private static void help() throws Exception {
    help(null);
  }
  private static void help(String error) throws Exception {
    Appendable a=error!=null ?System.err :System.out;
    if (error!=null){
      a.append("\n");
      a.append(error);
      a.append("\n");
    }
    a.append(
      "\n"
    + "  Usage : java org.tmotte.choogle.Main \\\n "
    + "       < --help | --server | --client [--verbose] [--depth <number>] [--no-cache] <urls>> \n"
    + "  Parameters:\n"
    + "    --help:      Print this message\n"
    + "    --server:    Start up server\n"
    + "      --verbose:   Debugging information. Specify more than once for even more debugging. \n"
    + "      --db:        Do things with a database\n"
    + "    --client:    If no url's are given they will be read from stdin\n"
    + "      --verbose:   Debugging information. Specify more than once for even more debugging. \n"
    + "      --depth:     Number of site URL's to traverse before walking away. If <number> is \n"
    + "                   -1, depth is unlimited.\n"
    + "      --no-cache:  Do not track URL's already crawled. This is only useful when\n"
    + "                   using the client as a load-tester.\n"
    + "      urls:        A space-delimited list of url's to start crawling from. \n"
    );
    System.err.flush();
    System.out.flush();
    if (error!=null)
      System.exit(1);
  }

}
