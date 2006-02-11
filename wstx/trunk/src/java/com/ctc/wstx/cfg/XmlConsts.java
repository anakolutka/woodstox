package com.ctc.wstx.cfg;

/**
 * Simple constant container interface, shared by input and output
 * sides.
 */
public interface XmlConsts
{
    // // // Constants for XML declaration

    public final static String XML_DECL_KW_ENCODING = "encoding";
    public final static String XML_DECL_KW_VERSION = "version";
    public final static String XML_DECL_KW_STANDALONE = "standalone";

    public final static String XML_V_10 = "1.0";
    public final static String XML_V_11 = "1.1";

    public final static String XML_SA_YES = "yes";
    public final static String XML_SA_NO = "no";

    // // // Well, these are not strictly xml constants, but for
    // // // now can live here

    /**
     * This constant defines the highest Unicode character allowed
     * in XML content.
     */
    final static int MAX_UNICODE_CHAR = 0x10FFFF;

}