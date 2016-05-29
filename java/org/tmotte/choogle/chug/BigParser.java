package org.tmotte.choogle.chug;

/**
 * Assumes that
 *   if you aren't interested in tag name, you aren't interested in attr name.
 *   if you aren't interested in attr name, you aren't interested in attr value.
 */
class BigParser {

  //FIXME lows should be highs, so that we don't collide with extra if logic
  private final static short
    CLEAN_START            =1,
    FIRST_AFTER_START_ANGLE=2,
    TAG_IS_NAMING          =4,
    WAITING_FOR_TAG_ATTRS  =5,
    IN_ATTR_NAME           =6,
    AFTER_ATTR_NAME        =7,
    AFTER_ATTR_EQUALS      =8,
    AFTER_ATTR_DBL_QUOTE   =9,
    AFTER_ATTR_QUOTE       =10,
    ATTR_VALUE_NO_QUOTE    =11,
    ELEMENT_SELF_CLOSING   =12,
    AFTER_BANG             =13,
    TAG_GARBAGED           =19,

    CDATA_AFTER_1_BRACK      =32,
    CDATA_AFTER_C            =33,
    CDATA_AFTER_D            =34,
    CDATA_AFTER_A            =35,
    CDATA_AFTER_T            =36,
    CDATA_AFTER_A2           =37,
    CDATA_TEXT               =38,
    CDATA_AFTER_CLOSE_BRACK_1=39,
    CDATA_AFTER_CLOSE_BRACK_2=40,

    COMMENT_AFTER_1_DASH      =64,
    COMMENT_TEXT              =65,
    COMMENT_CLOSE_AFTER_1_DASH=66,
    COMMENT_CLOSE_AFTER_2_DASH=67;


  private boolean recording=false;
  private BigParserListener reader;

  public BigParser(BigParserListener reader) {
    this.reader=reader;
  }


  public void add(String s) {
    int len=s.length();
    int i=0;
    short mode=CLEAN_START; //FIXME change to short
    while (i<len) {
      char c=s.charAt(i++);
      if (mode>=64)
        mode=parseComment(c, mode);
      else
      if (mode>=32)
        mode=parseCData(c, mode);
      else
        mode=parse(c, mode);
    }
  }


  private short parse(char c, short mode) {
    switch (mode){
      case CLEAN_START:
        if (c=='<'){
          recording=reader.tagNameStart();
          return FIRST_AFTER_START_ANGLE;
        }
        if (recording) recording=reader.text(c);
        return CLEAN_START;

      case FIRST_AFTER_START_ANGLE:
        // First char after <
        if (c=='/'){
          recording &= reader.tagNameComplete();
          return ELEMENT_SELF_CLOSING;
        }
        if (c=='>') {
          recording &= reader.tagNameComplete();
          recording =  reader.tagComplete(false);
          return CLEAN_START;
        }
        if (c=='=' || c=='\'' || c=='"')
          return tagGarbaged();
        if (isWhite(c) || c=='<')
          return FIRST_AFTER_START_ANGLE;
        if (c=='!')
          return AFTER_BANG;
        reader.tagName(c);
        return TAG_IS_NAMING;


      case TAG_IS_NAMING:
        // Still after <, getting tag name:
        if (isWhite(c)) {
          recording &=reader.tagNameComplete();
          return WAITING_FOR_TAG_ATTRS;
        }
        if (c=='/') {
          recording &= reader.tagNameComplete();
          return ELEMENT_SELF_CLOSING;
        }
        if (c=='>') {
          recording &= reader.tagNameComplete();
          recording = reader.tagComplete(false);
          return CLEAN_START;
        }
        if (c=='=' || c=='\'' || c=='"')
          return tagGarbaged();
        recording &= reader.tagName(c);
        return TAG_IS_NAMING;

      case WAITING_FOR_TAG_ATTRS:
        // Right after "<tag "
        if (isWhite(c)) return WAITING_FOR_TAG_ATTRS;
        if (c=='>') {
          recording=reader.tagComplete(false);
          return CLEAN_START;
        }
        if (c=='/')  return ELEMENT_SELF_CLOSING;
        if (c=='=')  return AFTER_ATTR_EQUALS;
        if (c=='\'') return AFTER_ATTR_QUOTE;
        if (c=='"')  return AFTER_ATTR_DBL_QUOTE;
        recording &= reader.attrNameStart();
        return parse(c, IN_ATTR_NAME);

      case IN_ATTR_NAME:
        // Right after "<tag  x"
        if (c=='=') {
          recording &= reader.attrNameComplete();
          return AFTER_ATTR_EQUALS;
        }
        if (c=='\'') {
          recording &= reader.attrNameComplete();
          return AFTER_ATTR_QUOTE;
        }
        if (c=='"') {
          recording &= reader.attrNameComplete();
          return AFTER_ATTR_DBL_QUOTE;
        }
        if (c==' ') {
          recording &= reader.attrNameComplete();
          return AFTER_ATTR_NAME;
        }
        recording &= reader.attrName(c);
        return IN_ATTR_NAME;

      case AFTER_ATTR_NAME:
        // We hit whitespace after attr name
        if (c=='=')  return AFTER_ATTR_EQUALS;
        if (c=='"')  return AFTER_ATTR_DBL_QUOTE;
        if (c=='\'') return AFTER_ATTR_QUOTE;
        if (c=='>') {
          recording &= reader.tagComplete(false);
          return CLEAN_START;
        }
        if (c=='/')
          return ELEMENT_SELF_CLOSING;
        if (c==' ')  return AFTER_ATTR_NAME;
        reader.attrNameStart();
        return parse(c, IN_ATTR_NAME);


      case AFTER_ATTR_EQUALS:
        // Right after "<tag xxx="
        if (c=='"')  return attrValueStart(AFTER_ATTR_DBL_QUOTE);
        if (c=='\'') return attrValueStart(AFTER_ATTR_QUOTE);
        if (isWhite(c)) return AFTER_ATTR_EQUALS;
        if (c=='>') {
          recording = reader.tagComplete(false);
          return CLEAN_START;
        }
        if (c=='/')
          return ELEMENT_SELF_CLOSING;
        reader.attrValueStart();
        return parse(c, ATTR_VALUE_NO_QUOTE);

      case AFTER_ATTR_DBL_QUOTE:
        if (c=='"') {
          recording &= reader.attrValueComplete();
          return WAITING_FOR_TAG_ATTRS;
        }
        recording &= reader.attrValue(c);
        return AFTER_ATTR_DBL_QUOTE;

      case AFTER_ATTR_QUOTE:
        if (c=='\'') {
          recording &= reader.attrValueComplete();
          return WAITING_FOR_TAG_ATTRS;
        }
        recording &= reader.attrValue(c);
        return AFTER_ATTR_QUOTE;

      case ATTR_VALUE_NO_QUOTE:
        if (c==' ') {
          recording &= reader.attrValueComplete();
          return WAITING_FOR_TAG_ATTRS;
        }
        if (c=='/') {
          recording &= reader.attrValueComplete();
          return ELEMENT_SELF_CLOSING;
        }
        if (c=='>') {
          recording &= reader.attrValueComplete();
          recording = reader.tagComplete(false);
          return CLEAN_START;
        }
        recording &= reader.attrValue(c);
        return ATTR_VALUE_NO_QUOTE;

      case ELEMENT_SELF_CLOSING:
        // Self closing, after /
        if (c=='>') {
          recording &= reader.tagComplete(true);
          return CLEAN_START;
        }
        return ELEMENT_SELF_CLOSING;

      case AFTER_BANG:
        // <!
        recording &= reader.tagNameComplete();
        if (c=='[') return CDATA_AFTER_1_BRACK;
        if (c=='-') return COMMENT_AFTER_1_DASH;
        if (c==' ') return AFTER_BANG;
        return TAG_GARBAGED;

      case TAG_GARBAGED:
        if (c=='>') {
          recording &= reader.tagComplete(true);
          return CLEAN_START;
        }
        return TAG_GARBAGED;

      default:
        throw new RuntimeException("Unexpected: "+c);
    }
  }

