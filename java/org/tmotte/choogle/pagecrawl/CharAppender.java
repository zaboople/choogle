package org.tmotte.choogle.pagecrawl;

/**
 * Used by AnchorReader to allow clients to read the characters coming out of it.
 * There aren't java.util.function classes for primitive types, so we made this.
 */
interface CharAppender {
  public void append(char c);
}