package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;

public interface LastHandler<Input> {
  public void handle(AsyncContext ac, Input i);
}
