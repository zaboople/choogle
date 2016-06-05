package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class NextContext<Input, Output> extends AnyContext<Input, Output> {
  NextHandler<Input, Output> fh;
  AnyContext<?, Input> ac;

  public NextContext(AsyncContext a, AnyContext<?, Input> ac, NextHandler<Input, Output> fh) {
    super(a);
    this.ac=ac;
    this.fh=fh;
  }

  public Output run(Input i) {
    return fh.handle(async, i);
  }

}
