/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sr;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.ri.typed.ValueDecoderFactory;
import org.codehaus.stax2.typed.TypedValueDecoder;
import org.codehaus.stax2.typed.TypedXMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.io.BranchingReaderSource;
import com.ctc.wstx.io.InputBootstrapper;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.util.TextAccumulator;

/**
 * Completed implementation of {@link XMLStreamReader2}, including
 * Typed Access API (Stax2 v3.0) implementation. Only functionality
 * missing is DTD validation, which is provided by a specialized
 * sub-class.
 */

public class TypedStreamReader
    extends BasicStreamReader
{
    /**
     * Factory used for constructing decoders we need for typed access
     */
    protected ValueDecoderFactory mDecoderFactory;

    /*
    ////////////////////////////////////////////////////
    // Instance construction
    ////////////////////////////////////////////////////
     */

    protected TypedStreamReader(InputBootstrapper bs,
                                BranchingReaderSource input, ReaderCreator owner,
                                ReaderConfig cfg, InputElementStack elemStack,
                                boolean forER)
        throws XMLStreamException
    {
        super(bs, input, owner, cfg, elemStack, forER);
    }

    /**
     * Factory method for constructing readers.
     *
     * @param owner "Owner" of this reader, factory that created the reader;
     *   needed for returning updated symbol table information after parsing.
     * @param input Input source used to read the XML document.
     * @param cfg Object that contains reader configuration info.
     */
    public static TypedStreamReader createStreamReader
        (BranchingReaderSource input, ReaderCreator owner, ReaderConfig cfg,
         InputBootstrapper bs, boolean forER)
        throws XMLStreamException
    {

        TypedStreamReader sr = new TypedStreamReader
            (bs, input, owner, cfg, createElementStack(cfg), forER);
        return sr;
    }


    /*
    ////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, scalar elements
    ////////////////////////////////////////////////////////
     */

    public boolean getElementAsBoolean() throws XMLStreamException
    {
        ValueDecoderFactory.BooleanDecoder dec = decoderFactory().getBooleanDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public int getElementAsInt() throws XMLStreamException
    {
        ValueDecoderFactory.IntDecoder dec = decoderFactory().getIntDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public long getElementAsLong() throws XMLStreamException
    {
        ValueDecoderFactory.LongDecoder dec = decoderFactory().getLongDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public float getElementAsFloat() throws XMLStreamException
    {
        ValueDecoderFactory.FloatDecoder dec = decoderFactory().getFloatDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public double getElementAsDouble() throws XMLStreamException
    {
        ValueDecoderFactory.DoubleDecoder dec = decoderFactory().getDoubleDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public BigInteger getElementAsInteger() throws XMLStreamException
    {
        ValueDecoderFactory.IntegerDecoder dec = decoderFactory().getIntegerDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public BigDecimal getElementAsDecimal() throws XMLStreamException
    {
        ValueDecoderFactory.DecimalDecoder dec = decoderFactory().getDecimalDecoder();
        decodeElementText(dec);
        return dec.getValue();
    }

    public QName getElementAsQName() throws XMLStreamException
    {
        ValueDecoderFactory.QNameDecoder dec = decoderFactory().getQNameDecoder(getNamespaceContext());
        decodeElementText(dec);
        return verifyQName(dec.getValue());
    }

    public void getElementAs(TypedValueDecoder tvd) throws XMLStreamException
    {
        decodeElementText(tvd);
    }

    /*
    ////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, array elements
    ////////////////////////////////////////////////////////
     */

    public int readElementAsIntArray(int[] value, int from, int length) throws XMLStreamException
    {
        // !!! TBI
        return -1;
    }

    public int readElementAsLongArray(long[] value, int from, int length) throws XMLStreamException
    {
        // !!! TBI
        return -1;
    }

    public int readElementAsFloatArray(float[] value, int from, int length) throws XMLStreamException
    {
        // !!! TBI
        return -1;
    }

    public int readElementAsDoubleArray(double[] value, int from, int length) throws XMLStreamException
    {
        // !!! TBI
        return -1;
    }

    /*
    public void readElementAs(TypedArrayDecoder tvd) throws XMLStreamException
    {
    }
    */

    /*
    ///////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, scalar attributes
    ///////////////////////////////////////////////////////////
     */

    public int getAttributeIndex(String namespaceURI, String localName)
    {
        // Note: cut'n pasted from "getAttributeInfo()"
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mElementStack.findAttributeIndex(namespaceURI, localName);
    }

    public boolean getAttributeAsBoolean(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.BooleanDecoder dec = decoderFactory().getBooleanDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public int getAttributeAsInt(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.IntDecoder dec = decoderFactory().getIntDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public long getAttributeAsLong(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.LongDecoder dec = decoderFactory().getLongDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public float getAttributeAsFloat(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.FloatDecoder dec = decoderFactory().getFloatDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public double getAttributeAsDouble(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.DoubleDecoder dec = decoderFactory().getDoubleDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public BigInteger getAttributeAsInteger(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.IntegerDecoder dec = decoderFactory().getIntegerDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public BigDecimal getAttributeAsDecimal(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.DecimalDecoder dec = decoderFactory().getDecimalDecoder();
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return dec.getValue();
    }

    public QName getAttributeAsQName(int index) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        ValueDecoderFactory.QNameDecoder dec = decoderFactory().getQNameDecoder(getNamespaceContext());
        try {
            mAttrCollector.decodeValue(index, dec);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
        return verifyQName(dec.getValue());
    }

    public void getAttributeAs(int index, TypedValueDecoder tvd) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        try {
            mAttrCollector.decodeValue(index, tvd);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, mAttrCollector.getValue(index));
        }
    }

    /**
     * Special implementation of functionality similar to that of
     * {@link #getElementText}, but optimized for the specific
     * use case of handling typed element content. This allows for
     * doing things like trimming leading white space.
     *
     * @return Collected String, if any; or null to indicate
     *   contents are in mTextBuffer.
     */
    private final String collectElementText()
        throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throwParseError(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        /* Ok, now: with START_ELEMENT we know that it's not partially
         * processed; that we are in-tree (not prolog or epilog).
         * The only possible complication would be 
         */
        if (mStEmptyElem) {
            // And if so, we'll then get 'virtual' close tag; things
            // are simple as location info was set when dealing with
            // empty start element; and likewise, validation (if any)
            // has been taken care of.
            mStEmptyElem = false;
            mCurrToken = END_ELEMENT;
            return "";
        }
        // First need to find a textual event
        while (true) {
            int type = next();
            if (type == END_ELEMENT) {
                return "";
            }
            if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                continue;
            }
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) == 0) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
            break;
        }
        if (mTokenState < TOKEN_FULL_SINGLE) {
            readCoalescedText(mCurrToken, false);
        }
        /* Ok: then quick check; if it looks like we are directly
         * followed by the end tag, we need not construct String
         * quite yet.
         */
        if ((mInputPtr + 1) < mInputEnd &&
            mInputBuffer[mInputPtr] == '<' && mInputBuffer[mInputPtr+1] == '/') {
            // But first: is textual content validation needed?
            if (mValidateText) {
                mElementStack.validateText(mTextBuffer, true);
            }
            mInputPtr += 2;
            mCurrToken = END_ELEMENT;
            // Can by-pass next(), nextFromTree(), in this case:
            readEndElem();
            return null;
        }

        // Otherwise, we'll need to do slower processing

        String text = mTextBuffer.contentsAsString();
        // Then we'll see if end is nigh...
        TextAccumulator acc = null;
        int type;
        
        while ((type = next()) != END_ELEMENT) {
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) != 0) {
                if (acc == null) {
                    acc = new TextAccumulator();
                    acc.addText(text);
                }
                acc.addText(getText());
                continue;
            }
            if (type != COMMENT && type != PROCESSING_INSTRUCTION) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
        }
        return (acc == null) ? text : acc.getAndClear();
    }

    private final void decodeElementText(TypedValueDecoder dec)
        throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throwParseError(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        /* Ok, now: with START_ELEMENT we know that it's not partially
         * processed; that we are in-tree (not prolog or epilog).
         * The only possible complication would be 
         */
        if (mStEmptyElem) {
            // And if so, we'll then get 'virtual' close tag; things
            // are simple as location info was set when dealing with
            // empty start element; and likewise, validation (if any)
            // has been taken care of.
            mStEmptyElem = false;
            mCurrToken = END_ELEMENT;
            try {
                dec.decode("");
            } catch (IllegalArgumentException iae) {
                throw constructTypeException(iae, "");
            }
            return;
        }
        // First need to find a textual event
        while (true) {
            int type = next();
            if (type == END_ELEMENT) {
                try {
                    dec.decode("");
                } catch (IllegalArgumentException iae) {
                    throw constructTypeException(iae, "");
                }
                return;
            }
            if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                continue;
            }
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) == 0) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
            break;
        }
        if (mTokenState < TOKEN_FULL_SINGLE) {
            readCoalescedText(mCurrToken, false);
        }
        /* Ok: then quick check; if it looks like we are directly
         * followed by the end tag, we need not construct String
         * quite yet.
         */
        if ((mInputPtr + 1) < mInputEnd &&
            mInputBuffer[mInputPtr] == '<' && mInputBuffer[mInputPtr+1] == '/') {
            // But first: is textual content validation needed?
            if (mValidateText) {
                mElementStack.validateText(mTextBuffer, true);
            }
            mInputPtr += 2;
            mCurrToken = END_ELEMENT;
            // Can by-pass next(), nextFromTree(), in this case:
            readEndElem();
            // And buffer, then, has data for conversion, so:
            try {
                mTextBuffer.decode(dec);
            } catch (IllegalArgumentException iae) {
                throw constructTypeException(iae, mTextBuffer.contentsAsString());
            }
            return;
        }

        // Otherwise, we'll need to do slower processing

        String text = mTextBuffer.contentsAsString();
        // Then we'll see if end is nigh...
        TextAccumulator acc = null;
        int type;
        
        while ((type = next()) != END_ELEMENT) {
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) != 0) {
                if (acc == null) {
                    acc = new TextAccumulator();
                    acc.addText(text);
                }
                acc.addText(getText());
                continue;
            }
            if (type != COMMENT && type != PROCESSING_INSTRUCTION) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
        }
        String str = (acc == null) ? text : acc.getAndClear();
        try {
            dec.decode(str);
        } catch (IllegalArgumentException iae) {
            throw constructTypeException(iae, str);
        }
    }

    /**
     * Method called to parse array of pritive
     */
    private final int readElementAsArray(TypedValueDecoder dec)
        throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throwParseError(ErrorConsts.ERR_STATE_NOT_STELEM);
        }


        // !!! TBI
        return -1;
    }

    /*
    /////////////////////////////////////////////////////
    // Internal helper methods
    /////////////////////////////////////////////////////
     */

    /**
     * Method called to verify validity of the parsed QName element
     * or attribute value. At this point binding of a prefixed name
     * (if qname has a prefix) has been verified, and thereby prefix
     * also must be valid (since there must have been a preceding
     * declaration). But local name might still not be a legal
     * well-formed xml name, so let's verify that.
     */
    protected QName verifyQName(QName n)
        throws TypedXMLStreamException
    {
        String ln = n.getLocalPart();
        int ix = WstxInputData.findIllegalNameChar(ln, mCfgNsEnabled, mXml11);
        if (ix >= 0) {
            String prefix = n.getPrefix();
            String pname = (prefix != null && prefix.length() > 0) ?
                (prefix + ":" +ln) : ln;
            throw constructTypeException("Invalid local name \""+ln+"\" (character at #"+ix+" is invalid)", pname);
        }
        return n;
    }

    protected ValueDecoderFactory decoderFactory()
    {
        if (mDecoderFactory == null) {
            mDecoderFactory = new ValueDecoderFactory();
        }
        return mDecoderFactory;
    }

    /**
     * Method called to wrap or convert given conversion-fail exception
     * into a full {@link TypedXMLStreamException),
     *
     * @param iae Problem as reported by converter
     * @param lexicalValue Lexical value (element content, attribute value)
     *    that could not be converted succesfully.
     */
    protected TypedXMLStreamException constructTypeException(IllegalArgumentException iae, String lexicalValue)
    {
        return new TypedXMLStreamException(lexicalValue, iae.getMessage(), getStartLocation(), iae);
    }

    protected TypedXMLStreamException constructTypeException(String msg, String lexicalValue)
    {
        return new TypedXMLStreamException(lexicalValue, msg, getStartLocation());
    }

}

