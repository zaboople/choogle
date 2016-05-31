package org.tmotte.choogle.chug;

/**
 * This is an ultra-ultra-minimal HTML parser. It doesn't know anything about HTML, just
 * about tags (open, close & self-closing), attributes, comments, & cdata. Usefulness
 * is obtained by providing it a listener that receives signals for tag/attribute/comment/cdata
 * start/end and 1-character-at-a-time signals for the data within them.
 *
 * Note that it assumes that if your listener:
 *   - isn't interested in tag name, it isn't interested in attributes.
 *   - isn't interested in attr name, it isn't interested in attr value.
 * The listener will *always* be notified at the beginning & end of a tag, but anytime
 * it loses interest (by returning false) it will stop getting notifications temporarily.
 *
 * Another highly internal note: &= is not short-circuited!!! Don't try that.
 */
class BigParser {

  //////////////////////////////
  // STATIC FUNCTIONS & DATA: //
  //////////////////////////////

  /** A utility function. Probably wrong, too. */
  public static boolean isWhite(char c) {
    return c==' ' || c=='\t' || c=='\n' || c=='\r';
  }

  /**
   * These are the different values for mode.
   * Note they are divided up into a 64 block, a 16 block and a 0 block.
   * The add() function does an if-else-if-else on the blocks.
   */
  private final static short
    CLEAN_START            =65,
    FIRST_AFTER_START_ANGLE=66,
    TAG_IS_NAMING          =67,
    WAITING_FOR_TAG_ATTRS  =68,
    IN_ATTR_NAME           =69,
    AFTER_ATTR_NAME        =70,
    AFTER_ATTR_EQUALS      =71,
    AFTER_ATTR_DBL_QUOTE   =72,
    AFTER_ATTR_QUOTE       =73,
    ATTR_VALUE_NO_QUOTE    =74,
    ELEMENT_SELF_CLOSING   =75,
    TAG_IS_CLOSING         =76,
    AFTER_BANG             =77,
    TAG_GARBAGED           =78,

    CDATA_AFTER_1_BRACK      =17,
    CDATA_AFTER_C            =18,
    CDATA_AFTER_D            =19,
    CDATA_AFTER_A            =20,
    CDATA_AFTER_T            =21,
    CDATA_AFTER_A2           =22,
    CDATA_TEXT               =23,
    CDATA_AFTER_CLOSE_BRACK_1=24,
    CDATA_AFTER_CLOSE_BRACK_2=25,

    COMMENT_AFTER_1_DASH      =1,
    COMMENT_TEXT              =2,
    COMMENT_CLOSE_AFTER_1_DASH=3,
    COMMENT_CLOSE_AFTER_2_DASH=4;

  /////////////////////////////////
  // PRIVATE DATA & CONSTRUCTOR: //
  /////////////////////////////////

  private final InnerParser inner;
  private short mode=CLEAN_START;
  public BigParser(BigParserListener reader) {
    inner=new InnerParser(reader);
  }

  //////////////////////////
  // *ONE* PUBLIC METHOD: //
  //////////////////////////

  public BigParser add(String s) {
    int len=s.length();
    for (int i=0; i<len; i++){
      char c=s.charAt(i);
      if (mode>=64)
        mode=inner.parse(c, mode);
      else
      if (mode>=16)
        mode=inner.parseCData(c, mode);
      else
        mode=inner.parseComment(c, mode);
    }
    return this;
  }

  //////////////////////
  // INNER CLASS(ES): //
  //////////////////////

  /**
   * This is only separate so that we don't have confusion about mode;
   * which in this context is input/output only, not retained state.
   * The external class tracks that state.
   */
  private static class InnerParser {

    // INSTANCE VARIABLES AND CONSTRUCTOR:
    private final BigParserListener reader;
    private boolean
      record=true,
      recordAttr=true;
    InnerParser(BigParserListener reader) {
      this.reader=reader;
    }

    // CONVENIENCE FUNCTIONS USED BY PARSERS:
    private short tagNameCompleteAndGarbaged() {
      record=record && reader.tagNameComplete();
      return TAG_GARBAGED;
    }
    private short tagCompleteCleanStart(boolean selfClosing) {
      record = reader.tagComplete(selfClosing);
      return CLEAN_START;
    }

