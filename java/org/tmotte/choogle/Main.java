package org.tmotte.choogle;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;
import java.util.stream.Collectors;
import org.tmotte.choogle.pagecrawl.WorldCrawler;
import org.tmotte.choogle.pagecrawlnetty.NettyConnectionFactory;
import org.tmotte.choogle.service.LoadTest;
import org.tmotte.common.jettyserver.MyJettyServer;
import org.tmotte.common.text.Outlog;

public class Main {
  public static void main(String[] args) throws Exception {
    boolean handled=false;
    String arg0=args.length==0 ?null :args[0];
    if (args.length==0)
      help();
    for (int i=0; i<args.length; i++) {
      if (args[i].equals("--help") || args[i].startsWith("-h"))
        handled=help();
      else
      if (args[i].equals("--server") || args[i].startsWith("-s"))
        handled=runServer(args);
      else
      if (args[i].equals("--client") || args[i].startsWith("-c"))
        handled=runClient(args);
    }
    if (!handled)
      help("Expected --server, --client or --help");
  }

  private static boolean runServer(String[] args) throws Exception {
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
      if (args[i].startsWith("-"))
        return help("Unrecognized argument: "+args[i]);

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
    return true;
  }
  private static boolean runClient(String[] args) throws Exception {
    int debugLevel=0;
    long depth=1;
    boolean cacheResults=true;

    int startURLs=0;
    for (int i=0; i<args.length; i++)
      if (args[i].equals("--client") || args[i].startsWith("-c")){
        //no-op
      }
      else
      if (args[i].startsWith("--depth") || args[i].startsWith("-d")) {
          int idnex=args[i].indexOf("=");
          String toParse=idnex > -1
            ?args[i].substring(idnex+1, args[i].length())
            :args[++i];
        try {
          depth=Long.parseLong(toParse);
        } catch (Exception e) {
          return help("Not an integer: "+toParse);
        }
      }
      else
      if (args[i].equals("--verbose") || args[i].startsWith("-v"))
        debugLevel++;
      else
      if (args[i].equals("--no-cache") || args[i].startsWith("-n"))
        cacheResults=false;
      else
      if (args[i].startsWith("-"))
        return help("Unrecognized argument: "+args[i]);
      else {
        startURLs=i;
        break;
      }

    Outlog log=new Outlog().setLevel(debugLevel);
    List<String> urls=java.util.Arrays.asList(args).subList(startURLs, args.length);
    if (urls.size()==0)
      return help("Missing list of URLs");
    System.out.println(String.format(
      "\nCRAWLING URL(S): %s\n",
      urls.stream().collect(Collectors.joining(", ")))
    );
    long s1=System.currentTimeMillis();
    WorldCrawler.crawl(
      new NettyConnectionFactory(log), urls, depth, log, cacheResults
    );
    return true;
  }


  private static boolean help() throws Exception {
    return help(null);
  }
  private static boolean help(String error) throws Exception {
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
    return false;
  }

}
