package org.tmotte.common.text;

class HTMLParserTest {

  final static class BigPrinter implements HTMLParserListener {
    boolean yes=true, yesAttr=true;



    private boolean print(char c) {
      System.out.print(c); return yes;
    }
    private boolean print(String s) {
      System.out.print(s); return yes;
    }
    private boolean printAttr(char c) {
      System.out.print(c); return yesAttr;
    }
    private boolean printAttr(String s) {
      System.out.print(s); return yesAttr;
    }

    public void reset() {
      yes=true; yesAttr=true;
    }

    public boolean text(char c, boolean inScript){
      print(inScript ?"*" :"-");
      return print(c);
    }

    public boolean tagNameComplete(boolean tagIsClosing, CharSequence cs){
      print("\nTAG: <");
      if (tagIsClosing)
        print("/");
      return print(cs.toString());
    }
    public boolean tagComplete(boolean selfClosing){
      if (selfClosing) print("/");
      return print(">\n");
    }

    public boolean attrNameStart(){return printAttr(" [");}
    public boolean attrName(char c){return printAttr(c);}
    public boolean attrNameComplete(){return printAttr("]");}

    public boolean attrValueStart(){return printAttr("='");}
    public boolean attrValue(char c){return printAttr(c);}
    public boolean attrValueComplete(){return printAttr("'");}

    public boolean cdataStart(){return print("CDATA: ");}
    public boolean cdata(char c){return print(c);}
    public boolean cdataComplete(){return print("END CDATA");}

    public boolean commentStart(){return print("COMMENT: ");}
    public boolean comment(char c){print('.'); return print(c);}
    public boolean commentComplete(){return print("END COMMENT");}

  }
  private static class BigDriver {
    private BigPrinter prn=new BigPrinter();
    private HTMLParser bp=new HTMLParser(prn);
    public BigDriver add(String s) {
      bp.add(s);
      return this;
    }
    public BigDriver doc(String s) {
      System.out.print("\n-- ");
      System.out.print(s);
      System.out.print(": ");
      int len=50-s.length();
      for (int i=0; i<len; i++)
        System.out.print("-");
      return this;
    }
    public BigDriver setYes(boolean b) {
      prn.yes=b;
      return this;
    }
    public BigDriver setYesAttr(boolean b) {
      prn.yesAttr=b;
      return this;
    }
  }

  public static void main(String[] args) {
    new BigDriver()
      .doc("Basic open-close")
        .add("<div> </div>")

      .doc("General attribute stuff")
      .add("<abc pig=booger pig2=\"mi\">hello</abc><div x>ee</div>")

      .doc("CDATA & COMMENT")
      .add("<div> <![CDATA[  a.type=<fudge></bomb> ] ] ]>  ]]> ")
      .add("<!--A comment-- -- ->--></div>")

      .doc("C")
      .add("<")
      .add("zoom ")
      .add(" wheels='off' tires=\"roof\"/><l>hi<b></l>")

      .doc("D")
      .add("<p wheels =  off    tires =\"roof\" notion=   \"bizarre   \">")

      .doc("E Should not print tag name or attrs")
      .setYes(false)
      .add("<tag val='yes'>")

      .doc("F Should not print attr names or values")
      .setYes(true)
      .setYesAttr(false)
      .add("<tag val='yes'>")

      .doc("G Should know script vs. not script:")
      .add("<script> blah blah <fuh><a href='berp'><script>\n")
      .add("wee </~~~ </~~ bler<!- <![CDATA[ ha it's cdata ]]>more </s> dumb<><!<<< </sc></script>")

      .doc("")
     ;
  }
}