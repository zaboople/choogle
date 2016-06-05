package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;

public interface NextHandler<Input, Output> {
  public Output handle(AsyncContext ac, Input i);
}
