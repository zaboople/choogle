package org.tmotte.choogle.service;
import java.util.Random;

/**
 * Implements our sort-of-clever system of putting a number in the URL
 * that can be scraped out, subtracted by 1 and turned into a new hyperlink. Thus
 * a simple WebCrawler can be a load tester and scaled by its initial
 * URL.
 */
public class LoadTestHTML {

  private Random random=new java.util.Random(System.currentTimeMillis());

  public void makeContent(Appendable buffer, String path) {
    try {

      // Get requested index:
      String indexStr=path.substring(
        path.lastIndexOf("/")
      );

      // Create the next lowest index:
      long index=1;
      if (indexStr.length()>1)
        try {
          index=Long.parseLong(indexStr.substring(1))-1;
        } catch (Exception e) {
          buffer
            .append("Error: Your input was supposed to be number but I could not parse it.");
        }
      indexStr=String.valueOf(index);

      //Render HTML:
      buffer.append("<html>\r\n");
      buffer.append("<head><title>")
        .append(String.valueOf(index))
        .append("</title></head>\r\n");
      buffer.append("<body>\r\n");
      buffer.append("<h3>First, A Random Number:</h3>\n<p>");
      for (int i=1; i<101; i++){
        buffer.append(String.valueOf(Math.abs(random.nextInt())));
        if (i % 5 == 0)
          buffer.append("<br>\n");
      }
      buffer.append("</p>\n");
      buffer.append("<h3>What else?</h3>");
      if (index > 0)
        buffer.append("Oh yeah: <a href=\"/")
          .append(indexStr)
          .append("\">Next number: ")
          .append(indexStr)
          .append("</a>");
      buffer.append("<br></body></html>");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}