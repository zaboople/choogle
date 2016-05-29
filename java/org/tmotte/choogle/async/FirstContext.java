package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FirstContext<Input, Output> extends AnyContext<Input, Output> {
  FirstHandler<Output> fh;
  public FirstContext(AsyncContext a, FirstHandler<Output> fh) {
    super(a);
    this.fh=fh;
  }
  public FirstContext(HttpServletRequest hq, HttpServletResponse hr, FirstHandler<Output> fh) {
    this(hq.startAsync(hq, hr), fh);
  }
  public Output run(Input i) {
    return fh.handle(async);
  }
}
