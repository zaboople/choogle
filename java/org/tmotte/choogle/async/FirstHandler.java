package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;

public interface FirstHandler<Output> {
  public Output handle(AsyncContext ac);
}
