package org.tmotte.choogle.pagecrawlnetty;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.tmotte.choogle.pagecrawl.SiteConnection;
import org.tmotte.choogle.pagecrawl.SiteConnectionFactory;
import org.tmotte.choogle.pagecrawl.SiteReader;
import org.tmotte.common.text.Outlog;

/** A Netty implementation of SiteConnectionFactory, and currently the only implementation. */
public final class NettyConnectionFactory implements SiteConnectionFactory  {

  private final EventLoopGroup elGroup=new NioEventLoopGroup();
  private final Outlog log;

  public NettyConnectionFactory(Outlog log) throws Exception{
    this.log=log;
  }
  public @Override SiteConnection get(SiteReader sc, URI uri) throws Exception {
    return new NettySiteConnection(elGroup, sc, log);
  }
  /** Shuts down netty's EventLoopGroup. */
  public @Override void finish() throws Exception {
    elGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
  }


}
