package org.tmotte.choogle.chug;
import java.util.Collection;

// WHAT we need is an EndTagFinder that knows how to
// go thru <blah xxx='hello' yy="wefwefwef"> without getting
// tripped on an ">" in the attributes
public class AnchorReader{

  private static StringMatcherChars
    smcHref=new StringMatcherChars("href"),
    smcScript=new StringMatcherChars("script");

  private Collection<Link> values;
  private StringBuilder
    bufURL=new StringBuilder(),
    bufText=new StringBuilder();



  public AnchorReader(Collection<Link> values) {
    this.values=values;
  }
  public void add(String s) {
    int index=0;
    int len=s.length();
    int scanMode=0;
    int textMode=0;
    while (index < len) {
      char c=s.charAt(index);
      scanMode=scanHref(c, scanMode);
      textMode=scanText(c, textMode);
      index++;
    }
  }


  private StringMatcher
    hrefMatcher=new StringMatcher(smcHref, 0, 4, 8)
    ,
    scriptMatcher=new StringMatcher(smcScript, 0, 4, 8)
    ;

  /**
    Some sites put everything in javascript strings, so we allow "\"" in #9
    because they'll have
      <a href=\"/blah/blah/blah\">

    Consider using a stack of operations!
  */
  private int scanHref(char c, int i){
    switch (i){
      case 0: return c=='<' ?1 :0;
      case 1: return c=='a' || c=='A' ?2 :0;
      case 2:
        return c==' ' ?4 :0;
      case 3:
        if (c=='>') return 0;
        if (c==' ' || c=='\t') return 4;
        return 3;
      case 4: return hrefMatcher.match(c);
      case 8:
        if (c=='=') return 9;
        if (c==' ' || c=='\t') return 8;
        if (c=='>') return 0;
        return 3;
      case 9:
        if (c=='\'') return 10;
        if (c=='"') return 12;
        if (isWhite(c) || c=='\\') return 9;
        if (c=='>') return 0;
        return 3;

      //Scanning for end of title; in order
      //single quote
      //single quote but after #
      //dbl quote
      //dbl quote but after #
      case 10:
        return checkURL(c, '\'', 10, 11, false);
      case 11:
        return checkURL(c, '\'', 11, 11, true);
      case 12:
        return checkURL(c, '"', 12, 13, false);
      case 13:
        return checkURL(c, '"', 13, 13, true);
      default:
        throw new RuntimeException("Unexpected: "+i);
    }
  };
  //WRong: <script> as well as < in attributes
  private int scanText(char c, int i) {
    switch(i) {
      case 0:
        if (isWhite(c)) return 0;
        if (c=='<') return 10;
        bufText.append(c);
        return 1;

      case 1:
        if (c=='<') {
          bufText.append("\r\n");
          return 10;
        }
        bufText.append(c);
        return 1;

      case 10:
        // Wait on tag to end:
        //return scriptMatcher
        if (c=='>') return 10;
        return 10;

      case 20:
        //You hit a script tag, poor you

      default:
        throw new RuntimeException("Unexpected: "+i);
    }
  }

  private int checkURL(char c, char compare, int self, int onPound, boolean ignore) {
    //Double quote:
    if (c==compare){
      if (bufURL.length()>0){
        Link link=new Link();
        link.url=bufURL.toString();
        values.add(link);
        bufURL.setLength(0);
      }
      return 0;
    }
    if (c=='#')  return onPound;
    if (!ignore) bufURL.append(c);
    return self;
  }


  private int checkChar(char c, char opt1, char opt2, int next){
    if (c=='>') return 0;
    if (isWhite(c)) return 4;
    if (c==opt1 || c==opt2) return next;
    return 3;
  }
  private static boolean isWhite(char c) {
    return c==' ' || c=='\t' || c=='\n' || c=='\r';
  }


  public static void main(String[] args) throws Exception {
    java.io.BufferedReader br=new java.io.BufferedReader(
      new java.io.InputStreamReader(System.in)
    );
    Collection<Link> values=new java.util.HashSet<>(1000);
    AnchorReader ar=new AnchorReader(values);
    String s=null;
    while ((s=br.readLine())!=null)
      ar.add(s);
    for (Link v : values)
      System.out.println(v);
    //System.out.println(ar.bufText);
  }
}
