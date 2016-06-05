package org.tmotte.choogle.chug;
import org.tmotte.common.text.HTMLParserListener;
import org.tmotte.common.text.StringMatcher;
import org.tmotte.common.text.StringMatcherChars;

public class TextCrawler implements HTMLParserListener {

  private static StringMatcherChars
    smcTitle=new StringMatcherChars("title"),
    smcBody=new StringMatcherChars("body");

  boolean closingTag=false;
  short state;
  short
    BEFORE_TITLE=2,
    IN_TITLE=3,
    BEFORE_BODY=4,
    IN_BODY=5,
    AFTER_BODY=6,
    IN_GARBAGE=7;

  private StringBuilder titleBuf=new StringBuilder();
  private String title;

  public boolean text(char c){
    if (state==IN_TITLE) {
      titleBuf.append(c);
      return true;
    }
    if (state!=IN_BODY)
      return false;
    System.out.print(c);
    return true;
  }

  public boolean tagNameStart(){
    closingTag=false;
    if (state==BEFORE_TITLE)
      return true;
    if (state==IN_TITLE){
      state=BEFORE_BODY;
      return true;
    }
    if (state==IN_BODY)
      //Because we're looking for /body
      return true;
    return false;
  }
  public boolean tagIsClosing(){
    closingTag=true;
    return state==IN_BODY;
  }
  public boolean tagName(char c){
    //tagNameBuf.append(c);
    return true;
  }
  public boolean tagNameComplete(){
    /*
    String name=tagNameBuf.toString().trim();
    if (state==BEFORE_TITLE) {
      if ("title".equalsIgnoreCase(name) && !closingTag)
        state=IN_TITLE;
    }
    else
    if (state==BEFORE_BODY) {
      if ("body".equalsIgnoreCase(name) && !closingTag)
        state=IN_BODY;
    }
    else
    if (state==IN_BODY) {
      boolean crap="script".equalsIgnoreCase(name) || "style".equalsIgnoreCase(name);
      if (crap && !closingTag)
        state=IN_GARBAGE;
      if ("body".equalsIgnoreCase(name) && closingTag)
        state=AFTER_BODY;
    }
    else
    if (state==IN_GARBAGE) {
      boolean crap="script".equalsIgnoreCase(name) || "style".equalsIgnoreCase(name);
      if (crap && closingTag)
        state=IN_BODY;
    }
    else
    if (state==IN_TITLE || state==AFTER_BODY){}
    else
      throw new RuntimeException("Indeterminate state: "+state);
    */
    return true;
  }
  public boolean tagComplete(boolean selfClosing){
    return true;
  }

  public boolean attrNameStart(){return false;}
  public boolean attrName(char c){return false;}
  public boolean attrNameComplete(){return false;}

  public boolean attrValueStart(){return false;}
  public boolean attrValue(char c){return false;}
  public boolean attrValueComplete(){return false;}

  public boolean cdataStart(){return false;}
  public boolean cdata(char c){return false;}
  public boolean cdataComplete(){return false;}
  public boolean commentStart(){return false;}
  public boolean comment(char c){return false;}
  public boolean commentComplete(){return false;}
}
