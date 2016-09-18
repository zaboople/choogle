package org.tmotte.common.text;

/**
 * My own homemade logging class. This only logs to System.out, nothing else.
 * An Outlog client is responsible for deciding whether to make noise; Outlog.is()
 * tells the client if the desired logging level is acceptable, but the various add()
 * methods and so forth don't check it.
 */
public final class Outlog {

  private int level=0;
  private String dateFormat="%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS%1$tz ";

  public Outlog setLevel(int level) {
    this.level=level;
    return this;
  }
  public int getLevel() {
    return level;
  }
  public boolean is(int level) {
    return level <= this.level;
  }

  /**
   * @param fmt Can be null, in which case date print is repressed
   */
  public Outlog setDateFormat(String fmt) {
    this.dateFormat=fmt;
    return this;
  }


  public Outlog date() {
    if (dateFormat!=null)
      System.out.append(
        String.format(dateFormat, new java.util.Date())
      );
    return this;
  }
  public Outlog lf() {
    System.out.append("\n");
    return this;
  }
  public Outlog add(String s) {
    System.out.append(s);
    return this;
  }
  public Outlog add(Object o) {
    System.out.append(o.toString());
    return this;
  }
  public Outlog add(int i) {
    System.out.append(String.valueOf(i));
    return this;
  }
  public Outlog add(long i) {
    System.out.append(String.valueOf(i));
    return this;
  }
  public Outlog add(boolean i) {
    System.out.append(String.valueOf(i));
    return this;
  }
  public Outlog add(double d) {
    System.out.append(String.valueOf(d));
    return this;
  }
  public Outlog add(char c) {
    System.out.append(c);
    return this;
  }

}