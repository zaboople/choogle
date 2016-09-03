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

  public void makeContent(Appendable buffer, Exception parseFail, long index) {
    try {

      String
        indexStr=String.valueOf(index),
        nextIndexStr=String.valueOf(index-1);

      // Start up:
      buffer
        .append("<html>\r\n")
        .append("<head><title>Choogle: ")
        .append(indexStr)
        .append("</title></head>\r\n")
        .append("<body>\r\n");
      if (parseFail!=null)
        buffer
          .append("<p>Error: Your input was supposed to be number but I could not parse it.</p>");


      // Random numbers? Sure:
      if (true){
        buffer.append("<h3>First, A Random Number:</h3>\n<p>");
        makeRandomNumbers(buffer);
        buffer.append("</p>\n");
      }

      // Next number:
      buffer.append("<h3>What else?</h3>");
      if (index > 0)
        buffer.append("Oh yeah: <a href=\"/")
          .append(nextIndexStr)
          .append("\">Next number: ")
          .append(nextIndexStr)
          .append("</a>");
      buffer.append("<br></body></html>");

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final int randomLineWidth=48;
  private final int randomLineCount=16;

  /**
   * Makes a perfect rectangle of random numbers based on our constants.
   */
  private void makeRandomNumbers(Appendable buffer) throws Exception {
    int lineCount=-1, lineWidth=0;
    StringBuilder randomStr=new StringBuilder();
    while (lineCount < randomLineCount){
      lineCount++;
      lineWidth=0;
      while (lineWidth<randomLineWidth){
        int ranLen=randomStr.length();
        if (ranLen < randomLineWidth - lineWidth) {
          randomStr.append(
            String.valueOf(Math.abs(random.nextInt()))
          );
          ranLen=randomStr.length();
        }
        int appendLen=Math.min(ranLen, randomLineWidth - lineWidth);
        if (appendLen == 0)
          throw new RuntimeException("Failed to append anything to buffer");
        buffer.append(randomStr.substring(0, appendLen));
        randomStr.delete(0, appendLen);
        lineWidth+=appendLen;
      }
      buffer.append("<br>\n");
    }
  }
}
