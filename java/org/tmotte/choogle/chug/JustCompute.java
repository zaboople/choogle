package org.tmotte.choogle.chug;
import java.util.Random;

public class JustCompute {
  Random rand=new Random(System.currentTimeMillis());
  private synchronized int syncNext() {
    return rand.nextInt();
  }
  public int getNext() {
    int n=0;
    for (int i=0; i<10000; i++)
      n+=syncNext();
    return n;
  }

}