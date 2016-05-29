package org.tmotte.choogle.servejetty;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;


public interface IMyHandler {
  public void handle(AsyncContext async, MyAsyncProcessor asyncProcessor) throws Exception;
}