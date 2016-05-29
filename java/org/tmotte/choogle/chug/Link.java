package org.tmotte.choogle.chug;

public class Link {
  String url;
  String title;
  public @Override String toString() {
    return new StringBuilder(
        (title==null ?5 :title.length())
        +1
        +(url==null ?5 :url.length())
      )
      .append(title).append(" ").append(url)
      .toString();
  }
}