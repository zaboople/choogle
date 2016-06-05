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

    public boolean text(char c){print("-");return print(c);}

    public boolean tagStart(){return print("\nTAG: <");}
    public boolean tagIsClosing(){return print("/");}
    public boolean tagName(char c){return print(c);}
    public boolean tagNameComplete(){return print("");}
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

  public static void main(String[] args) {
    BigPrinter prn=new BigPrinter();
    System.out.println("=========");
    HTMLParser bp=new HTMLParser(prn);
    bp.add("<abc pig=booger pig2=\"mi\">hello</abc><div x>ee</div>");
    System.out.println("=========");
    bp.add("<div><![CDATA[  a.type=<fudge></bomb> ] ] ]>  ]]> <!--A comment-- -- ->--></div>");
    System.out.println("=========");
    bp.add("<");
    bp.add("zoom ");
    bp.add(" wheels='off' tires=\"roof\"/><l>hi<b></l>");
    System.out.println("=========");
    bp.add("<p wheels =  off    tires =\"roof\" notion=   \"bizarre   \">");

    System.out.println("=========");
    prn.yes=false;
    bp.add("<tag val='yes'>");
    System.out.println("=========");
    prn.yes=true;
    prn.yesAttr=false;
    bp.add("<tag val='yes'>");
    System.out.println("=========");
  }
}