package org.tmotte.choogle.chug;
class StringMatcherChars {
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
