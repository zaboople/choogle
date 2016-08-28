package org.tmotte.common.nettyclient;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;

public interface MyResponseReceiver {
  public void start(HttpResponse headers) throws Exception;
  public void body(HttpContent body) throws Exception;
  public void complete(HttpHeaders trailingHeaders) throws Exception;
}