    // EVERYTHING ELSE IS OUR
    // 3 PARSING FUNCTIONS:
    short parse(char c, short mode) {
      switch (mode){
        case CLEAN_START:
          if (c=='<'){
            record=reader.tagNameStart();
            return FIRST_AFTER_START_ANGLE;
          }
          record=record && reader.text(c);
          return CLEAN_START;

        case FIRST_AFTER_START_ANGLE:
          // First char after <
          if (c=='/'){
            record=record && reader.tagIsClosing();
            return TAG_IS_CLOSING;
          }
          if (c=='>') {
            record=record && reader.tagNameComplete();
            return tagCompleteCleanStart(false);
          }
          if (c=='=' || c=='\'' || c=='"')
            return tagNameCompleteAndGarbaged();
          if (isWhite(c) || c=='<')
            return FIRST_AFTER_START_ANGLE;
          if (c=='!')
            return AFTER_BANG;
          record=record && reader.tagName(c);
          return TAG_IS_NAMING;


        case TAG_IS_NAMING:
          // Still after <, getting tag name:
          if (isWhite(c)) {
            record=record && reader.tagNameComplete();
            return WAITING_FOR_TAG_ATTRS;
          }
          if (c=='/') {
            record=record && reader.tagNameComplete();
            return ELEMENT_SELF_CLOSING;
          }
          if (c=='>') {
            record=record && reader.tagNameComplete();
            return tagCompleteCleanStart(false);
          }
          if (c=='=' || c=='\'' || c=='"')
            return tagNameCompleteAndGarbaged();
          record=record && reader.tagName(c);
          return TAG_IS_NAMING;

        case WAITING_FOR_TAG_ATTRS:
          // Right after "<tag "
          if (isWhite(c)) return WAITING_FOR_TAG_ATTRS;
          if (c=='>')
            return tagCompleteCleanStart(false);
          if (c=='/')  return ELEMENT_SELF_CLOSING;
          if (c=='=')  return AFTER_ATTR_EQUALS;
          if (c=='\'') return AFTER_ATTR_QUOTE;
          if (c=='"')  return AFTER_ATTR_DBL_QUOTE;
          recordAttr=record && reader.attrNameStart();
          return parse(c, IN_ATTR_NAME);

        case IN_ATTR_NAME:
          // Right after "<tag  x"
          if (c=='=') {
            recordAttr=recordAttr && reader.attrNameComplete();
            return AFTER_ATTR_EQUALS;
          }
          if (c=='\'') {
            recordAttr=recordAttr && reader.attrNameComplete();
            return AFTER_ATTR_QUOTE;
          }
          if (c=='"') {
            recordAttr=recordAttr && reader.attrNameComplete();
            return AFTER_ATTR_DBL_QUOTE;
          }
          if (c==' ') {
            recordAttr=recordAttr && reader.attrNameComplete();
            return AFTER_ATTR_NAME;
          }
          if (c=='>') {
            recordAttr=recordAttr && reader.attrNameComplete();
            return tagCompleteCleanStart(false);
          }
          recordAttr=recordAttr && reader.attrName(c);
          return IN_ATTR_NAME;

        case AFTER_ATTR_NAME:
          // We hit whitespace after attr name
          if (c=='=')  return AFTER_ATTR_EQUALS;
          if (c=='"')  return AFTER_ATTR_DBL_QUOTE;
          if (c=='\'') return AFTER_ATTR_QUOTE;
          if (c=='>')  return tagCompleteCleanStart(false);
          if (c=='/')
            return ELEMENT_SELF_CLOSING;
          if (c==' ')  return AFTER_ATTR_NAME;
          reader.attrNameStart();
          return parse(c, IN_ATTR_NAME);


        case AFTER_ATTR_EQUALS:
          // Right after "<tag xxx="
          if (c=='"')  {
            recordAttr=recordAttr && reader.attrValueStart();
            return AFTER_ATTR_DBL_QUOTE;
          }
          if (c=='\'') {
            recordAttr=recordAttr && reader.attrValueStart();
            return AFTER_ATTR_QUOTE;
          }
          if (isWhite(c)) return AFTER_ATTR_EQUALS;
          if (c=='>')     return tagCompleteCleanStart(false);
          if (c=='/')     return ELEMENT_SELF_CLOSING;
          recordAttr=recordAttr && reader.attrValueStart();
          return parse(c, ATTR_VALUE_NO_QUOTE);

        case AFTER_ATTR_DBL_QUOTE:
          if (c=='"') {
            recordAttr=recordAttr && reader.attrValueComplete();
            return WAITING_FOR_TAG_ATTRS;
          }
          recordAttr=recordAttr && reader.attrValue(c);
          return AFTER_ATTR_DBL_QUOTE;

        case AFTER_ATTR_QUOTE:
          if (c=='\'') {
            recordAttr=recordAttr && reader.attrValueComplete();
            return WAITING_FOR_TAG_ATTRS;
          }
          recordAttr=recordAttr && reader.attrValue(c);
          return AFTER_ATTR_QUOTE;

        case ATTR_VALUE_NO_QUOTE:
          if (c==' ') {
            recordAttr=recordAttr && reader.attrValueComplete();
            return WAITING_FOR_TAG_ATTRS;
          }
          if (c=='/') {
            recordAttr=recordAttr && reader.attrValueComplete();
            return ELEMENT_SELF_CLOSING;
          }
          if (c=='>') {
            recordAttr=recordAttr && reader.attrValueComplete();
            return tagCompleteCleanStart(false);
          }
          recordAttr=recordAttr && reader.attrValue(c);
          return ATTR_VALUE_NO_QUOTE;

        case ELEMENT_SELF_CLOSING:
          // Self closing, after /
          if (c=='>') return tagCompleteCleanStart(true);
          return ELEMENT_SELF_CLOSING;

        case TAG_IS_CLOSING:
          // After the "/" in "</....>"
          if (c=='>'){
            record=record && reader.tagNameComplete();
            return tagCompleteCleanStart(false);
          }
          record=record && reader.tagName(c);
          return TAG_IS_CLOSING;

        case AFTER_BANG:
          // <!
          record=record && reader.tagNameComplete();
          if (c=='[') return CDATA_AFTER_1_BRACK;
          if (c=='-') return COMMENT_AFTER_1_DASH;
          if (c==' ') return AFTER_BANG;
          return TAG_GARBAGED;

        case TAG_GARBAGED:
          if (c=='>') return tagCompleteCleanStart(false);
          return TAG_GARBAGED;

        default:
          throw new RuntimeException("Unexpected: "+c);
      }
    }


