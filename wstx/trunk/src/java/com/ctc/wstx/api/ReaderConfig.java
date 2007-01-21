package com.ctc.wstx.api;

import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLInputFactory2; // for property consts
import org.codehaus.stax2.XMLStreamProperties; // for property consts

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.InputConfigFlags;
import com.ctc.wstx.dtd.DTDEventListener;
import com.ctc.wstx.ent.IntEntity;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.io.BufferRecycler;
import com.ctc.wstx.util.ArgUtil;
import com.ctc.wstx.util.EmptyIterator;
import com.ctc.wstx.util.SymbolTable;

/**
 * Simple configuration container class; passed by reader factory to reader
 * instance created.
 *<p>
 * In addition to its main task as a configuration container, this class
 * also acts as a wrapper around simple buffer recycling functionality.
 * The reason is that while conceptually this is a separate concern,
 * there are enough commonalities with the life-cycle of this object to
 * make this a very convenience place to add that functionality...
 * (that is: conceptually this is not right, but from pragmatic viewpoint
 * it just makes sense)
 */
public final class ReaderConfig
    extends CommonConfig
    implements InputConfigFlags
{
    /*
    ////////////////////////////////////////////////
    // Constants for reader properties:
    ////////////////////////////////////////////////
    */

    // // First, standard StAX properties:

    // Simple flags:
    final static int PROP_COALESCE_TEXT = 1;
    final static int PROP_NAMESPACE_AWARE = 2;
    final static int PROP_REPLACE_ENTITY_REFS = 3;
    final static int PROP_SUPPORT_EXTERNAL_ENTITIES = 4;
    final static int PROP_VALIDATE_AGAINST_DTD = 5;
    final static int PROP_SUPPORT_DTD = 6;

    // Object type properties
    public final static int PROP_EVENT_ALLOCATOR = 7;
    final static int PROP_WARNING_REPORTER = 8;
    final static int PROP_XML_RESOLVER = 9;

    // // Then StAX2 standard properties:

    // Simple flags:
    final static int PROP_INTERN_NS_URIS = 20;
    final static int PROP_INTERN_NAMES = 21;
    final static int PROP_REPORT_CDATA = 22;
    final static int PROP_REPORT_PROLOG_WS = 23;
    final static int PROP_PRESERVE_LOCATION = 24;
    final static int PROP_AUTO_CLOSE_INPUT = 25;
    final static int PROP_SUPPORT_XMLID = 26;

    // // // Constants for additional Wstx properties:

    // Simple flags:

    // Note: these were included pre-4.0, deprecated:
    //final static int PROP_NORMALIZE_LFS = 40;
    //final static int PROP_NORMALIZE_ATTR_VALUES = 41;

    final static int PROP_CACHE_DTDS = 42;
    final static int PROP_CACHE_DTDS_BY_PUBLIC_ID = 43;
    final static int PROP_LAZY_PARSING = 44;
    final static int PROP_SUPPORT_DTDPP = 45;

    // Object type properties:

    final static int PROP_INPUT_BUFFER_LENGTH = 50;
    //final static int PROP_TEXT_BUFFER_LENGTH = 51;
    final static int PROP_MIN_TEXT_SEGMENT = 52;
    final static int PROP_CUSTOM_INTERNAL_ENTITIES = 53;
    final static int PROP_DTD_RESOLVER = 54;
    final static int PROP_ENTITY_RESOLVER = 55;
    final static int PROP_UNDECLARED_ENTITY_RESOLVER = 56;
    final static int PROP_BASE_URL = 57;
    final static int PROP_INPUT_PARSING_MODE = 58;

    /*
    ////////////////////////////////////////////////
    // Limits for numeric properties
    ////////////////////////////////////////////////
    */

    /**
     * Need to set a minimum size, since there are some limitations to
     * smallest consequtive block that can be used.
     */
    final static int MIN_INPUT_BUFFER_LENGTH = 8; // 16 bytes

    /**
     * Let's allow caching of just a dozen DTDs... shouldn't really
     * matter, how many DTDs does one really use?
     */
    final static int DTD_CACHE_SIZE_J2SE = 12;

    final static int DTD_CACHE_SIZE_J2ME = 5;

    /*
    ////////////////////////////////////////////////
    // Default values for custom properties:
    ////////////////////////////////////////////////
    */

    /**
     * By default, let's require minimum of 64 chars to be delivered
     * as shortest partial (piece of) text (CDATA, text) segment;
     * same for both J2ME subset and full readers. Prevents tiniest
     * runts from getting passed
     */
    final static int DEFAULT_SHORTEST_TEXT_SEGMENT = 64;

    /**
     * Default config flags are converted from individual settings,
     * to conform to StAX 1.0 specifications.
     */
    final static int DEFAULT_FLAGS_FULL =
        // First, default settings StAX specs dictate:
        CFG_COALESCE_TEXT
        | CFG_NAMESPACE_AWARE
        | CFG_REPLACE_ENTITY_REFS
        | CFG_SUPPORT_EXTERNAL_ENTITIES
        | CFG_SUPPORT_DTD

        // and then custom setting defaults:

        // and namespace URI interning
        | CFG_INTERN_NS_URIS

        // we will also accurately report CDATA, by default
        | CFG_REPORT_CDATA

        /* 20-Jan-2006, TSa: As per discussions on stax-builders list
         *   (and input from xml experts), 4.0 will revert to "do not
         *   report SPACE events outside root element by default"
         *   settings. Conceptually this is what xml specification
         *   implies should be done: there is no content outside of
         *   the element tree, including any ignorable content, just
         *   processing instructions and comments.
         */
        //| CFG_REPORT_PROLOG_WS

        /* but enable DTD caching (if they are handled):
         * (... maybe J2ME subset shouldn't do it?)
         */
        | CFG_CACHE_DTDS
        /* 29-Mar-2006, TSa: But note, no caching by public-id, due
         *   to problems with cases where public-id/system-id were
         *   inconsistently used, leading to problems.
         */

        /* by default, let's also allow lazy parsing, since it tends
         * to improve performance
         */
        | CFG_LAZY_PARSING

        /* and also make Event objects preserve location info...
         * can be turned off for maximum performance
         */
        | CFG_PRESERVE_LOCATION
        /* Also, let's enable dtd++ support (shouldn't hurt with non-dtd++
         * dtds)
         */

        | CFG_SUPPORT_DTDPP

        /* Regarding Xml:id, let's enabled typing by default, but not
         * uniqueness validity checks: latter will be taken care of
         * by DTD validation if enabled, otherwise needs to be explicitly
         * enabled
         */
        | CFG_XMLID_TYPING
        // | CFG_XMLID_UNIQ_CHECKS
        ;

    /**
     * For now defaults for J2ME flags can be identical to 'full' set;
     * differences are in buffer sizes.
     */
    final static int DEFAULT_FLAGS_J2ME = DEFAULT_FLAGS_FULL;

    // // //

    /**
     * Map to use for converting from String property ids to ints
     * described above; useful to allow use of switch later on.
     */
    final static HashMap sProperties = new HashMap(64); // we have about 40 entries
    static {
        // Standard ones; support for features
        sProperties.put(XMLInputFactory.IS_COALESCING,
                        new Integer(PROP_COALESCE_TEXT));
        sProperties.put(XMLInputFactory.IS_NAMESPACE_AWARE,
                        new Integer(PROP_NAMESPACE_AWARE));
        sProperties.put(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,
                    new Integer(PROP_REPLACE_ENTITY_REFS));
        sProperties.put(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                    new Integer(PROP_SUPPORT_EXTERNAL_ENTITIES));
        sProperties.put(XMLInputFactory.IS_VALIDATING,
                        new Integer(PROP_VALIDATE_AGAINST_DTD));
        sProperties.put(XMLInputFactory.SUPPORT_DTD,
                        new Integer(PROP_SUPPORT_DTD));

        // Standard ones; pluggable components
        sProperties.put(XMLInputFactory.ALLOCATOR,
                        new Integer(PROP_EVENT_ALLOCATOR));
        sProperties.put(XMLInputFactory.REPORTER,
                        new Integer(PROP_WARNING_REPORTER));
        sProperties.put(XMLInputFactory.RESOLVER,
                        new Integer(PROP_XML_RESOLVER));

        // StAX2-introduced flags:
        sProperties.put(XMLInputFactory2.P_INTERN_NAMES,
                        new Integer(PROP_INTERN_NAMES));
        sProperties.put(XMLInputFactory2.P_INTERN_NS_URIS,
                        new Integer(PROP_INTERN_NS_URIS));
        sProperties.put(XMLInputFactory2.P_REPORT_CDATA,
                        new Integer(PROP_REPORT_CDATA));
        sProperties.put(XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE,
                        new Integer(PROP_REPORT_PROLOG_WS));
        sProperties.put(XMLInputFactory2.P_PRESERVE_LOCATION,
                        new Integer(PROP_PRESERVE_LOCATION));
        sProperties.put(XMLInputFactory2.P_AUTO_CLOSE_INPUT,
                        new Integer(PROP_AUTO_CLOSE_INPUT));
        sProperties.put(XMLInputFactory2.XSP_SUPPORT_XMLID,
                        new Integer(PROP_SUPPORT_XMLID));

        // Non-standard ones, flags:

        sProperties.put(WstxInputProperties.P_CACHE_DTDS,
                        new Integer(PROP_CACHE_DTDS));
        sProperties.put(WstxInputProperties.P_CACHE_DTDS_BY_PUBLIC_ID,
                        new Integer(PROP_CACHE_DTDS_BY_PUBLIC_ID));
        sProperties.put(WstxInputProperties.P_LAZY_PARSING,
                        new Integer(PROP_LAZY_PARSING));
        sProperties.put(WstxInputProperties.P_SUPPORT_DTDPP,
                        new Integer(PROP_SUPPORT_DTDPP));

        // Non-standard ones, non-flags:

        sProperties.put(WstxInputProperties.P_INPUT_BUFFER_LENGTH,
                        new Integer(PROP_INPUT_BUFFER_LENGTH));
        sProperties.put(WstxInputProperties.P_MIN_TEXT_SEGMENT,
                        new Integer(PROP_MIN_TEXT_SEGMENT));
        sProperties.put(WstxInputProperties.P_CUSTOM_INTERNAL_ENTITIES,
                        new Integer(PROP_CUSTOM_INTERNAL_ENTITIES));
        sProperties.put(WstxInputProperties.P_DTD_RESOLVER,
                        new Integer(PROP_DTD_RESOLVER));
        sProperties.put(WstxInputProperties.P_ENTITY_RESOLVER,
                        new Integer(PROP_ENTITY_RESOLVER));
        sProperties.put(WstxInputProperties.P_UNDECLARED_ENTITY_RESOLVER,
                        new Integer(PROP_UNDECLARED_ENTITY_RESOLVER));
        sProperties.put(WstxInputProperties.P_BASE_URL,
                        new Integer(PROP_BASE_URL));
        sProperties.put(WstxInputProperties.P_INPUT_PARSING_MODE,
                        new Integer(PROP_INPUT_PARSING_MODE));
    }

    /*
    //////////////////////////////////////////////////////////
    // Current config state:
    //////////////////////////////////////////////////////////
     */

    final boolean mIsJ2MESubset;

    final SymbolTable mSymbols;

    int mConfigFlags;

    int mInputBufferLen;
    int mMinTextSegmentLen;

    /**
     * Base URL to use as the resolution context for relative entity
     * references
     */
    URL mBaseURL = null;

    /**
     * Parsing mode can be changed from the default xml compliant
     * behavior to one of alternate modes (fragment processing,
     * multiple document processing).
     */
    WstxInputProperties.ParsingMode mParsingMode =
        WstxInputProperties.PARSING_MODE_DOCUMENT;

    /**
     * This boolean flag is set if the input document requires
     * xml 1.1 (or above) compliant processing: default is xml 1.0
     * compliant. Note that unlike most other properties, this
     * does not come from configuration settings, but from processed
     * document itself.
     */
    boolean mXml11 = false;

    /*
    //////////////////////////////////////////////////////////
    // Common configuration objects
    //////////////////////////////////////////////////////////
     */

    XMLReporter mReporter;

    XMLResolver mDtdResolver = null;
    XMLResolver mEntityResolver = null;

    /*
    //////////////////////////////////////////////////////////
    // More special(ized) configuration objects
    //////////////////////////////////////////////////////////
     */

    //Map mCustomEntities;
    //XMLResolver mUndeclaredEntityResolver;
    //DTDEventListener mDTDEventListener;

    Object[] mSpecialProperties = null;

    private final static int SPEC_PROC_COUNT = 3;

    private final static int SP_IX_CUSTOM_ENTITIES = 0;
    private final static int SP_IX_UNDECL_ENT_RESOLVER = 1;
    private final static int SP_IX_DTD_EVENT_LISTENER = 2;

    /*
    //////////////////////////////////////////////////////////
    // Buffer recycling:
    //////////////////////////////////////////////////////////
     */

    /**
     * This <code>ThreadLocal</code> contains a {@link SoftRerefence}
     * to a {@link BufferRecycler} used to provide a low-cost
     * buffer recycling between Reader instances.
     */
    final static ThreadLocal mRecyclerRef = new ThreadLocal();

    /**
     * This is the actually container of the recyclable buffers. It
     * is obtained via ThreadLocal/SoftReference combination, if one
     * exists, when Config instance is created. If one does not
     * exist, it will created first time a buffer is returned.
     */
    BufferRecycler mCurrRecycler = null;

    /*
    //////////////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////////////
     */

    private ReaderConfig(boolean j2meSubset, SymbolTable symbols,
                         int configFlags,
                         int inputBufLen,
                         int minTextSegmentLen)
    {
        mIsJ2MESubset = j2meSubset;
        mSymbols = symbols;

        mConfigFlags = configFlags;

        mInputBufferLen = inputBufLen;
        mMinTextSegmentLen = minTextSegmentLen;

        /* Ok, let's then see if we can find a buffer recycler. Since they
         * are lazily constructed, and since GC may just flush them out
         * on its whims, it's possible we might not find one. That's ok;
         * we can reconstruct one if and when we are to return one or more
         * buffers.
         */
        SoftReference ref = (SoftReference) mRecyclerRef.get();
        if (ref != null) {
            mCurrRecycler = (BufferRecycler) ref.get();
        }
    }

    public static ReaderConfig createJ2MEDefaults()
    {
        /* For J2ME we'll use slightly smaller buffer sizes by
         * default, on assumption lower memory usage is desireable:
         */
        ReaderConfig rc = new ReaderConfig
            (true, null, DEFAULT_FLAGS_J2ME,
             // 4k input buffer (2000 chars):
             2000,
             DEFAULT_SHORTEST_TEXT_SEGMENT);
        return rc;
    }

    public static ReaderConfig createFullDefaults()
    {
        /* For full version, can use bit larger buffers to achieve better
         * overall performance.
         */
        ReaderConfig rc = new ReaderConfig
            (false, null, DEFAULT_FLAGS_FULL,
             // 8k input buffer (4000 chars):
             4000,
             DEFAULT_SHORTEST_TEXT_SEGMENT);
        return rc;
    }

    public ReaderConfig createNonShared(SymbolTable sym)
    {
        // should we throw an exception?
        //if (sym == null) { }
        ReaderConfig rc = new ReaderConfig(mIsJ2MESubset, sym,
                                           mConfigFlags,
                                           mInputBufferLen,
                                           mMinTextSegmentLen);
        rc.mReporter = mReporter;
        rc.mDtdResolver = mDtdResolver;
        rc.mEntityResolver = mEntityResolver;
        rc.mBaseURL = mBaseURL;
        rc.mParsingMode = mParsingMode;
        if (mSpecialProperties != null) {
            int len = mSpecialProperties.length;
            Object[] specProps = new Object[len];
            System.arraycopy(mSpecialProperties, 0, specProps, 0, len);
            rc.mSpecialProperties = specProps;
        }

        return rc;
    }

    /**
     * Unlike name suggests there is also some limited state information
     * associated with the config object. If these objects are reused,
     * that state needs to be reset between reuses, to avoid carrying
     * over incorrect state.
     */
    public void resetState()
    {
        // Current, only xml 1.0 vs 1.1 state is stored here:
        mXml11 = false;
    }

    /*
    //////////////////////////////////////////////////////////
    // Implementation of abstract methods
    //////////////////////////////////////////////////////////
     */

    protected int findPropertyId(String propName)
    {
        Integer I = (Integer) sProperties.get(propName);
        return (I == null) ? -1 : I.intValue();
    }
 
    /*
    //////////////////////////////////////////////////////////
    // Public API, accessors
    //////////////////////////////////////////////////////////
     */

    // // // Accessors for immutable configuration:

    public SymbolTable getSymbols() { return mSymbols; }

    /**
     * In future this property could/should be made configurable?
     */

    public int getDtdCacheSize() {
        return mIsJ2MESubset ? DTD_CACHE_SIZE_J2ME : DTD_CACHE_SIZE_J2SE;
    }

    // // // "Raw" accessors for on/off properties:

    public int getConfigFlags() { return mConfigFlags; }
    public boolean hasConfigFlags(int flags) {
        return (mConfigFlags & flags) == flags;
    }

    // // // Standard StAX on/off property accessors

    public boolean willCoalesceText() {
        return hasConfigFlags(CFG_COALESCE_TEXT);
    }

    public boolean willSupportNamespaces() {
        return hasConfigFlags(CFG_NAMESPACE_AWARE);
    }

    public boolean willReplaceEntityRefs() {
        return hasConfigFlags(CFG_REPLACE_ENTITY_REFS);
    }

    public boolean willSupportExternalEntities() {
        return hasConfigFlags(CFG_SUPPORT_EXTERNAL_ENTITIES);
    }

    public boolean willSupportDTDs() {
        return hasConfigFlags(CFG_SUPPORT_DTD);
    }

    public boolean willValidateWithDTD() {
        return hasConfigFlags(CFG_VALIDATE_AGAINST_DTD);
    }

    // // // Woodstox on/off property accessors

    public boolean willInternNames() {
	// 17-Apr-2005, TSa: NOP, we'll always intern them...
        return true;
    }

    public boolean willInternNsURIs() {
        return hasConfigFlags(CFG_INTERN_NS_URIS);
    }

    public boolean willReportCData() {
        return hasConfigFlags(CFG_REPORT_CDATA);
    }

    public boolean willReportPrologWhitespace() {
        return hasConfigFlags(CFG_REPORT_PROLOG_WS);
    }

    public boolean willCacheDTDs() {
        return hasConfigFlags(CFG_CACHE_DTDS);
    }

    public boolean willCacheDTDsByPublicId() {
        return hasConfigFlags(CFG_CACHE_DTDS_BY_PUBLIC_ID);
    }

    public boolean willParseLazily() {
        return hasConfigFlags(CFG_LAZY_PARSING);
    }

    public boolean willDoXmlIdTyping() {
        return hasConfigFlags(CFG_XMLID_TYPING);
    }

    public boolean willDoXmlIdUniqChecks() {
        return hasConfigFlags(CFG_XMLID_UNIQ_CHECKS);
    }

    public boolean willPreserveLocation() {
        return hasConfigFlags(CFG_PRESERVE_LOCATION);
    }

    public boolean willAutoCloseInput() {
        return hasConfigFlags(CFG_AUTO_CLOSE_INPUT);
    }

    public boolean willSupportDTDPP() {
        return hasConfigFlags(CFG_SUPPORT_DTDPP);
    }

    public int getInputBufferLength() { return mInputBufferLen; }

    public int getShortestReportedTextSegment() { return mMinTextSegmentLen; }

    public Map getCustomInternalEntities()
    {
        Map custEnt = (Map) getSpecialProperty(SP_IX_CUSTOM_ENTITIES);
        if (custEnt == null) {
            return Collections.EMPTY_MAP;
        }
        // Better be defensive and just return a copy...
        int len = custEnt.size();
        HashMap m = new HashMap(len + (len >> 2), 0.81f);
        Iterator it = custEnt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry me = (Map.Entry) it.next();
            /* Cast is there just as a safe-guard (assertion), and to
             * document the type...
             */
            m.put(me.getKey(), (EntityDecl) me.getValue());
        }
        return m;
    }

    public XMLReporter getXMLReporter() { return mReporter; }

    public XMLResolver getXMLResolver() { return mEntityResolver; }

    public XMLResolver getDtdResolver() { return mDtdResolver; }
    public XMLResolver getEntityResolver() { return mEntityResolver; }
    public XMLResolver getUndeclaredEntityResolver() {
        return (XMLResolver) getSpecialProperty(SP_IX_UNDECL_ENT_RESOLVER);
    }

    public URL getBaseURL() { return mBaseURL; }

    public WstxInputProperties.ParsingMode getInputParsingMode() {
        return mParsingMode;
    }

    public boolean inputParsingModeDocuments() {
        return mParsingMode == WstxInputProperties.PARSING_MODE_DOCUMENTS;
    }

    public boolean inputParsingModeFragment() {
        return mParsingMode == WstxInputProperties.PARSING_MODE_FRAGMENT;
    }

    /**
     * @return True if the input well-formedness and validation checks
     *    should be done according to xml 1.1 specification; false if
     *    xml 1.0 specification.
     */
    public boolean isXml11() {
        return mXml11;
    }

    public DTDEventListener getDTDEventListener() {
        return (DTDEventListener) getSpecialProperty(SP_IX_DTD_EVENT_LISTENER);
    }

    /*
    //////////////////////////////////////////////////////////
    // Simple mutators
    //////////////////////////////////////////////////////////
     */

    public void setConfigFlags(int flags) {
        mConfigFlags = flags;
    }

    public void setConfigFlag(int flag) {
        mConfigFlags |= flag;
    }

    public void clearConfigFlag(int flag) {
        mConfigFlags &= ~flag;
    }

    // // // Mutators for standard StAX properties

    public void doCoalesceText(boolean state) {
        setConfigFlag(CFG_COALESCE_TEXT, state);
    }

    public void doSupportNamespaces(boolean state) {
        setConfigFlag(CFG_NAMESPACE_AWARE, state);
    }

    public void doReplaceEntityRefs(boolean state) {
        setConfigFlag(CFG_REPLACE_ENTITY_REFS, state);
    }

    public void doSupportExternalEntities(boolean state) {
        setConfigFlag(CFG_SUPPORT_EXTERNAL_ENTITIES, state);
    }

    public void doSupportDTDs(boolean state) {
        setConfigFlag(CFG_SUPPORT_DTD, state);
    }

    public void doValidateWithDTD(boolean state) {
        setConfigFlag(CFG_VALIDATE_AGAINST_DTD, state);
    }

    // // // Mutators for Woodstox-specific properties

    public void doInternNames(boolean state) {
        // 17-Apr-2005, TSa: NOP, we'll always intern them...
    }

    public void doInternNsURIs(boolean state) {
        setConfigFlag(CFG_INTERN_NS_URIS, state);
    }

    public void doReportPrologWhitespace(boolean state) {
        setConfigFlag(CFG_REPORT_PROLOG_WS, state);
    }

    public void doReportCData(boolean state) {
        setConfigFlag(CFG_REPORT_CDATA, state);
    }

    public void doCacheDTDs(boolean state) {
        setConfigFlag(CFG_CACHE_DTDS, state);
    }

    public void doCacheDTDsByPublicId(boolean state) {
        setConfigFlag(CFG_CACHE_DTDS_BY_PUBLIC_ID, state);
    }

    public void doParseLazily(boolean state) {
        setConfigFlag(CFG_LAZY_PARSING, state);
    }

    public void doXmlIdTyping(boolean state) {
        setConfigFlag(CFG_XMLID_TYPING, state);
    }

    public void doXmlIdUniqChecks(boolean state) {
        setConfigFlag(CFG_XMLID_UNIQ_CHECKS, state);
    }

    public void doPreserveLocation(boolean state) {
        setConfigFlag(CFG_PRESERVE_LOCATION, state);
    }

    public void doAutoCloseInput(boolean state) {
        setConfigFlag(CFG_AUTO_CLOSE_INPUT, state);
    }

    public void doSupportDTDPP(boolean state) {
        setConfigFlag(CFG_SUPPORT_DTDPP, state);
    }

    public void setInputBufferLength(int value)
    {
        /* Let's enforce minimum here; necessary to allow longest
         * consequtive text span to be available (xml decl, etc)
         */
        if (value < MIN_INPUT_BUFFER_LENGTH) {
            value = MIN_INPUT_BUFFER_LENGTH;
        }
        mInputBufferLen = value;
    }

    public void setShortestReportedTextSegment(int value) {
        mMinTextSegmentLen = value;
    }

    public void setCustomInternalEntities(Map m)
    {
        Map entMap;
        if (m == null || m.size() < 1) {
            entMap = Collections.EMPTY_MAP;
        } else {
            int len = m.size();
            entMap = new HashMap(len + (len >> 1), 0.75f);
            Iterator it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry me = (Map.Entry) it.next();
                Object val = me.getValue();
                char[] ch;
                if (val == null) {
                    ch = EmptyIterator.getEmptyCharArray();
                } else if (val instanceof char[]) {
                    ch = (char[]) val;
                } else {
                    // Probably String, but let's just ensure that
                    String str = val.toString();
                    ch = str.toCharArray();
                }
                String name = (String) me.getKey();
                entMap.put(name, IntEntity.create(name, ch));
            }
        }
        setSpecialProperty(SP_IX_CUSTOM_ENTITIES, entMap);
    }

    public void setXMLReporter(XMLReporter r) {
        mReporter = r;
    }

    /**
     * Note: for better granularity, you should call {@link #setEntityResolver}
     * and {@link #setDtdResolver} instead.
     */
    public void setXMLResolver(XMLResolver r) {
        mEntityResolver = r;
        mDtdResolver = r;
    }

    public void setDtdResolver(XMLResolver r) {
        mDtdResolver = r;
    }

    public void setEntityResolver(XMLResolver r) {
        mEntityResolver = r;
    }

    public void setUndeclaredEntityResolver(XMLResolver r) {
        setSpecialProperty(SP_IX_UNDECL_ENT_RESOLVER, r);
    }

    public void setBaseURL(URL baseURL) { mBaseURL = baseURL; }

    public void setInputParsingMode(WstxInputProperties.ParsingMode mode) {
        mParsingMode = mode;
    }

    /**
     * Method called to enable or disable 1.1 compliant processing; if
     * disabled, defaults to xml 1.0 compliant processing.
     */
    public void enableXml11(boolean state) {
        mXml11 = state;
    }

    public void setDTDEventListener(DTDEventListener l) {
        setSpecialProperty(SP_IX_DTD_EVENT_LISTENER, l);
    }

    /*
    /////////////////////////////////////////////////////
    // Profile mutators:
    /////////////////////////////////////////////////////
     */

    /**
     * Method to call to make Reader created conform as closely to XML
     * standard as possible, doing all checks and transformations mandated
     * (linefeed conversions, attr value normalizations).
     * See {@link XMLInputFactory2#configureForXmlConformance} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <b>None</b>.
     *</ul>
     *<p>
     * Notes: Does NOT change 'performance' settings (buffer sizes,
     * DTD caching, coalescing, interning, accurate location info).
     */
    public void configureForXmlConformance()
    {
        // // StAX 1.0 settings
        doSupportNamespaces(true);
        doSupportDTDs(true);
        doSupportExternalEntities(true);
        doReplaceEntityRefs(true);

        // // Stax2 additional settings

        // Better enable full xml:id checks:
        doXmlIdTyping(true);
        doXmlIdUniqChecks(true);

        // Woodstox-specific ones:
    }

    /**
     * Method to call to make Reader created be as "convenient" to use
     * as possible; ie try to avoid having to deal with some of things
     * like segmented text chunks. This may incur some slight performance
     * penalties, but should not affect XML conformance.
     * See {@link XMLInputFactory2#configureForConvenience} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     *  <li>Disable <code>P_LAZY_PARSING</code> (to allow for synchronous
     *    error notification by forcing full XML events to be completely
     *    parsed when reader's <code>next() is called)
     * </li>
     *</ul>
     */
    public void configureForConvenience()
    {
        // StAX (1.0) settings:
        doCoalesceText(true);
        doReplaceEntityRefs(true);

        // StAX2: 
        doReportCData(false);
        doReportPrologWhitespace(false);
        /* Also, knowing exact locations is nice esp. for error
         * reporting purposes
         */
        doPreserveLocation(true);

        // Woodstox-specific:

        /* Also, we can force errors to be reported in timely manner:
         * (once again, at potential expense of performance)
         */
        doParseLazily(false);
    }

    /**
     * Method to call to make the Reader created be as fast as possible reading
     * documents, especially for long-running processes where caching is
     * likely to help.
     *
     * See {@link XMLInputFactory2#configureForSpeed} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Enable <code>P_CACHE_DTDS</code>.
     *  </li>
     * <li>Enable <code>P_LAZY_PARSING</code> (can improve performance
     *   especially when skipping text segments)
     *  </li>
     * <li>Disable Xml:id uniqueness checks (and leave typing as is)
     *  </li>
     * <li>Set lowish value for <code>P_MIN_TEXT_SEGMENT</code>, to allow
     *   reader to optimize segment length it uses (and possibly avoids
     *   one copy operation in the process)
     *  </li>
     * <li>Increase <code>P_INPUT_BUFFER_LENGTH</code> a bit from default,
     *   to allow for longer consequtive read operations; also reduces cases
     *   where partial text segments are on input buffer boundaries.
     *  </li>
     *</ul>
     */
    public void configureForSpeed()
    {
        // StAX (1.0):
        doCoalesceText(false);

        // StAX2:
        doPreserveLocation(false);
        doReportPrologWhitespace(false);
        //doInternNames(true); // this is a NOP
        doInternNsURIs(true);
        doXmlIdUniqChecks(false);

        // Woodstox-specific:
        doCacheDTDs(true);
        doParseLazily(true);

        /* If we let Reader decide sizes of text segments, it should be
         * able to optimize it better, thus low min value. This value
         * is only used in cases where text is at buffer boundary, or
         * where entity prevents using consequtive chars from input buffer:
         */
        setShortestReportedTextSegment(16);
        setInputBufferLength(8000); // 16k input buffer
    }

    /**
     * Method to call to minimize the memory usage of the stream/event reader;
     * both regarding Objects created, and the temporary memory usage during
     * parsing.
     * This generally incurs some performance penalties, due to using
     * smaller input buffers.
     *<p>
     * See {@link XMLInputFactory2#configureForLowMemUsage} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Disable <code>P_CACHE_DTDS</code>
     *  </li>
     * <li>Enable <code>P_PARSE_LAZILY</code>
     *  </li>
     * <li>Resets <code>P_MIN_TEXT_SEGMENT</code> to the (somewhat low)
     *   default value.
     *  <li>
     * <li>Reduces <code>P_INPUT_BUFFER_LENGTH</code> a bit from the default
     *  <li>
     *</ul>
     */
    public void configureForLowMemUsage()
    {
        // StAX (1.0)
        doCoalesceText(false);

        // StAX2:

        doPreserveLocation(false); // can reduce temporary mem usage

        // Woodstox-specific:
        doCacheDTDs(false);
        doParseLazily(true); // can reduce temporary mem usage
        doXmlIdUniqChecks(false); // enabling would increase mem usage
        setShortestReportedTextSegment(ReaderConfig.DEFAULT_SHORTEST_TEXT_SEGMENT);
        setInputBufferLength(512); // 1k input buffer
        // Text buffer need not be huge, as we do not coalesce
    }
    
    /**
     * Method to call to make Reader try to preserve as much of input
     * formatting as possible, so that round-tripping would be as lossless
     * as possible.
     *<p>
     * See {@link XMLInputFactory2#configureForLowMemUsage} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Increases <code>P_MIN_TEXT_SEGMENT</code> to the maximum value so
     *    that all original text segment chunks are reported without
     *    segmentation (but without coalescing with adjacent CDATA segments)
     *  <li>
     *</ul>
     */
    public void configureForRoundTripping()
    {
        // StAX (1.0)
        doCoalesceText(false);
        doReplaceEntityRefs(false);
        
        // StAX2:
        doReportCData(true);
        doReportPrologWhitespace(true);
        
        // Woodstox specific settings

        // effectively prevents from reporting partial segments:
        setShortestReportedTextSegment(Integer.MAX_VALUE);
    }

    /*
    /////////////////////////////////////////////////////
    // Buffer recycling:
    /////////////////////////////////////////////////////
     */

    public char[] allocSmallCBuffer(int minSize)
    {
//System.err.println("DEBUG: cfg, allocCSmall: "+mCurrRecycler);
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getSmallCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        // Nope; no recycler, or it has no suitable buffers, let's create:
        return new char[minSize];
    }

    public void freeSmallCBuffer(char[] buffer)
    {
//System.err.println("DEBUG: cfg, freeCSmall: "+buffer);
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnSmallCBuffer(buffer);
    }

    public char[] allocMediumCBuffer(int minSize)
    {
//System.err.println("DEBUG: cfg, allocCMed: "+mCurrRecycler);
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getMediumCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new char[minSize];
    }

    public void freeMediumCBuffer(char[] buffer)
    {
//System.err.println("DEBUG: cfg, freeCMed: "+buffer);
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnMediumCBuffer(buffer);
    }

    public char[] allocFullCBuffer(int minSize)
    {
//System.err.println("DEBUG: cfg, allocCFull: "+mCurrRecycler);
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getFullCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new char[minSize];
    }

    public void freeFullCBuffer(char[] buffer)
    {
//System.err.println("DEBUG: cfg, freeCFull: "+buffer);
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnFullCBuffer(buffer);
    }

    public byte[] allocFullBBuffer(int minSize)
    {
//System.err.println("DEBUG: cfg, allocBFull: "+mCurrRecycler);
        if (mCurrRecycler != null) {
            byte[] result = mCurrRecycler.getFullBBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new byte[minSize];
    }

    public void freeFullBBuffer(byte[] buffer)
    {
//System.err.println("DEBUG: cfg, freeBFull: "+buffer);
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnFullBBuffer(buffer);
    }

    static int Counter = 0;

    private BufferRecycler createRecycler()
    {
        BufferRecycler recycler = new BufferRecycler();
        // No way to reuse/reset SoftReference, have to create new always:
//System.err.println("DEBUG: RefCount: "+(++Counter));
        mRecyclerRef.set(new SoftReference(recycler));
        return recycler;
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods:
    /////////////////////////////////////////////////////
     */

    private void setConfigFlag(int flag, boolean state) {
        if (state) {
            mConfigFlags |= flag;
        } else {
            mConfigFlags &= ~flag;
        }
    }

    public Object getProperty(int id)
    {
        switch (id) {
            // First, standard Stax 1.0 properties:

        case PROP_COALESCE_TEXT:
            return willCoalesceText() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_NAMESPACE_AWARE:
            return willSupportNamespaces() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_REPLACE_ENTITY_REFS:
            return willReplaceEntityRefs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_EXTERNAL_ENTITIES:
            return willSupportExternalEntities() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_VALIDATE_AGAINST_DTD:
            return willValidateWithDTD() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_DTD:
            return willSupportDTDs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_WARNING_REPORTER:
            return getXMLReporter();
        case PROP_XML_RESOLVER:
            return getXMLResolver();
        case PROP_EVENT_ALLOCATOR:
            /* 25-Mar-2006, TSa: Not really supported here, so let's
             *   return null
             */
            return null;

        // Then Stax2 properties:

        case PROP_REPORT_PROLOG_WS:
            return willReportPrologWhitespace() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_REPORT_CDATA:
            return willReportCData() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_INTERN_NAMES:
            return willInternNames() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_INTERN_NS_URIS:
            return willInternNsURIs() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_PRESERVE_LOCATION:
            return willPreserveLocation() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_AUTO_CLOSE_INPUT:
            return willAutoCloseInput() ? Boolean.TRUE : Boolean.FALSE;

        // // // Then Woodstox custom properties:

            // first, flags:
        case PROP_CACHE_DTDS:
            return willCacheDTDs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_CACHE_DTDS_BY_PUBLIC_ID:
            return willCacheDTDsByPublicId() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_LAZY_PARSING:
            return willParseLazily() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_XMLID:
            {
                if (!hasConfigFlags(CFG_XMLID_TYPING)) {
                    return XMLStreamProperties.XSP_V_XMLID_NONE;
                }
                return hasConfigFlags(CFG_XMLID_UNIQ_CHECKS) ?
                    XMLStreamProperties.XSP_V_XMLID_FULL :
                    XMLStreamProperties.XSP_V_XMLID_TYPING;
            }

            // then object values:
        case PROP_INPUT_BUFFER_LENGTH:
            return new Integer(getInputBufferLength());
        case PROP_MIN_TEXT_SEGMENT:
            return new Integer(getShortestReportedTextSegment());
        case PROP_CUSTOM_INTERNAL_ENTITIES:
            return getCustomInternalEntities();
        case PROP_DTD_RESOLVER:
            return getDtdResolver();
        case PROP_ENTITY_RESOLVER:
            return getEntityResolver();
        case PROP_UNDECLARED_ENTITY_RESOLVER:
            return getUndeclaredEntityResolver();
        case PROP_BASE_URL:
            return getBaseURL();
        case PROP_INPUT_PARSING_MODE:
            return getInputParsingMode();

        default: // sanity check, should never happen
            throw new Error("Internal error: no handler for property with internal id "+id+".");
        }
    }

    public boolean setProperty(String propName, int id, Object value)
    {
        switch (id) {
            // First, standard properties:

        case PROP_COALESCE_TEXT:
            doCoalesceText(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_NAMESPACE_AWARE:
            doSupportNamespaces(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPLACE_ENTITY_REFS:
            doReplaceEntityRefs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_SUPPORT_EXTERNAL_ENTITIES:
            doSupportExternalEntities(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_SUPPORT_DTD:
            doSupportDTDs(ArgUtil.convertToBoolean(propName, value));
            break;
            
            // // // Then ones that can be dispatched:

        case PROP_VALIDATE_AGAINST_DTD:
            doValidateWithDTD(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_WARNING_REPORTER:
            setXMLReporter((XMLReporter) value);
            break;

        case PROP_XML_RESOLVER:
            setXMLResolver((XMLResolver) value);
            break;

        case PROP_EVENT_ALLOCATOR:
            /* 25-Mar-2006, TSa: Not really supported here, so let's
             *   return false to let caller deal with it
             */
            return false;

            // // // Custom settings, flags:

        case PROP_INTERN_NAMES:
            doInternNames(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_INTERN_NS_URIS:
            doInternNsURIs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPORT_PROLOG_WS:
            doReportPrologWhitespace(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_CACHE_DTDS:
            doCacheDTDs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_CACHE_DTDS_BY_PUBLIC_ID:
            doCacheDTDsByPublicId(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_LAZY_PARSING:
            doParseLazily(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_SUPPORT_XMLID:
            {
                boolean typing, uniq;

                if (XMLStreamProperties.XSP_V_XMLID_NONE.equals(value)) {
                    typing = uniq = false;
                } else if (XMLStreamProperties.XSP_V_XMLID_TYPING.equals(value)) {
                    typing = true;
                    uniq = false;
                } else if (XMLStreamProperties.XSP_V_XMLID_FULL.equals(value)) {
                    typing = uniq = true;
                } else {
                    throw new IllegalArgumentException
                        ("Illegal argument ('"+value+"') to set property "
+XMLStreamProperties.XSP_SUPPORT_XMLID+" to: has to be one of '"
+XMLStreamProperties.XSP_V_XMLID_NONE+"', '"
+XMLStreamProperties.XSP_V_XMLID_TYPING+"' or '"
+XMLStreamProperties.XSP_V_XMLID_FULL+"'"
                         );
                }
                setConfigFlag(CFG_XMLID_TYPING, typing);
                setConfigFlag(CFG_XMLID_UNIQ_CHECKS, uniq);
                break;
            }

        case PROP_PRESERVE_LOCATION:
            doPreserveLocation(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_AUTO_CLOSE_INPUT:
            doAutoCloseInput(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPORT_CDATA:
            doReportCData(ArgUtil.convertToBoolean(propName, value));
            break;

            // // // Custom settings, Object properties:

        case PROP_INPUT_BUFFER_LENGTH:
            setInputBufferLength(ArgUtil.convertToInt(propName, value, 1));
            break;
            
        case PROP_MIN_TEXT_SEGMENT:
            setShortestReportedTextSegment(ArgUtil.convertToInt(propName, value, 1));
            break;

        case PROP_CUSTOM_INTERNAL_ENTITIES:
            setCustomInternalEntities((Map) value);
            break;

        case PROP_DTD_RESOLVER:
            setDtdResolver((XMLResolver) value);
            break;

        case PROP_ENTITY_RESOLVER:
            setEntityResolver((XMLResolver) value);
            break;

        case PROP_UNDECLARED_ENTITY_RESOLVER:
            setUndeclaredEntityResolver((XMLResolver) value);
            break;

        case PROP_BASE_URL:
            setBaseURL((URL) value);
            break;

        case PROP_INPUT_PARSING_MODE:
            setInputParsingMode((WstxInputProperties.ParsingMode) value);
            break;

        default: // sanity check, should never happen
            throw new Error("Internal error: no handler for property with internal id "+id+".");
        }

        return true;
    }

    private Object getSpecialProperty(int ix)
    {
        if (mSpecialProperties == null) {
            return null;
        }
        return mSpecialProperties[ix];
    }

    private void setSpecialProperty(int ix, Object value)
    {
        if (mSpecialProperties == null) {
            mSpecialProperties = new Object[3];
        }
        mSpecialProperties[ix] = value;
    }
}
