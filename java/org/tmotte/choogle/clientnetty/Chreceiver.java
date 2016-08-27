package org.tmotte.choogle.clientnetty;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpContent;

interface Chreceiver {
  public void start(HttpResponse headers) throws Exception;
  public void body(HttpContent body) throws Exception;
  public void complete()throws Exception;
}