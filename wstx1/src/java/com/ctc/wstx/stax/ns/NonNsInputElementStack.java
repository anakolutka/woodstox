package com.ctc.wstx.stax.ns;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.stax.stream.StreamScanner;
import com.ctc.wstx.util.EmptyIterator;
import com.ctc.wstx.util.SingletonIterator;
import com.ctc.wstx.util.StringVector;
import com.ctc.wstx.util.TextBuilder;

/**
 * Sub-class of {@link InputElementStack} used when operating in
 * non-namespace-aware, non valiadting mode.
 */
public class NonNsInputElementStack
    extends InputElementStack
{
    /*
    //////////////////////////////////////////////////
    // Configuration
    //////////////////////////////////////////////////
     */

    private final NonNsAttributeCollector mAttrCollector;

    /*
    //////////////////////////////////////////////////
    // Element stack state information
    //////////////////////////////////////////////////
     */

    /**
     * Array that contains path of open elements from root, one String
     * for each open start element.
     */
    protected String[] mElements;

    /**
     * Number of Strings in {@link #mElements} that are valid.
     */
    protected int mSize;

    /*
    //////////////////////////////////////////////////
    // Life-cycle (create, update state)
    //////////////////////////////////////////////////
     */

    public NonNsInputElementStack(int initialSize)
    {
        mSize = 0;
        if (initialSize < 4) {
            initialSize = 4;
        }
        mElements = new String[initialSize];
        mAttrCollector = new NonNsAttributeCollector();
    }

    public final void push(String prefix, String localName)
    {
        throw new Error("Internal error: push(prefix, localName) shouldn't be called for non-namespace element stack.");
    }

    public final void push(String fullName)
    {
        if (mSize == mElements.length) {
            String[] old = mElements;
            mElements = new String[old.length + 32];
            System.arraycopy(old, 0, mElements, 0, old.length);
        }
        mElements[mSize] = fullName;
        ++mSize;
        mAttrCollector.reset();
    }

    /**
     * @return Validation state that should be effective for the parent
     *   element state
     */
    public int pop()
    {
        if (mSize == 0) {
            throw new IllegalStateException("Popping from empty stack.");
        }
        /* Let's allow GCing (not likely to matter, as Strings are very
         * likely interned... but it's a good habit
         */
        mElements[--mSize] = null;

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
        // Need to inform attribute collector, at least
        mAttrCollector.resolveValues(sc);

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
        return mSize;
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
        return EmptyNamespaceContext.getInstance();
    }

    /*
    ///////////////////////////////////////////////////
    // Implementation of NamespaceContext:
    ///////////////////////////////////////////////////
     */

    public final String getNamespaceURI(String prefix) {
        return null;
    }

    public final String getPrefix(String nsURI) {
        return null;
    }

    public final Iterator getPrefixes(String nsURI) {
        return EmptyIterator.getInstance();
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
        return null;
    }

    public final String getNsURI() {
        return null;
    }

    public final String getPrefix() {
        return null;
    }

    public final String getLocalName() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-1];
    }

    public final QName getQName() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return new QName(mElements[mSize-1]);
    }

    public final boolean matches(String prefix, String localName)
    {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        if (prefix != null && prefix.length() > 0) {
            return false;
        }
        String thisName = mElements[mSize-1];
        return (thisName == localName) || thisName.equals(localName);
    }

    public final String getTopElementDesc() {
        if (mSize == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mElements[mSize-1];
    }

    // // // Namespace information:

    public final int getTotalNsCount() {
        return 0;
    }

    /**
     * @return Number of active prefix/namespace mappings for current scope,
     *   NOT including mappings from enclosing elements.
     */
    public final int getCurrentNsCount() {
        return 0;
    }

    public final String getLocalNsPrefix(int index) { 
        throw new IllegalArgumentException("Illegal namespace index "+index
                                           +"; current scope has no namespace declarations.");
    }

    public final String getLocalNsURI(int index) { 
        throw new IllegalArgumentException("Illegal namespace index "+index
                                           +"; current scope has no namespace declarations.");
    }
}
