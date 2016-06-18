package org.tmotte.choogle;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.tmotte.choogle.servenetty.MyServer;
import org.tmotte.choogle.clientnetty.ChClient;
import org.tmotte.choogle.clientnetty.PageCrawler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception  {
    try {
      String arg0=args.length==0 ?null :args[0];
      if (args.length==0)
        help();
      else
      if (arg0.equals("--help") || arg0.startsWith("-h"))
        help();
      else
      if (arg0.equals("--server") || arg0.startsWith("-s"))
        //org.tmotte.choogle.servejetty.MyJettyServer.serve();
        MyServer.serve();
      else
      if (arg0.equals("--client") || arg0.startsWith("-c")){
        List<String> urls=java.util.Arrays.asList(args).subList(1, args.length);
        System.out.println("Starting up, gonna read: "+urls);
        for (String u:urls)
          new PageCrawler(u).read(urls);
      }
      else
        help();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
  private static void help() {
    System.out.println(
      "\n\nUsage : java org.tmotte.choogle.Main <--server | --client [urls] | --help> \n"
    + "   --client: If no url's are given they will be read from stdin"
    );
  }

}
