package org.tmotte.choogle;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.tmotte.choogle.servenetty.MyServer;
import org.tmotte.choogle.clientnetty.WorldCrawler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public static void main(String[] args) throws Exception  {
    String arg0=args.length==0 ?null :args[0];
    if (args.length==0)
      help();
    else
    if (arg0.equals("--help") || arg0.startsWith("-h"))
      help();
    else
    if (arg0.equals("--server") || arg0.startsWith("-s"))
      //org.tmotte.choogle.servejetty.MyJettyServer.serve();
      MyServer.serve(() -> new org.tmotte.choogle.servenetty.LoadTest());
    else
    if (arg0.equals("--client") || arg0.startsWith("-c")){
      long depth=1;
      int i=1, debugLevel=1;
      while (i < args.length)
        if (args[i].equals("--depth") || args[i].startsWith("-d"))
          try {
            depth=Long.parseLong(args[++i]);
            i++;
          } catch (Exception e) {
            help("Not an integer: "+args[i]);
            return;
          }
        else
        if (args[i].equals("--verbose") || args[i].startsWith("-v")){
          debugLevel++;
          i++;
        }
        else
        if (args[i].startsWith("-")){
          help("Unrecognized argument: "+args[i]);
          return;
        }
        else
          break;
      List<String> urls=java.util.Arrays.asList(args).subList(i, args.length);
      if (urls.size()==0){
        help("Missing depth & list of URLs");
        return;
      }
      System.out.println("Crawling: "+urls.stream().collect(Collectors.joining(", ")));
      WorldCrawler.crawl(urls, depth, debugLevel);
    }
    else
      help("Unexpected: "+arg0);
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
    + "       < --help | --server | --client [--verbose | -v] [--depth | -d] [urls]> \n"
    + "  Parameters:\n"
    + "    --client: If no url's are given they will be read from stdin\n"
    + "      --verbose: Debugging information. Specify more than once for even more debugging. \n"
    + "      --depth:   Number of site URL's to traverse before walking away. \n"
    );
    System.err.flush();
    System.out.flush();
    if (error!=null)
      System.exit(1);
  }

}
