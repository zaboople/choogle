package org.tmotte.choogle.chug;

class BigParserTester {

  final static class BigPrinter implements BigParserListener {
    private boolean print(char c) {
      System.out.print(c); return true;
    }
    private boolean print(String s) {
      System.out.print(s); return true;
    }
    private boolean println(){
      System.out.println(); return true;
    }
    private boolean println(char c){
      System.out.println(c); return true;
    }
    private boolean println(String c){
      System.out.println(c); return true;
    }
    public boolean text(char c){return print(c);}

    public boolean tagNameStart(){return print("<");}
    public boolean tagName(char c){return print(c);}
    public boolean tagNameComplete(){return print(" ");}
    public boolean tagComplete(boolean selfClosing){return println(">  SC="+selfClosing+"\n");}

    public boolean attrNameStart(){return print("  ");}
    public boolean attrName(char c){return print(c);}
    public boolean attrNameComplete(){return print(" ");}

    public boolean attrValueStart(){return print(" = '");}
    public boolean attrValue(char c){return print(c);}
    public boolean attrValueComplete(){return print("'");}

    public boolean cdataStart(){return true;}
    public boolean cdata(char c){return true;}
    public boolean cdataComplete(){return true;}

    public boolean commentStart(){return true;}
    public boolean comment(char c){return true;}
    public boolean commentComplete(){return true;}

  }
  public static void main(String[] args) {
    BigParser bp=new BigParser(new BigPrinter());
    bp.add("<a pig=booger pig2=\"mi\">hello</a>");
  }
}