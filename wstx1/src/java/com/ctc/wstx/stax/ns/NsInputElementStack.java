package com.ctc.wstx.stax.ns;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.stax.stream.StreamScanner;
import com.ctc.wstx.util.SingletonIterator;
import com.ctc.wstx.util.StringVector;
import com.ctc.wstx.util.TextBuilder;

/**
 * Sub-class of {@link InputElementStack} used when operating in
 * namespace-aware mode.
 */
public class NsInputElementStack
    extends InputElementStack
{
    /**
     * Topmost namespace URI assigned for root element, if not specifically
     * defined. Could either be null, or empty String; seems like most
     * xml processors prefer latter.
     */
    final static String DEFAULT_NAMESPACE_URI = "";

    final static int IX_PREFIX = 0;
    final static int IX_LOCALNAME = 1;
    final static int IX_URI = 2;
    final static int IX_DEFAULT_NS = 3;

    final static int ENTRY_SIZE = 4;

    /**
     * Canonicalized String 'xml'; used to verify that 'xml' namespace
     * prefix won't be redefined from its default URI.
     */
    protected final String mPrefixXml;

    /**
     * Canonicalized String 'xmlns'; used to verify that 'xmlns' namespace
     * prefix won't be redefined from its default URI.
     */
    protected final String mPrefixXmlns;

    /*
    //////////////////////////////////////////////////
    // Configuration
    //////////////////////////////////////////////////
     */

    protected final NsAttributeCollector mAttrCollector;

    /*
    //////////////////////////////////////////////////
    // Element stack state information
    //////////////////////////////////////////////////
     */

    /**
     * Vector that contains all currently active namespaces; one String for
     * prefix, another for matching URI. Does NOT contain default name
     * spaces.
     */
    protected final StringVector mNamespaces = new StringVector(64);

    /**
     * Array that contains path of open elements from root; for each there
     * are 4 Strings; prefix, localname, URI, and default name space URI.
     */
    protected String[] mElements;

    /**
     * Number of Strings in {@link #mElements} that are valid (ie depth
     * multiplied by 4)
     */
    protected int mSize;

    /**
     * Array that contains namespace offsets for each element; that is,
     * index of first 'local' name space entry, entry declared for
     * current element. Number of such local entries is
     * <code>mCurrNsCount - mNsCounts[mSize-1]</code>
     */
    protected int[] mNsCounts;

    /*
    //////////////////////////////////////////////////
    // Life-cycle (create, update state)
    //////////////////////////////////////////////////
     */

    public NsInputElementStack(int initialSize,
                               String prefixXml, String prefixXmlns)
    {
        mPrefixXml = prefixXml;
        mPrefixXmlns = prefixXmlns;
        mSize = 0;
        if (initialSize < 4) {
            initialSize = 4;
        }
        mElements = new String[initialSize << 2];
        mNsCounts = new int[initialSize];
        mAttrCollector = new NsAttributeCollector(prefixXml, prefixXmlns);
    }

    public final void push(String prefix, String localName)
    {
        int index = mSize;
        if (index == mElements.length) {
            String[] old = mElements;
            mElements = new String[old.length + 64];
            System.arraycopy(old, 0, mElements, 0, old.length);
        }
        mElements[index] = prefix;
        mElements[index+1] = localName;

        if (index == 0) { // root element
            mElements[IX_DEFAULT_NS] = DEFAULT_NAMESPACE_URI;
        } else {
            // Let's just duplicate parent's default NS URI as baseline:
            mElements[index + IX_DEFAULT_NS] = mElements[index - (ENTRY_SIZE - IX_DEFAULT_NS)];
         }
        mSize = index+4;

        // Also need to update namespace stack:
        index >>= 2;
        if (index == mNsCounts.length) {
            int[] old = mNsCounts;
            mNsCounts = new int[old.length + 16];
            System.arraycopy(old, 0, mNsCounts, 0, old.length);
        }
        mNsCounts[index] = mNamespaces.size();
        mAttrCollector.reset();
    }

    public final void push(String fullName) {
        throw new Error("Internal error: push(fullName) shouldn't be called for namespace aware element stack.");
    }

    /**
     * @return Validation state that should be effective for the parent
     *   element state
     */
    public int pop()
    {
        int index = mSize;
        if (index == 0) {
            throw new IllegalStateException("Popping from empty stack.");
        }
        /* Let's allow GCing (not likely to matter, as Strings are very
         * likely interned... but it's a good habit
         */
        index -= 4;
        mSize = index;
        mElements[index] = null;
        mElements[index+1] = null;
        mElements[index+2] = null;
        mElements[index+3] = null;

        // Need to purge namespaces?
        index = (index >> 2);
        int nsCount = mNamespaces.size() - mNsCounts[index];
        if (nsCount > 0) { // 2 entries for each NS mapping:
            mNamespaces.removeLast(nsCount);
        }
        return CONTENT_ALLOW_MIXED;
    }

    /**
     * Method called to update information about top of the stack, with
     * attribute information passed in. Will resolve namespace references,
     * and update namespace stack with information.
     *
     * @return Validation state that should be effective for the fully
     *   resolved element context
     */
    public int resolveElem(StreamScanner sc, boolean internNsURIs)
        throws XMLStreamException
    {
        if (mSize == 0) {
            throw new IllegalStateException("Calling validate() on empty stack.");
        }
        NsAttributeCollector ac = mAttrCollector;

        // Any namespace declarations?
        {
            int nsCount = ac.getNsCount();
            if (nsCount > 0) {
                String [] nsPrefixes = ac.getNsPrefixes();
                TextBuilder nsURIs = ac.getNsURIs();
                for (int i = 0; i < nsCount; ++i) {
                    String nsUri = nsURIs.getEntry(i);
                    if (internNsURIs && nsUri.length() > 0) {
                        nsUri = nsUri.intern();
                    }
                    /* 28-Jul-2004, TSa: Now we will have default namespaces
                     *   in there too; they have null as prefix
                     */
                    String prefix = nsPrefixes[i];
                    if (prefix == null) {
                        mElements[mSize-(ENTRY_SIZE - IX_DEFAULT_NS)] = nsUri;

                    /* 18-Jul-2004, TSa: Need to check that 'xml' and 'xmlns'
                     *   prefixes are not re-defined.
                     * !!! Should probably also check that matching URIs are
                     *   never bound to other prefixes, since that's what
                     *   Xerces does?
                     */
                    } else if (prefix == mPrefixXml) {
                        if (!nsUri.equals(XMLConstants.XML_NS_URI)) {
                            sc.throwParseError("Trying to redeclare prefix '"
                                               +prefix+"' from its default URI '"
                                               +XMLConstants.XML_NS_URI
                                               +"' to '"+nsUri+".");
                        }
                    } else if (prefix == mPrefixXmlns) {
                        if (!nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                            sc.throwParseError("Trying to redeclare prefix '"
                                               +prefix+"' from its default URI '"
                                               +XMLConstants.XMLNS_ATTRIBUTE_NS_URI
                                               +"' to '"+nsUri+".");
                        }
                    }
                    mNamespaces.addStrings(prefix, nsUri);
                }
            }
        }

        // Then, let's set element's namespace, if any:
        String prefix = mElements[mSize-(ENTRY_SIZE - IX_PREFIX)];
        if (prefix == null || prefix.length() == 0) { // use default NS, if any
            mElements[mSize-(ENTRY_SIZE - IX_URI)] 
                = mElements[mSize-(ENTRY_SIZE - IX_DEFAULT_NS)];
        } else {
            // Need to find namespace with the prefix:
            String ns = mNamespaces.findLastFromMap(prefix);
            if (ns == null) {
                sc.throwParseError("Undeclared namespace prefix '"+prefix+"'.");
            }
            mElements[mSize-(ENTRY_SIZE - IX_URI)] = ns;
        }

        // And finally, resolve attributes' namespaces too:
        ac.resolveNamespaces(sc, mNamespaces);
        
        return CONTENT_ALLOW_MIXED;
    }

    /*
    ///////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////
     */

    /**
     * @return Number of open elements in the stack; 0 when parser is in
     *  prolog/epilog, 1 inside root element and so on.
     */
    public final int getDepth() {
        return (mSize >> 2);
    }

    public final AttributeCollector getAttrCollector() {
        return mAttrCollector;
    }

    /**
     * Method called to construct a non-transient NamespaceContext instance;
     * generally needed when creating events to return from event-based
     * iterators.
     */
    public final BaseNsContext createNonTransientNsContext(Location loc) {
        int localCount = getCurrentNsCount() << 1;
        return new CompactNsContext(loc, getDefaultNsURI(), mNamespaces.asArray(),
                              mNamespaces.size() - localCount);
                              
    }

    /*
    ///////////////////////////////////////////////////
    // Implementation of NamespaceContext:
    ///////////////////////////////////////////////////
     */

    public final String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Illegal to pass null as argument.");
        }
        if (prefix.length() == 0) {
            if (mSize == 0) { // could signal an error too
                return null;
            }
            return mElements[mSize-(ENTRY_SIZE - IX_DEFAULT_NS)];
        }
        if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
            return XMLConstants.XML_NS_URI;
        }
        if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        }
        /* Ok, need to find the match, if any; starting from end of the
         * list of active namespaces. Note that we can not count on prefix
         * being interned/canonicalized.
         */
        return mNamespaces.findLastNonInterned(prefix);
    }

    public final String getPrefix(String nsURI) {
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return XMLConstants.XML_NS_PREFIX;
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return XMLConstants.XMLNS_ATTRIBUTE;
        }
        /* Ok, need to find the match, if any; starting from end of the
         * list of active namespaces. Note that we can not count on prefix
         * being interned/canonicalized.
         */
        return mNamespaces.findLastByValueNonInterned(nsURI);
    }

    public final Iterator getPrefixes(String nsURI) {
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return new SingletonIterator(XMLConstants.XML_NS_PREFIX);
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return new SingletonIterator(XMLConstants.XMLNS_ATTRIBUTE);
        }

        return mNamespaces.findAllByValueNonInterned(nsURI);
    }

    /*
    ///////////////////////////////////////////////////
    // Accessors:
    ///////////////////////////////////////////////////
     */

    // // // Generic stack information:

    public final boolean isEmpty() {
        return mSize == 0;
    }

    // // // Information about element at top of stack:

    public final String getDefaultNsURI() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-(ENTRY_SIZE - IX_DEFAULT_NS)];
    }

    public final String getNsURI() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-(ENTRY_SIZE - IX_URI)];
    }

    public final String getPrefix() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-(ENTRY_SIZE - IX_PREFIX)];
    }

    public final String getLocalName() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-(ENTRY_SIZE - IX_LOCALNAME)];
    }

    public final QName getQName() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        String prefix = mElements[mSize-(ENTRY_SIZE - IX_PREFIX)];
        if (prefix == null) {
            prefix = "";
        }
        return new QName(mElements[mSize-(ENTRY_SIZE - IX_URI)],
                         mElements[mSize-(ENTRY_SIZE - IX_LOCALNAME)],
                         prefix
                         );
    }

    public final boolean matches(String prefix, String localName) {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        String thisPrefix = mElements[mSize-(ENTRY_SIZE - IX_PREFIX)];
        if (prefix == null || prefix.length() == 0) { // no name space
            if (thisPrefix != null && thisPrefix.length() > 0) {
                return false;
            }
        } else {
            if (thisPrefix != prefix && !thisPrefix.equals(prefix)) {
                return false;
            }
        }

        String thisName = mElements[mSize-3];
        return (thisName == localName) || thisName.equals(localName);
    }

    public final String getTopElementDesc() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        String name = mElements[mSize-3];
        String prefix = mElements[mSize-4];
        if (prefix == null || prefix.length() == 0) { // no name space
            return name;
        }
        return prefix + ":" + name;
    }

    // // // Namespace information:

    /**
     * @return Number of active prefix/namespace mappings for current scope,
     *   including mappings from enclosing elements.
     */
    public final int getTotalNsCount() {
        return mNamespaces.size() >> 1;
    }

    /**
     * @return Number of active prefix/namespace mappings for current scope,
     *   NOT including mappings from enclosing elements.
     */
    public final int getCurrentNsCount() {
        // Need not check for empty stack; should return 0 properly
        return (mNamespaces.size() - mNsCounts[(mSize-1) >> 2]) >> 1;
    }

    public final String getLocalNsPrefix(int index) {
        int offset = mNsCounts[(mSize-1) >> 2];
        int localCount = (mNamespaces.size() - offset);
        index <<= 1; // 2 entries, prefix/URI for each NS
        if (index < 0 || index >= localCount) {
            throw new IllegalArgumentException("Illegal namespace index "
                                           +(index >> 1)
                                           +"; current scope only has "
                                           +(localCount >> 1)
                                           +" namespace declarations.");
        }
        return mNamespaces.getString(offset + index);
    }

    public final String getLocalNsURI(int index) {
        int offset = mNsCounts[(mSize-1) >> 2];
        int localCount = (mNamespaces.size() - offset);
        index <<= 1; // 2 entries, prefix/URI for each NS
        if (index < 0 || index >= localCount) {
            throw new IllegalArgumentException("Illegal namespace index "
                                           +(index >> 1)
                                           +"; current scope only has "
                                           +(localCount >> 1)
                                           +" namespace declarations.");
        }
        return mNamespaces.getString(offset + index + 1);
    }
}
