package org.tmotte.common.text;

public interface HTMLParserListener {

  /**
   * Called by HTMLParser.reset();
   */
  public void reset();

  public boolean text(char c, boolean inScript);

  public boolean tagNameComplete(boolean isClosing, CharSequence cs);

  /**
   * @param selfClosing means that the tag ends with /&gt;, so you
   *        should not expect a closing tag (refer to tagIsClosing()) to match it.
   * @return true if you want to text() to be called with characters that come after.
   */
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