  private short tagGarbaged() {
    recording &= reader.tagNameComplete();
    return TAG_GARBAGED;
  }
  private short attrValueStart(short returnMode){
    reader.attrValueStart();
    return returnMode;
  }
  private short parseComment(char c, short mode) {
    switch(mode) {
      case COMMENT_AFTER_1_DASH:
        if (c=='-') return COMMENT_TEXT;
        recording=reader.commentStart();
        return parseComment(c, COMMENT_TEXT);

      case COMMENT_TEXT:
        if (c=='-') return COMMENT_CLOSE_AFTER_1_DASH;
        recording &= reader.comment(c);
        return COMMENT_TEXT;

      case COMMENT_CLOSE_AFTER_1_DASH:
        if (c=='-') return COMMENT_CLOSE_AFTER_2_DASH;
        recording &= (reader.comment('-') && reader.comment(c));
        return COMMENT_TEXT;

      case COMMENT_CLOSE_AFTER_2_DASH:
        if (c=='>') {
          if (recording) reader.commentComplete();
          recording = reader.tagComplete(true);
          return CLEAN_START;
        }
        recording &= (reader.comment('-') && reader.comment('-') && reader.comment(c));
        return COMMENT_TEXT;

      default:
        throw new RuntimeException("Unexpected: "+mode);
    }
  }

  private short parseCData(char c, short mode) {
    switch (mode) {
      case CDATA_AFTER_1_BRACK:
        if (c=='C' || c=='c') return CDATA_AFTER_C;
        return tagGarbaged();
      case CDATA_AFTER_C:
        if (c=='D' || c=='d') return CDATA_AFTER_D;
        return tagGarbaged();
      case CDATA_AFTER_D:
        if (c=='A' || c=='a') return CDATA_AFTER_A;
        return tagGarbaged();
      case CDATA_AFTER_A:
        if (c=='T' || c=='t') return CDATA_AFTER_T;
        return tagGarbaged();
      case CDATA_AFTER_T:
        if (c=='A' || c=='a') return CDATA_AFTER_A2;
        return tagGarbaged();
      case CDATA_AFTER_A2:
        if (c=='[') {
          recording &= reader.cdataStart();
          return CDATA_TEXT;
        }
        return tagGarbaged();

      case CDATA_TEXT:
        if (c==']') return CDATA_AFTER_CLOSE_BRACK_1;
        reader.cdata(c);
        return CDATA_TEXT;

      case CDATA_AFTER_CLOSE_BRACK_1:
        if (c==']') return CDATA_AFTER_CLOSE_BRACK_2;
        recording &= (reader.cdata(']') && reader.cdata(c));
        return CDATA_TEXT;
      case CDATA_AFTER_CLOSE_BRACK_2:
        if (c=='>') {
          recording &= reader.cdataComplete();
          recording = reader.tagComplete(true);
          return CLEAN_START;
        }
        recording &= (reader.cdata(']') && reader.cdata(']') && reader.cdata(c));
        return CDATA_TEXT;
      default:
        throw new RuntimeException("Unexpected: "+mode);
    }
  }

  private static boolean isWhite(char c) {
    return c==' ' || c=='\t' || c=='\n' || c=='\r';
  }


}
