package org.tmotte.choogle.pagecrawl;
import java.util.Collection;
import org.tmotte.common.text.HTMLParserListener;
import org.tmotte.common.text.HTMLParser;
import org.tmotte.common.text.StringMatcher;
import org.tmotte.common.text.StringMatcherStatic;
import org.tmotte.common.text.StringMatcherChars;

public final class AnchorReader { //FIXME why public?

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
    ,
    matchP =new StringMatcherStatic("p")
    ,
    matchDiv =new StringMatcherStatic("div")
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
  private MyListener listener;

  ///////////////////////////
  // INPUTS + CONSTRUCTOR: //
  ///////////////////////////

  //FIXME don't take as parameter
  private Collection<String> values;
  public AnchorReader(Collection<String> values, CharAppender charAppender) {
    this.values=values;
    listener=new MyListener(charAppender);
    parser=new HTMLParser(listener);
  }

  ///////////////////////
  // PUBLIC FUNCTIONS: //
  ///////////////////////

  public String getTitle() {
    return bufTitle.toString().trim();
  }
  public void add(String s) {
    parser.add(s);
  }
  public void reset() {
    parser.reset();
    bufURL.setLength(0);
    bufTitle.setLength(0);
    listener.reset();//FIXME this also gets called by parser
  }

  ////////////////////////////
  // INTERFACE FULFILLMENT: //
  ////////////////////////////

  private class MyListener implements HTMLParserListener {
    private final CharAppender charAppender;

    private boolean wasWhite=false;
    private short state=BEFORE_TITLE;
    private StringMatcher matchHref=new StringMatcher(smcHref);

    public MyListener(CharAppender charAppender) {
      this.charAppender=charAppender;
    }
    public void reset() {
      state=BEFORE_TITLE;
      wasWhite=false;
      matchHref.reset();
    }

    public boolean tagNameComplete(boolean closingTag, CharSequence cs){
      if (matchTitle.matches(cs)){
        if (state==BEFORE_TITLE)
          state=closingTag ?BEFORE_BODY :IN_TITLE;
        else
        if (state==IN_TITLE)
          state=BEFORE_BODY;
        return state==IN_TITLE;
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
        if (state==IN_BODY || state==IN_BODY_GARBAGE_SCRIPT)
          state=closingTag ?IN_BODY :IN_BODY_GARBAGE_SCRIPT;
        return closingTag;
      }
      if (matchStyle.matches(cs)) {
        if (state==IN_BODY || state==IN_BODY_GARBAGE_STYLE)
          state=closingTag ?IN_BODY :IN_BODY_GARBAGE_STYLE;
        return closingTag;
      }
      if (state==IN_BODY && charAppender!=null && !wasWhite && (matchP.matches(cs) || matchDiv.matches(cs))){
        charAppender.append((char)10);
        wasWhite=true;
      }
      return false;
    }
    public boolean tagComplete(boolean selfClosing){
      if (state==IN_ANCHOR)
        state=IN_BODY;
      else
      if (state==IN_TITLE && !selfClosing)
        return true;
      else
      if (state==IN_BODY_GARBAGE_SCRIPT && selfClosing)
        state=IN_BODY;
      else
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
      values.add(bufURL.toString());
      bufURL.setLength(0);
      return false;
    }
    public boolean text(char c, boolean inScript){
      if (state==IN_BODY && charAppender!=null) {
        boolean thisWhite=c==' ' || c=='\t' || c==13 || c==10;
        if (!wasWhite || !thisWhite) {
          charAppender.append(c);
          wasWhite=thisWhite;
        }
        return true;
      }
      else
      if (state==IN_TITLE) {
        bufTitle.append(c);
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
    Collection<String> values=new java.util.HashSet<>(1000);
    AnchorReader bp=new AnchorReader(values, x->System.out.print(x));
    String s=null;
    while ((s=br.readLine())!=null)
      bp.add(s);
    System.out.println();
    for (String v : values)   System.out.println(v);
    System.out.println("TITLE "+bp.getTitle());
    System.out.flush();
  }
}
