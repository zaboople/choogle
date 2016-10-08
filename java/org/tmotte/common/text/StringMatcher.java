package org.tmotte.common.text;

/**
 * Used to match a specific string value, case-insensitive (no regex, wildcards, etc.).
 * Call add() one character at a time, checking soFarSoGood() to determine whether the match has failed
 * and finally success() to verify there was an exact match.
 */
public final class StringMatcher {

  // Once:
  private StringMatcherChars smc;
  private int stop=-1;

  // Changing:
  private int currIndex=0;
  private char currL, currU;
  private boolean success=false, keepGoing=true;

  public StringMatcher(StringMatcherChars smc) {
    this.smc=smc;
    this.stop=smc.lchars.length;
    setChar(0);
  }

  public void add(char c){
    if (!keepGoing)
      //We already failed, or we've gone too far, same difference:
      success=false;
    else
    if (currL==c || currU==c){
      // A match:
      currIndex++;
      if (currIndex==stop){
        success=true;
        keepGoing=false;
      }
      else
        setChar(currIndex);
    }
    else {
      // Failed:
      success=false;
      keepGoing=false;
    }
  }

  /** Indicates a successful match. */
  public boolean success(){
    return success;
  }

  /**
   * Indicates that so far we have not failed.
   * @return True if we have a partial
   * match, a full match, or are in our initial state
   * (just instantiated or after reset())
   */
  public boolean soFarSoGood(){
    return keepGoing || success;
  }

  /** A shortcut to add(char) + soFarSoGood() */
  public boolean soFarSoGood(char c){
    if (soFarSoGood()) {
      add(c);
      return soFarSoGood();
    }
    return false;
  }

  /** Reinitialize for matching a fresh batch of characters. */
  public void reset() {
    success=false;
    keepGoing=true;
    if (currIndex!=0)
      setChar(currIndex=0);
  }


  private void setChar(int index) {
    currL=smc.lchars[index];
    currU=smc.uchars[index];
  }

}
