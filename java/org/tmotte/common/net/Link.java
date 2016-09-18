package org.tmotte.common.net;
import java.net.URI;

/**
 * Just a handy way to resolve funky strings into http/https URI/URL's.
 */
public class Link {

  /**
   * Converts the given String to a url.
   * @param url A string that hopefully starts with http:// or https://
   *    or even // but we'll do what we can.
   */
  public static URI getURI(String url) throws Exception {
    return getURI(null, url);
  }
  /**
   * Attempts to resolve a possibly relative URL in the context of
   * of a web page that provided it; it's okay if it's an absolute
   * url, however.
   *
   * @param base The web page we got this url from.
   * @param url The partial or complete or relative url we want resolved.
   */
  public static URI getURI(URI base, String url) throws Exception {
    try {
      if (url.startsWith("https:") || url.startsWith("http:"))
        return cleanup(new URI(url));
      else
      if (url.startsWith("www."))
        return cleanup(new URI("http://"+url));
      else
      if (url.startsWith("//"))
        //Crazy, but google does this:
        return cleanup(new URI("http:"+url));
      else
      if (url.startsWith("javascript:") || url.startsWith("mailto:"))
        return null;
      else
      if (base==null && url.contains("."))
        return cleanup(new URI("http://"+url));
      else
      if (base==null)
        return null;
      else
        return cleanup(base.resolve(url));
    } catch (Exception e) {
      return null;
    }
  }

  private static URI cleanup(URI uri) throws Exception {
    String host = uri.getHost();
    int port = uri.getPort();
    String path = uri.getPath();
    String scheme = uri.getScheme();
    String query = uri.getQuery();

    // Yes we want query to be null
    if (host!=null && port > -1 && path != null && scheme!=null && query==null)
      return uri;
    if (host==null)
      return null;

    if (port == -1) {
      if (scheme==null) scheme="http";
      if ("http".equalsIgnoreCase(scheme))
        port = 80;
      else
      if ("https".equalsIgnoreCase(scheme))
        port = 443;
      else
        return null;
    }
    if (scheme==null) {
      if (port==443) scheme="https";
      else scheme="http";
    }
    if (path==null || path.equals(""))
      path="/";
    return new URI(scheme, null, host, port, path, null, null);
  }

  /** Just for testing things. */
  public static void main(String[] args) throws Exception {
    URI u=null;
    for (String a: args) {
      System.out.print(a+" --> ");
      u=Link.getURI(u, a);
      if (u==null)
        System.out.println("NULL");
      else
        System.out.println(u+" ---> ["+u.getScheme()+"]["+u.getHost()+"]["+u.getPort()+"]["+u.getRawPath()+"]");
    }
  }
}