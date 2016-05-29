package org.tmotte.choogle.clientnetty;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpContent;

public interface Chreceiver {
  public void start(HttpResponse headers);
  public void body(HttpContent body);
  public void complete();
}