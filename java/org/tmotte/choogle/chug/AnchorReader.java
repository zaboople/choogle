package org.tmotte.choogle.chug;
import java.util.Collection;
import org.tmotte.common.text.HTMLParserListener;
import org.tmotte.common.text.HTMLParser;
import org.tmotte.common.text.StringMatcher;
import org.tmotte.common.text.StringMatcherStatic;
import org.tmotte.common.text.StringMatcherChars;

/** FIXME **NOT** SELF-RESETTING */
public final class AnchorReader {

  /////////////////////////////////////
  // STATIC VARIABLES AND FUNCTIONS: //
  /////////////////////////////////////

  private static StringMatcherStatic
    matchAnchor=new StringMatcherStatic("a")
    ,
    matchBody  =new StringMatcherStatic("body")
    ,
    matchTitle =new StringMatcherStatic("title")
    ,
    matchScript=new StringMatcherStatic("script")
    ,
    matchStyle =new StringMatcherStatic("style")
    ;
  private static StringMatcherChars
    smcHref  =new StringMatcherChars("href");
  private static short
    BEFORE_TITLE=2,
    IN_TITLE=3,
    BEFORE_BODY=4,
    IN_BODY=5,
    IN_ANCHOR=6,
    IN_BODY_GARBAGE_SCRIPT=7,
    IN_BODY_GARBAGE_STYLE=8,
    AFTER_BODY=9;


  ////////////////////
  // PRIVATE STATE: //
  ////////////////////

  private StringBuilder
    bufURL=new StringBuilder(),
    bufTitle=new StringBuilder();
  private HTMLParser parser;


  ///////////////////////////
  // INPUTS + CONSTRUCTOR: //
  ///////////////////////////

  private Collection<Link> values;
  public AnchorReader(Collection<Link> values) {
    this.values=values;
    parser=new HTMLParser(new MyListener());
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public void add(String s) {
    parser.add(s);
  }
  public void reset() {
    parser.reset();
  }

  ////////////////////////////
  // INTERFACE FULFILLMENT: //
  ////////////////////////////

  private class MyListener implements HTMLParserListener {
    private boolean textWhite=false;
    private short state=BEFORE_TITLE;
    private StringMatcher matchHref    =new StringMatcher(smcHref);

    public void reset() {
      state=BEFORE_TITLE;
      textWhite=false;
    }

    public boolean tagNameComplete(boolean closingTag, CharSequence cs){
      if (state==BEFORE_TITLE && matchTitle.matches(cs)){
        state=closingTag
          ?BEFORE_BODY
          :IN_TITLE;
        return false;
      }
      if (matchAnchor.matches(cs)) {
        if (closingTag) return false;
        bufURL.setLength(0);
        matchHref.reset();
        state=IN_ANCHOR;
        return true;
      }
      if (matchBody.matches(cs)) {
        state=closingTag ?AFTER_BODY :IN_BODY;
        return !closingTag;
      }
      if (matchScript.matches(cs)) {
        state=closingTag ?IN_BODY :IN_BODY_GARBAGE_SCRIPT;
        return closingTag;
      }
      if (matchStyle.matches(cs)) {
        state=closingTag ?IN_BODY :IN_BODY_GARBAGE_STYLE;
        return closingTag;
      }
      return false;
    }
    public boolean tagComplete(boolean selfClosing){
      if (state==IN_ANCHOR)
        state=IN_BODY;
      if (state==IN_BODY_GARBAGE_SCRIPT && selfClosing)
        state=IN_BODY;
      if (state==IN_BODY_GARBAGE_STYLE && selfClosing)
        state=IN_BODY;
      return state==IN_BODY;
    }

    public boolean attrNameStart(){
      if (state==IN_ANCHOR) {
        if (!matchHref.success())  matchHref.reset();
        return true;
      }
      return false;
    }
    public boolean attrName(char c){
      return matchHref.soFarSoGood(c);
    }
    public boolean attrNameComplete(){
      return matchHref.success();
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
    public boolean text(char c, boolean inScript){
      if (state==IN_BODY) {
        boolean thisWhite=c==' ' || c=='\t';
        if (textWhite && thisWhite){}
        else {
          System.out.print(c);
          textWhite=thisWhite;
        }
        return true;
      }
      return false;
    }

    public boolean cdataStart(){return false;}
    public boolean cdata(char c){return false;}
    public boolean cdataComplete(){return false;}
    public boolean commentStart(){return false;}
    public boolean comment(char c){return false;}
    public boolean commentComplete(){return false;}
  }


  //////////////
  // TESTING: //
  //////////////

  public static void main(String[] args) throws Exception {
    java.io.BufferedReader br=new java.io.BufferedReader(
      new java.io.InputStreamReader(System.in)
    );
    Collection<Link> values=new java.util.HashSet<>(1000);
    AnchorReader bp=new AnchorReader(values);
    String s=null;
    while ((s=br.readLine())!=null)
      bp.add(s);
    System.out.println();
    for (Link v : values)   System.out.println(v);
  }
}
