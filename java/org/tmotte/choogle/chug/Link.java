package org.tmotte.choogle.chug;
import java.net.URI;

/** FIXME you don't need this. */
public class Link {
  String url;
  String title;

  public @Override String toString() {
    return url;
  }
  public @Override boolean equals(Object x) {
    if (!(x instanceof Link))
      return false;
    Link lk=(Link)x;
    return lk.url!=null && lk.url.equals(this.url);
  }
  public @Override int hashCode(){
    return url.hashCode();
  }
  public URI getURI(URI base) {
    return getURI(base, url);
  }
  public static URI getURI(String url) {
    return getURI(null, url);
  }
  public static URI getURI(URI base, String url) {
    try {
      if (url.startsWith("https:") || url.startsWith("http:"))
        return new URI(url);
      else
      if (url.startsWith("www."))
        return new URI("http://"+url);
      else
      if (url.startsWith("//"))
        //Crazy, but google does this:
        return new URI("http:"+url);
      else
      if (base==null)
        return null;
      else
        return base.resolve(url);
    } catch (Exception e) {
      return null;
    }
  }
  public static void main(String[] args) throws Exception {
    URI u=new URI("http://www.google.com");
    for (String a: args) {
      u=u.resolve(a);
      System.out.println(u.getScheme()+" "+u.getHost()+" "+u.getRawPath());
    }
  }
}