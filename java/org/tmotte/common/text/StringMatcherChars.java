package org.tmotte.common.text;

/**
 * This is the stateless part of StringMatcher, so it's kept separate, so you
 * can instantiate it as a thread-safe static variable.
 */
public class StringMatcherChars {
  char[] lchars;
  char[] uchars;
  public StringMatcherChars(String match) {
    lchars=new char[match.length()];
    uchars=new char[match.length()];
    for (int i=0; i<lchars.length; i++){
      char c=(match.charAt(i));
      lchars[i]=Character.toLowerCase(c);
      uchars[i]=Character.toUpperCase(c);
    }
  }
}
