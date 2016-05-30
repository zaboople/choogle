package org.tmotte.choogle.chug;

interface BigParserListener {
  public boolean text(char c);

  public boolean tagNameStart();
  /** Means the tag is of the form </foo>, not <foo ... /> or <foo ...> */
  public boolean tagIsClosing();
  public boolean tagName(char c);
  public boolean tagNameComplete();
  public boolean tagComplete(boolean selfClosing);

  public boolean attrNameStart();
  public boolean attrName(char c);
  public boolean attrNameComplete();

  public boolean attrValueStart();
  public boolean attrValue(char c);
  public boolean attrValueComplete();

  public boolean cdataStart();
  public boolean cdata(char c);
  public boolean cdataComplete();
  public boolean commentStart();
  public boolean comment(char c);
  public boolean commentComplete();
}