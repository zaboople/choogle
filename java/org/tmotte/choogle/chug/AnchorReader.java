package org.tmotte.choogle.chug;
import java.util.Collection;
import org.tmotte.common.text.HTMLParserListener;
import org.tmotte.common.text.HTMLParser;
import org.tmotte.common.text.StringMatcher;
import org.tmotte.common.text.StringMatcherChars;

/** This is self-resetting; the tagNameStart() function automatically reinitializes state. */
public class AnchorReader implements HTMLParserListener {

  /////////////////////////////////////
  // STATIC VARIABLES AND FUNCTIONS: //
  /////////////////////////////////////

  private static StringMatcherChars
    smcHref=new StringMatcherChars("href"),
    smcAnchor=new StringMatcherChars("a");

  /** A shortcut */
  public static HTMLParser withParser(Collection<Link> values) {
    return new HTMLParser(new AnchorReader(values));
  }

  ////////////////////
  // PRIVATE STATE: //
  ////////////////////

  private StringBuilder bufURL=new StringBuilder();
  private StringMatcher
    hrefMatcher  =new StringMatcher(smcHref),
    anchorMatcher=new StringMatcher(smcAnchor);
  private boolean inAnchor=false;

  ///////////////////////////
  // INPUTS + CONSTRUCTOR: //
  ///////////////////////////

  private Collection<Link> values;
  public AnchorReader(Collection<Link> values) {
    this.values=values;
  }

  ////////////////////////////
  // INTERFACE FULFILLMENT: //
  ////////////////////////////

  public boolean tagNameStart(){
    bufURL.setLength(0);
    hrefMatcher.reset();
    anchorMatcher.reset();
    return true;
  }
  public boolean tagIsClosing(){
    return false;
  }
  public boolean tagName(char c){
    return anchorMatcher.soFarSoGood(c);
  }
  public boolean tagNameComplete(){
    return anchorMatcher.success();
  }
  public boolean tagComplete(boolean selfClosing){
    return false;
  }

  public boolean attrNameStart(){
    if (!hrefMatcher.success())  hrefMatcher.reset();
    return true;
  }
  public boolean attrName(char c){
    return hrefMatcher.soFarSoGood(c);
  }
  public boolean attrNameComplete(){
    return hrefMatcher.success();
  }

  public boolean attrValueStart(){
    return true;
  }
  public boolean attrValue(char c){
    if (c!='#') {
      bufURL.append(c);
      return true;
    }
    return false;
  }
  public boolean attrValueComplete(){
    Link link=new Link();
    link.url=bufURL.toString();
    bufURL.setLength(0);
    values.add(link);
    return false;
  }

  public boolean cdataStart(){return false;}
  public boolean cdata(char c){return false;}
  public boolean cdataComplete(){return false;}
  public boolean commentStart(){return false;}
  public boolean comment(char c){return false;}
  public boolean commentComplete(){return false;}
  public boolean text(char c){return false;}


  //////////////
  // TESTING: //
  //////////////

  public static void main(String[] args) throws Exception {
    java.io.BufferedReader br=new java.io.BufferedReader(
      new java.io.InputStreamReader(System.in)
    );
    Collection<Link> values=new java.util.HashSet<>(1000);
    HTMLParser bp=new HTMLParser(
      new AnchorReader(values)
    );
    String s=null;
    while ((s=br.readLine())!=null)
      bp.add(s);
    for (Link v : values)
      System.out.println(v);
  }
}