    short parseComment(char c, short mode) {
      switch(mode) {
        case COMMENT_AFTER_1_DASH:
          if (c=='-') {
            record=record && reader.commentStart();
            return COMMENT_TEXT;
          }
          record=record && reader.commentStart();
          return parseComment(c, COMMENT_TEXT);

        case COMMENT_TEXT:
          if (c=='-') return COMMENT_CLOSE_AFTER_1_DASH;
          record=record && reader.comment(c);
          return COMMENT_TEXT;

        case COMMENT_CLOSE_AFTER_1_DASH:
          if (c=='-') return COMMENT_CLOSE_AFTER_2_DASH;
          record=record && reader.comment('-') && reader.comment(c);
          return COMMENT_TEXT;

        case COMMENT_CLOSE_AFTER_2_DASH:
          if (c=='>') {
            record=record && reader.commentComplete();
            return tagCompleteCleanStart(false);
          }
          record=record && reader.comment('-') && reader.comment('-') && reader.comment(c);
          return COMMENT_TEXT;

        default:
          throw new RuntimeException("Unexpected: "+mode);
      }
    }

    short parseCData(char c, short mode) {
      switch (mode) {
        case CDATA_AFTER_1_BRACK:
          if (c=='C' || c=='c') return CDATA_AFTER_C;
          return tagNameCompleteAndGarbaged();
        case CDATA_AFTER_C:
          if (c=='D' || c=='d') return CDATA_AFTER_D;
          return tagNameCompleteAndGarbaged();
        case CDATA_AFTER_D:
          if (c=='A' || c=='a') return CDATA_AFTER_A;
          return tagNameCompleteAndGarbaged();
        case CDATA_AFTER_A:
          if (c=='T' || c=='t') return CDATA_AFTER_T;
          return tagNameCompleteAndGarbaged();
        case CDATA_AFTER_T:
          if (c=='A' || c=='a') return CDATA_AFTER_A2;
          return tagNameCompleteAndGarbaged();
        case CDATA_AFTER_A2:
          if (c=='[') {
            record=record && reader.cdataStart();
            return CDATA_TEXT;
          }
          return tagNameCompleteAndGarbaged();

        case CDATA_TEXT:
          if (c==']') return CDATA_AFTER_CLOSE_BRACK_1;
          reader.cdata(c);
          return CDATA_TEXT;

        case CDATA_AFTER_CLOSE_BRACK_1:
          if (c==']') return CDATA_AFTER_CLOSE_BRACK_2;
          record=record && reader.cdata(']') && reader.cdata(c);
          return CDATA_TEXT;
        case CDATA_AFTER_CLOSE_BRACK_2:
          if (c=='>') {
            record=record && reader.cdataComplete();
            return tagCompleteCleanStart(false);
          }
          record=record && reader.cdata(']') && reader.cdata(']') && reader.cdata(c);
          return CDATA_TEXT;
        default:
          throw new RuntimeException("Unexpected: "+mode);
      }
    }
  }
}
