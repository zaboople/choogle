package org.tmotte.choogle.chug;
import java.util.Collection;
import org.tmotte.common.text.HTMLParserListener;
import org.tmotte.common.text.HTMLParser;
import org.tmotte.common.text.StringMatcher;
import org.tmotte.common.text.StringMatcherChars;

/** FIXME **NOT** SELF-RESETTING */
public final class AnchorReader {

  /////////////////////////////////////
  // STATIC VARIABLES AND FUNCTIONS: //
  /////////////////////////////////////

  private static StringMatcherChars
    smcAnchor =new StringMatcherChars("a")
    ,smcBody  =new StringMatcherChars("body")
    ,smcHref  =new StringMatcherChars("href")
    ,smcScript=new StringMatcherChars("script")
    ,smcStyle =new StringMatcherChars("style")
    ,smcTitle =new StringMatcherChars("title")
    ;
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
    private StringMatcher
       matchAnchor  =new StringMatcher(smcAnchor)//FIXME most of these can be StringMatcherStatic
      ,matchBody    =new StringMatcher(smcBody)
      ,matchHref    =new StringMatcher(smcHref)
      ,scriptMatcher=new StringMatcher(smcScript)
      ,styleMatcher =new StringMatcher(smcStyle)
      ,titleMatcher =new StringMatcher(smcTitle)
      ;

    public void reset() {
      state=BEFORE_TITLE;
      textWhite=false;
    }


    private boolean tagName(char c){ //FIXME match the damn string not the char

      // Note that these all start with a different character,
      // so we can ignore the others if one works.
      if (matchAnchor.soFarSoGood(c))
        return true;
      if (matchBody.soFarSoGood(c))
        return true;
      if (state==BEFORE_TITLE && titleMatcher.soFarSoGood(c))
        return true;

      // OH wait we have two things that start with S:
      if (state==IN_BODY) {
        scriptMatcher.add(c);
        styleMatcher.add(c);
        return scriptMatcher.soFarSoGood() || styleMatcher.soFarSoGood();
      }

      return false;
    }
    public boolean tagNameComplete(boolean closingTag, CharSequence cs){
      if (state==BEFORE_TITLE)
        titleMatcher.reset();
      matchAnchor.reset();
      matchBody.reset();
      scriptMatcher.reset();
      styleMatcher.reset();
      for (int i=0; i<cs.length(); i++)
        if (!tagName(cs.charAt(i)))
          return false;
      if (titleMatcher.success()) {
        titleMatcher.reset();
        state=closingTag
          ?BEFORE_BODY
          :IN_TITLE;
        return false;
      }
      if (matchAnchor.success()) {
        if (closingTag) return false;
        bufURL.setLength(0);
        matchHref.reset();
        state=IN_ANCHOR;
        return true;
      }
      if (matchBody.success()) {
        state=closingTag ?AFTER_BODY :IN_BODY;
        return !closingTag;
      }
      if (scriptMatcher.success()) {
        state=closingTag ?IN_BODY :IN_BODY_GARBAGE_SCRIPT;
        return closingTag;
      }
      if (styleMatcher.success()) {
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
