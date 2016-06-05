package org.tmotte.choogle.async;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AnyContext<Input, Output> {
  AsyncContext async;
  private AnyContext<Output, ?> next;
  public AnyContext(AsyncContext a) {
    this.async=a;
  }
  public abstract Output run(Input i);
  public <Next> AnyContext<Output, Next> add(NextHandler<Output, Next> nh){
    NextContext<Output, Next> temp=new NextContext<>(async, this, nh);
    this.next=temp;
    return temp;
  }
}
