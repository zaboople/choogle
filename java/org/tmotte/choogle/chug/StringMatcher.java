package org.tmotte.choogle.chug;
class StringMatcher {

  // Once:
  private StringMatcherChars smc;
  private int whenDoneReturn=-1;
  private int stop=-1;
  private int noMatch=0, match=1, done=2;

  // Changing:
  private int currIndex=0;
  private char currL, currU;

  public StringMatcher(
      StringMatcherChars smc, int noMatch, int match, int done
    ) {
    this.smc=smc;
    this.noMatch=noMatch;
    this.match=match;
    this.done=done;
    this.stop=smc.lchars.length;
    setChar(0);
  }
  private void setChar(int index) {
    currL=smc.lchars[index];
    currU=smc.uchars[index];
  }
  public int match(char c){
    if (currL==c || currU==c){
      currIndex++;
      if (currIndex==stop) {
        setChar(currIndex=0);
        return done;
      }
      setChar(currIndex);
      return match;
    }
    setChar(currIndex=0);
    return noMatch;
  }

}