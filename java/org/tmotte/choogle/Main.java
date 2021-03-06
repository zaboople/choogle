package org.tmotte.choogle;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.tmotte.choogle.pagecrawl.WorldCrawler;
import org.tmotte.choogle.pagecrawlnetty.NettyConnectionFactory;
import org.tmotte.choogle.service.LoadTest;
import org.tmotte.common.jettyserver.MyJettyServer;
import org.tmotte.common.text.Outlog;

/**
 * Boots either a choogle crawler or a webserver that we use for testing.
 */
public class Main {
  /** Boots the application. Pass a --help or -h to get detailed usage information. */
  public static void main(String[] args) throws Exception {
    boolean handled=false;
    String arg0=args.length==0 ?null :args[0];
    for (int i=0; i<args.length && !handled; i++) {
      if (args[i].equals("--help") || args[i].startsWith("-h"))
        handled=help();
      else
      if (args[i].equals("--server") || args[i].startsWith("-s"))
        handled=runServer(args);
      else
      if (args[i].equals("--client") || args[i].startsWith("-cl"))
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
    int connsPerSite=1;
    boolean db=false, dbreset=true;
    List<String> urls=new ArrayList<>();

    for (int i=0; i<args.length; i++)
      if (args[i].equals("--client") || args[i].startsWith("-cl")){
        //no-op
      }
      else
      if (args[i].startsWith("--depth") || args[i].startsWith("-de")) {
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
      if (args[i].startsWith("--conn") || args[i].startsWith("-co")){
        int idnex=args[i].indexOf("=");
        String toParse=idnex > -1
          ?args[i].substring(idnex+1, args[i].length())
          :args[++i];
        try {
          connsPerSite=Integer.parseInt(toParse);
        } catch (Exception e) {
          return help("Not an integer: "+toParse);
        }
      }
      else
      if (args[i].equals("--reset") || args[i].startsWith("-r"))
        dbreset=true;
      else
      if (args[i].equals("--verbose") || args[i].startsWith("-v"))
        debugLevel++;
      else
      if (args[i].equals("--no-cache") || args[i].startsWith("-n"))
        cacheResults=false;
      else
      if (args[i].equals("--db") || args[i].startsWith("-db"))
        db=true;
      else
      if (args[i].startsWith("-"))
        return help("Unrecognized argument: "+args[i]);
      else
        urls.add(args[i]);

    Outlog log=new Outlog().setLevel(debugLevel);
    if (urls.size()==0)
      return help("Missing list of URLs");
    System.out.println(String.format(
      "\nCRAWLING URL(S): %s\n",
      urls.stream().collect(Collectors.joining(", ")))
    );
    NettyConnectionFactory factory=new NettyConnectionFactory(log);
    WorldCrawler.crawl(
      log, factory, cacheResults,
      ()-> {
        try {factory.finish();}
        catch (Exception e) {e.printStackTrace();}
      },
      db, dbreset,
      urls, depth, connsPerSite
    );
    return true;
  }


  private static boolean help() throws Exception {
    return help(null);
  }
  private static boolean help(String error) throws Exception {
    Appendable a=error!=null ?System.err :System.out;
    if (error!=null)
      a.append("\n")
        .append("ERROR: ")
        .append(error)
        .append("\n");
    a.append(
      "\n"
    + "  Usage : java org.tmotte.choogle.Main \\\n "
    + "       < --help | --server | --client [--verbose] [--depth=<#>] [--no-cache] [--conns-per-site=<#>] <urls>> \n"
    + "  Parameters:\n"
    + "    --help:      Print this message\n"
    + "    --server:    Start up server\n"
    + "      --verbose:        Debugging information. Specify more than once for even more debugging. \n"
    + "      --db:             Do things with a database.\n"
    + "    --client:    If no url's are given they will be read from stdin\n"
    + "      --verbose:        Debugging information. Specify more than once for even more debugging. \n"
    + "      --depth:          Number of site URL's to traverse before walking away. If <#> is \n"
    + "                        -1, depth is unlimited. Default is 1. \n"
    + "      --conns-per-site: Number of connections per site. Default is 1. More is usually not helpful.\n"
    + "      --no-cache:       Do not track URL's already crawled. This is only useful when\n"
    + "                        using the client as a load-tester.\n"
    + "      --db:             Use a postgres database to track URL's for greater scale. Requires\n"
    + "                        environment variables for JDBC_URL, JDBC_USER & JDBC_PASS\n"
    + "        --reset:        Clear previous tries from the database.\n"
    + "    <urls>:             A space-delimited list of url's to start crawling from. \n"
    );
    System.err.flush();
    System.out.flush();
    if (error!=null)
      System.exit(1);
    return true;
  }

}
