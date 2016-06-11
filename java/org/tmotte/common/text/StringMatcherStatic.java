package org.tmotte.common.text;

/** This is threadsafe */
public final class StringMatcherStatic {

  private StringMatcherChars smc;
  private int stop=-1;

  public StringMatcherStatic(StringMatcherChars smc) {
    this.smc=smc;
    this.stop=smc.lchars.length;
  }
  public StringMatcherStatic(String s) {
    this(new StringMatcherChars(s));
  }

  public boolean matches(CharSequence s) {
    int currIndex=0;
    int len=s.length();
    if (len!=stop)
      return false;
    for (int i=0; i<len; i++){
      char c=s.charAt(i);
      if (smc.lchars[i]!=c && smc.uchars[i]!=c)
        return false;
    }
    return true;
  }
}
