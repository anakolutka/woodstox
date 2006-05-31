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

package com.ctc.wstx.sw;

import java.io.*;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.io.CharsetNames;

/**
 * Concrete implementation of {@link XmlWriter} that will dispatch writes
 * to another writer (of type {@link java.io.Writer}, and will NOT handle
 * encoding. It will, however, do basic buffering such that the underlying
 * Writer need (and thus, should) not do buffering.
 *<p>
 * One design goal for this class is to avoid unnecessary buffering: since
 * there will be another Writer doing the actual encoding, amount of
 * buffering needed should still be limited. To this end, a threshold is
 * used to define what's the threshold of writes that we do want to
 * coalesce, ie. buffer. Writes bigger than this should in general proceed
 * without buffering.
 */
public final class BufferingXmlWriter
    extends XmlWriter
            implements XMLStreamConstants
{
    /**
     * Let's use a typical default to have a compromise between large
     * enough chunks to output, and minimizing memory overhead.
     * Compared to encoding writers, buffer size can be bit smaller
     * since there's one more level of processing (at encoding), which
     * may use bigger buffering.
     */
    final static int DEFAULT_BUFFER_SIZE = 1000;

    /**
     * Choosing threshold for 'small size' is a compromise between
     * excessive buffering (high small size), and too many fragmented
     * calls to the underlying writer (low small size). Let's just
     * use about 1/4 of the full buffer size.
     */
    final static int DEFAULT_SMALL_SIZE = 256;

    /**
     * Highest valued character that may need to be encoded (minus charset
     * encoding requirements) when writing attribute values.
     */
    protected final static int HIGHEST_ENCODABLE_ATTR_CHAR = (int)'<';

    /**
     * Highest valued character that may need to be encoded (minus charset
     * encoding requirements) when writing attribute values.
     */
    protected final static int HIGHEST_ENCODABLE_TEXT_CHAR = (int)'>';

    /*
    ////////////////////////////////////////////////
    // Output state, buffering
    ////////////////////////////////////////////////
     */

    /**
     * Actual Writer to use for outputting buffered data as appropriate.
     */
    protected final Writer mOut;

    protected final char[] mOutputBuffer;

    /**
     * This is the threshold used to check what is considered a "small"
     * write; small writes will be buffered until resulting size will
     * be above the threshold.
     */
    protected final int mSmallWriteSize;

    protected int mOutputPtr;

    /*
    ////////////////////////////////////////////////
    // Encoding/escaping configuration
    ////////////////////////////////////////////////
     */

    /**
     * First Unicode character (one with lowest value) after (and including)
     * which character entities have to be used. For
     */
    private final int mEncHighChar;

    /**
     * Character that is considered to be the enclosing quote character;
     * for XML either single or double quote.
     */
    final char mEncQuoteChar;

    /**
     * Entity String to use for escaping the quote character.
     */
    final String mEncQuoteEntity;

    /*
    ////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////
     */

    public BufferingXmlWriter(Writer out, WriterConfig cfg, String enc)
        throws IOException
    {
        super(cfg, enc);
        mOut = out;
        //mOutputBuffer = new char[DEFAULT_BUFFER_SIZE];
        mOutputBuffer = cfg.allocFullCBuffer(DEFAULT_BUFFER_SIZE);
        mSmallWriteSize = DEFAULT_SMALL_SIZE;
        mOutputPtr = 0;

        // Let's use double-quotes, as usual; alternative is apostrophe
        mEncQuoteChar = '"';
        mEncQuoteEntity = "&quot;";
        /* Note: let's actually exclude couple of illegal chars for
         * unicode-based encoders. But we do not have to worry about
         * surrogates quite here, fortunately.
         */
        int bitsize = guessEncodingBitSize(enc);
        mEncHighChar = ((bitsize < 16) ? (1 << bitsize) : 0xFFFE);
    }

    protected int getOutputPtr() {
        return mOutputPtr;
    }

    /*
    ////////////////////////////////////////////////
    // Low-level (pass-through) methods
    ////////////////////////////////////////////////
     */

    public void close()
        throws IOException
    {
        mOut.close();
    }

    public final void flush()
        throws IOException
    {
        flushBuffer();
        mOut.flush();
    }

    public void writeRaw(char[] cbuf, int offset, int len)
        throws IOException
    {
        // First; is the new request small or not? If yes, needs to be buffered
        if (len < mSmallWriteSize) { // yup
            // Does it fit in with current buffer? If not, need to flush first
            if ((mOutputPtr + len) > mOutputBuffer.length) {
                flushBuffer();
            }
            int ptr = mOutputPtr;
            /* Note: since it's a small copy, probably faster to copy without
             * System.arraycopy (which uses JNI)
             */
            char[] outBuf = mOutputBuffer;
            for (len += offset; offset < len; ++offset, ++ptr) {
                outBuf[ptr] = cbuf[offset];
            }
            mOutputPtr = ptr;
            return;
        }

        // Ok, not a small request. But buffer may have existing content?
        int ptr = mOutputPtr;
        if (ptr > 0) {
            // If it's a small chunk, need to fill enough before flushing
            if (ptr < mSmallWriteSize) {
                /* Also, if we are to copy any stuff, let's make sure
                 * that we either copy it all in one chunk, or copy
                 * enough for non-small chunk, flush, and output remaining
                 * non-small chink (former possible if chunk we were requested
                 * to output is only slightly over 'small' size)
                 */
                char[] outBuf = mOutputBuffer;
                int needed = (mSmallWriteSize - ptr);

                if ((len - needed) < mSmallWriteSize) {
                    // Would have too little left, let's just copy it all
                    len += offset;
                    do {
                        outBuf[ptr++] = cbuf[offset++];
                    } while (offset < len);
                    mOutputPtr = ptr;
                    return;
                }
                // Just need minimal copy:
                int last = ptr + needed;
                do {
                    outBuf[ptr++] = cbuf[offset++];
                } while (ptr < last);
                mOutputPtr = ptr;
                len -= needed;
            }
            flushBuffer();
        }

        // And then we'll just write whatever we have left:
        mOut.write(cbuf, offset, len);
    }

    public void writeRaw(String str, int offset, int len)
        throws IOException
    {
        // First; is the new request small or not? If yes, needs to be buffered
        if (len < mSmallWriteSize) { // yup
            // Does it fit in with current buffer? If not, need to flush first
            if ((mOutputPtr + len) >= mOutputBuffer.length) {
                flushBuffer();
            }
            int ptr = mOutputPtr;
            /* Note: since it's a small copy, probably faster to copy without
             * System.arraycopy (which uses JNI)
             */
            char[] outBuf = mOutputBuffer;
            for (len += offset; offset < len; ++offset, ++ptr) {
                outBuf[ptr] = str.charAt(offset);
            }
            mOutputPtr = ptr;
            return;
        }

        // Ok, not a small request. But buffer may have existing content?
        int ptr = mOutputPtr;
        if (ptr > 0) {
            // If it's a small chunk, need to fill enough before flushing
            if (ptr < mSmallWriteSize) {
                /* Also, if we are to copy any stuff, let's make sure
                 * that we either copy it all in one chunk, or copy
                 * enough for non-small chunk, flush, and output remaining
                 * non-small chink (former possible if chunk we were requested
                 * to output is only slightly over 'small' size)
                 */
                char[] outBuf = mOutputBuffer;
                int needed = (mSmallWriteSize - ptr);

                if ((len - needed) < mSmallWriteSize) {
                    // Would have too little left, let's just copy it all
                    len += offset;
                    do {
                        outBuf[ptr++] = str.charAt(offset++);
                    } while (offset < len);
                    mOutputPtr = ptr;
                    return;
                }
                // Just need minimal copy:
                int last = ptr + needed;
                do {
                    outBuf[ptr++] = str.charAt(offset++);
                } while (ptr < last);
                mOutputPtr = ptr;
                len -= needed;
            }
            flushBuffer();
        }

        // And then we'll just write whatever we have left:
        mOut.write(str, offset, len);
    }

    /*
    ////////////////////////////////////////////////
    // "Trusted" low-level output methods
    ////////////////////////////////////////////////
     */

    public final void writeCDataStart()
        throws IOException
    {
        fastWriteRaw("<![CDATA[");
    }

    public final void writeCDataEnd()
        throws IOException
    {
        fastWriteRaw("]]>");
    }

    public final void writeCommentStart()
        throws IOException
    {
        fastWriteRaw("<!--");
    }

    public final void writeCommentEnd()
        throws IOException
    {
        fastWriteRaw("-->");
    }

    public final void writePIStart(String target, boolean addSpace)
        throws IOException
    {
        fastWriteRaw('<', '?');
        fastWriteRaw(target);
        if (addSpace) {
            fastWriteRaw(' ');
        }
    }

    public final void writePIEnd()
        throws IOException
    {
        fastWriteRaw('?', '>');
    }

    /*
    ////////////////////////////////////////////////
    // Higher-level output methods, text output
    ////////////////////////////////////////////////
     */

    public int writeCData(String data)
        throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCDataContent(data);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedCData(data, ix);
                return -1;
            }
        }
        fastWriteRaw("<![CDATA[");
        writeRaw(data, 0, data.length());
        fastWriteRaw("]]>");
        return -1;
    }

    public int writeCData(char[] cbuf, int offset, int len)
        throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCDataContent(cbuf, offset, len);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedCData(cbuf, offset, len, ix);
                return -1;
            }
        }
        fastWriteRaw("<![CDATA[");
        writeRaw(cbuf, offset, len);
        fastWriteRaw("]]>");
        return -1;
    }    

    public void writeCharacters(String text)
        throws IOException
    {
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(text);
        } else { // nope, default:
            int offset = 0;
            int len = text.length();
            do {
                int c = 0;
                int highChar = mEncHighChar;
                int start = offset;
                String ent = null;
                
                for (; offset < len; ++offset) {
                    c = text.charAt(offset); 
                    if (c <= HIGHEST_ENCODABLE_TEXT_CHAR) {
                        if (c == '<') {
                            ent = "&lt;";
                            break;
                        } else if (c == '&') {
                            ent = "&amp;";
                            break;
                        } else if (c == '>') {
                            /* Let's be conservative; and if there's any
                             * change it might be part of "]]>" quote it
                             */
                            if ((offset == start) || text.charAt(offset-1) == ']') {
                                ent = "&gt;";
                                break;
                            }
                        } else if (c < 0x0020) {
                            if (c == '\n' || c == '\t') { // fine as is
                                ;
                            } else {
                                if (c != '\r' && (!mXml11 || c == 0)) {
                                    throwInvalidChar(c);
                                }
                                break; // need quoting ok
                            }
                        }
                    } else if (c >= highChar) {
                        break;
                    }
                    // otherwise ok
                }
                int outLen = offset - start;
                if (outLen > 0) {
                    writeRaw(text, start, outLen);
                } 
                if (ent != null) {
                    writeRaw(ent);
                    ent = null;
                } else if (offset < len) {
                    writeAsEntity(c);
                }
            } while (++offset < len);
        }
    }    

    public void writeCharacters(char[] cbuf, int offset, int len)
        throws IOException
    {
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(cbuf, offset, len);
        } else { // nope, default:
            len += offset;
            do {
                int c = 0;
                int highChar = mEncHighChar;
                int start = offset;
                String ent = null;
                
                for (; offset < len; ++offset) {
                    c = cbuf[offset];
                    if (c <= HIGHEST_ENCODABLE_TEXT_CHAR) {
                        if (c == '<') {
                            ent = "&lt;";
                            break;
                        } else if (c == '&') {
                            ent = "&amp;";
                            break;
                        } else if (c == '>') {
                            /* Let's be conservative; and if there's any
                             * change it might be part of "]]>" quote it
                             */
                            if ((offset == start) || cbuf[offset-1] == ']') {
                                ent = "&gt;";
                                break;
                            }
                        } else if (c < 0x0020) {
                            if (c == '\n' || c == '\t') { // fine as is
                                ;
                            } else {
                                if (c != '\r' && (!mXml11 || c == 0)) {
                                    throwInvalidChar(c);
                                }
                                break; // need quoting ok
                            }
                        }
                    } else if (c >= highChar) {
                        break;
                    }
                    // otherwise ok
                }
                int outLen = offset - start;
                if (outLen > 0) {
                    writeRaw(cbuf, start, outLen);
                } 
                if (ent != null) {
                    writeRaw(ent);
                    ent = null;
                } else if (offset < len) {
                    writeAsEntity(c);
                }
            } while (++offset < len);
        }
    }    

    /**
     * Method that will try to output the content as specified. If
     * the content passed in has embedded "--" in it, it will either
     * add an intervening space between consequtive hyphens (if content
     * fixing is enabled), or return the offset of the first hyphen in
     * multi-hyphen sequence.
     */
    public int writeComment(String data)
        throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCommentContent(data);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedComment(data, ix);
                return -1;
            }
        }
        fastWriteRaw("<!--");
        writeRaw(data, 0, data.length());
        fastWriteRaw("-->");
        return -1;
    }

    public void writeDTD(String data)
        throws IOException
    {
        writeRaw(data, 0, data.length());
    }    

    public void writeDTD(String rootName, String systemId, String publicId,
                         String internalSubset)
        throws IOException, XMLStreamException
    {
        fastWriteRaw("<!DOCTYPE ");
        if (mCheckNames) {
            /* 20-Apr-2005, TSa: Can only really verify that it has at most
             *    one colon in ns-aware mode (and not even that in non-ns
             *    mode)... so let's just ignore colon count, and check
             *    that other chars are valid at least
             */
            verifyNameValidity(rootName, false);
        }
        fastWriteRaw(rootName);
        if (systemId != null) {
            if (publicId != null) {
                fastWriteRaw(" PUBLIC \"");
                fastWriteRaw(publicId);
                fastWriteRaw("\" \"");
            } else {
                fastWriteRaw(" SYSTEM \"");
            }
            fastWriteRaw(systemId);
            fastWriteRaw('"');
        }
        // Hmmh. Should we output empty internal subset?
        if (internalSubset != null && internalSubset.length() > 0) {
            fastWriteRaw(' ', '[');
            fastWriteRaw(internalSubset);
            fastWriteRaw(']');
        }
        fastWriteRaw('>');
    }

    public void writeEntityReference(String name)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            verifyNameValidity(name, mNsAware);
        }
        fastWriteRaw('&');
        fastWriteRaw(name);
        fastWriteRaw(';');
    }    

    public void writeXmlDeclaration(String version, String encoding, String standalone)
        throws IOException
    {
        fastWriteRaw("<?xml version='");
        fastWriteRaw(version);
        fastWriteRaw('\'');
        
        if (encoding != null && encoding.length() > 0) {
            fastWriteRaw(" encoding='");
            fastWriteRaw(encoding);
            fastWriteRaw('\'');
        }
        if (standalone != null) {
            fastWriteRaw(" standalone='");
            fastWriteRaw(standalone);
            fastWriteRaw('\'');
        }
        fastWriteRaw('?', '>');
    }    

    public int writePI(String target, String data)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            // As per namespace specs, can not have colon(s)
            verifyNameValidity(target, mNsAware);
        }
        fastWriteRaw('<', '?');
        fastWriteRaw(target);
        if (data != null && data.length() > 0) {
            if (mCheckContent) {
                int ix = data.indexOf('?');
                if (ix >= 0) {
                    ix = data.indexOf("?>", ix);
                    if (ix >= 0) {
                        return ix;
                    }
                }
            }
            fastWriteRaw(' ');
            // Data may be longer, let's call regular writeRaw method
            writeRaw(data, 0, data.length());
        }
        fastWriteRaw('?', '>');
        return -1;
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, elements
    ////////////////////////////////////////////////////
     */

    public void writeStartTagStart(String prefix, String localName)
        throws IOException, XMLStreamException
    {
        fastWriteRaw('<');
        if (prefix != null && prefix.length() > 0) {
            if (mCheckNames) {
                verifyNameValidity(prefix, mNsAware);
            }
            fastWriteRaw(prefix);
            fastWriteRaw(':');
        }
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }
        fastWriteRaw(localName);
    }    

    public void writeStartTagEnd()
        throws IOException
    {
        fastWriteRaw('>');
    }    

    public void writeStartTagEmptyEnd()
        throws IOException
    {
        fastWriteRaw(" />");
    }    

    public void writeEndTag(String prefix, String localName)
        throws IOException
    {
        fastWriteRaw('<', '/');
        /* At this point, it is assumed caller knows that end tag
         * matches with start tag, and that it (by extension) has been
         * validated if and as necessary
         */
        if (prefix != null && prefix.length() > 0) {
            fastWriteRaw(prefix);
            fastWriteRaw(':');
        }
        fastWriteRaw(localName);
        fastWriteRaw('>');
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, attributes/ns
    ////////////////////////////////////////////////////
     */

    public void writeAttribute(String prefix, String localName, String value)
        throws IOException, XMLStreamException
    {
        fastWriteRaw(' ');
        if (prefix != null && prefix.length() > 0) {
            if (mCheckNames) {
                verifyNameValidity(prefix, mNsAware);
            }
            fastWriteRaw(prefix);
            fastWriteRaw(':');
        }
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }
        fastWriteRaw(localName);
        fastWriteRaw('=', '"');

        int len = (value == null) ? 0 : value.length();
        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, 0, len);
            } else { // nope, default
                final char qchar = mEncQuoteChar;
                int offset = 0;
                int highChar = mEncHighChar;

                do {
                    int start = offset;
                    int c = 0;
                    String ent = null;

                    for (; offset < len; ++offset) {
                        c = value.charAt(offset);
                        if (c <= HIGHEST_ENCODABLE_ATTR_CHAR) { // special char?
                            if (c == qchar) {
                                ent = mEncQuoteEntity;
                                break;
                            } else if (c == '<') {
                                ent = "&lt;";
                                break;
                            } else if (c == '&') {
                                ent = "&amp;";
                                break;
                            } else if (c < 0x0020) { // tab, cr/lf need encoding too
                                if (c != '\n' && c != '\r' && c != '\t') {
                                    if (!mXml11 || c == 0) {
                                        throwInvalidChar(c);
                                    }
                                }
                                break; // need quoting ok
                            }
                        } else if (c >= highChar) { // out of range, have to escape
                            break;
                        }
                    }
                    // otherwise ok
                    int outLen = offset - start;
                    if (outLen > 0) {
                        writeRaw(value, start, outLen);
                    }
                    if (ent != null) {
                        fastWriteRaw(ent);
                        ent = null;
                    } else if (offset < len) {
                        writeAsEntity(c);
                    }
                } while (++offset < len);
            }
        }

        fastWriteRaw('"');
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods, buffering
    ////////////////////////////////////////////////////
     */

    private final void flushBuffer()
        throws IOException
    {
        if (mOutputPtr > 0) {
            int ptr = mOutputPtr;
            // Need to update location info, to keep it in sync
            mLocPastChars += ptr;
            mLocRowStartOffset -= ptr;
            mOutputPtr = 0;
            mOut.write(mOutputBuffer, 0, ptr);
        }
    }

    private final void fastWriteRaw(char c)
        throws IOException
    {
        if (mOutputPtr >= mOutputBuffer.length) {
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = c;
    }

    private final void fastWriteRaw(char c1, char c2)
        throws IOException
    {
        if ((mOutputPtr + 1) >= mOutputBuffer.length) {
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = c1;
        mOutputBuffer[mOutputPtr++] = c2;
    }

    private final void fastWriteRaw(String str)
        throws IOException
    {
        int len = str.length();
        int ptr = mOutputPtr;
        char[] buf = mOutputBuffer;
        if ((ptr + len) >= buf.length) {
            /* It's even possible that String is longer than the buffer (not
             * likely, possible). If so, let's just call the full
             * method:
             */
            if (len > buf.length) {
                writeRaw(str, 0, len);
                return;
            }
            flushBuffer();
            ptr = mOutputPtr;
        }
        mOutputPtr += len;
        for (int i = 0; i < len; ++i) {
            buf[ptr++] = str.charAt(i);
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods, content verification/fixing
    ////////////////////////////////////////////////////
     */

    /**
     * @return Index at which a problem was found, if any; -1 if there's
     *   no problem.
     */
    protected int verifyCDataContent(String content)
    {
        if (content != null && content.length() >= 3) {
            int ix = content.indexOf(']');
            if (ix >= 0) {
                return content.indexOf("]]>", ix);
            }
        }
        return -1;
    }

    protected int verifyCDataContent(char[] c, int start, int end)
    {
        if (c != null) {
            start += 2;
            /* Let's do simple optimization for search...
             * (bayer-moore search algorithm)
             */
            while (start < end) {
                char ch = c[start];
                if (ch == ']') {
                    ++start; // let's just move by one in this case
                    continue;
                }
                if (ch == '>') { // match?
                    if (c[start-1] == ']' 
                        && c[start-2] == ']') {
                        return start-2;
                    }
                }
                start += 2;
            }
        }
        return -1;
    }
    
    protected int verifyCommentContent(String content)
    {
        int ix = content.indexOf('-');
        if (ix >= 0) {
            /* actually, it's illegal to just end with '-' too, since 
             * that would cause invalid end marker '--->'
             */
            if (ix < (content.length() - 1)) {
                ix = content.indexOf("--", ix);
            }
        }
        return ix;
    }

    protected void writeSegmentedCData(String content, int index)
        throws IOException
    {
        /* It's actually fairly easy, just split "]]>" into 2 pieces;
         * for each ']]>'; first one containing "]]", second one ">"
         * (as long as necessary)
         */
        int start = 0;
        while (index >= 0) {
            fastWriteRaw("<![CDATA[");
            writeRaw(content, start, (index+2) - start);
            fastWriteRaw("]]>");
            start = index+2;
            index = content.indexOf("]]>", start);
        }
        // Ok, then the last segment
        fastWriteRaw("<![CDATA[");
        writeRaw(content, start, content.length()-start);
        fastWriteRaw("]]>");
    }

    protected void writeSegmentedCData(char[] c, int start, int len, int index)
        throws IOException
    {
        int end = start + len;
        while (index >= 0) {
            fastWriteRaw("<![CDATA[");
            writeRaw(c, start, (index+2) - start);
            fastWriteRaw("]]>");
            start = index+2;
            index = verifyCDataContent(c, start, end);
        }
        // Ok, then the last segment
        fastWriteRaw("<![CDATA[");
        writeRaw(c, start, end-start);
        fastWriteRaw("]]>");
    }

    protected void writeSegmentedComment(String content, int index)
        throws IOException
    {
        int len = content.length();
        // First the special case (last char is hyphen):
        if (index == (len-1)) {
            fastWriteRaw("<!--");
            writeRaw(content, 0, len);
            // we just need to inject one space in there
            fastWriteRaw(" -->");
            return;
        }
        
        /* Fixing comments is more difficult than that of CDATA segments';
         * this because CDATA can still contain embedded ']]'s, but
         * comment neither allows '--' nor ending with '-->'; which means
         * that it's impossible to just split segments. Instead we'll do
         * something more intrusive, and embed single spaces between all
         * '--' character pairs... it's intrusive, but comments are not
         * supposed to contain any data, so that should be fine (plus
         * at least result is valid, unlike contents as is)
         */
        int start = 0;
        while (index >= 0) {
            fastWriteRaw("<!--");
            // first, content prior to '--' and the first hyphen
            writeRaw(content, start, (index+1) - start);
            // and an obligatory trailing space to split double-hyphen
            fastWriteRaw(' ');
            // still need to handle rest of consequtive double'-'s if any
            start = index+1;
            index = content.indexOf("--", start);
        }
        // Ok, then the last segment
        writeRaw(content, start, len-start);
        // ends with a hyphen? that needs to be fixed, too
        if (content.charAt(len-1) == '-') {
            fastWriteRaw(' ');
        }
        fastWriteRaw("-->");
    }

    /**
     * Method used to figure out which part of the Unicode char set the
     * encoding can natively support. Values returned are 7, 8 and 16,
     * to indicate (respectively) "ascii", "ISO-Latin" and "native Unicode".
     * These just best guesses, but should work ok for the most common
     * encodings.
     */
    public static int guessEncodingBitSize(String enc)
    {
        if (enc == null || enc.length() == 0) { // let's assume default is UTF-8...
            return 16;
        }
        // Let's see if we can find a normalized name, first:
        enc = CharsetNames.normalize(enc);

        // Ok, first, do we have known ones; starting with most common:
        if (enc == CharsetNames.CS_UTF8) {
            return 16; // meaning up to 2^16 can be represented natively
        } else if (enc == CharsetNames.CS_ISO_LATIN1) {
            return 8;
        } else if (enc == CharsetNames.CS_US_ASCII) {
            return 7;
        } else if (enc == CharsetNames.CS_UTF16
                   || enc == CharsetNames.CS_UTF16BE
                   || enc == CharsetNames.CS_UTF16LE
                   || enc == CharsetNames.CS_UTF32BE
                   || enc == CharsetNames.CS_UTF32LE) {
            return 16;
        }

        /* Above and beyond well-recognized names, it might still be
         * good to have more heuristics for as-of-yet unhandled cases...
         * But, it's probably easier to only assume 8-bit clean (could
         * even make it just 7, let's see how this works out)
         */
        return 8;
    }

    protected final void writeAsEntity(int c)
        throws IOException
    {
        char[] cbuf = mOutputBuffer;
        int ptr = mOutputPtr;
        if ((ptr + 8) >= cbuf.length) {
            flushBuffer();
            ptr = mOutputPtr;
        }
        cbuf[ptr++] = '&';
        cbuf[ptr++] = '#';
        cbuf[ptr++] = 'x';
        // Can use shorter quoting for tab, cr, lf:
        if (c < 16) {
            cbuf[ptr++] = (char) ((c < 10) ?
                                  ('0' + c) :
                                  (('a' - 10) + c));
        } else {
            int digits;

            if (c < (1 << 8)) {
                digits = 2;
            } else if (c < (1 << 12)) {
                digits = 3;
            } else if (c < (1 << 16)) {
                digits = 4;
            } else {
                digits = 6;
            }
            ptr += digits;
            for (int i = 1; i <= digits; ++i) {
                int digit = (c & 0xF);
                c >>= 4;
                cbuf[ptr-i] = (char) ((digit < 10) ?
                                      ('0' + digit) :
                                      (('a' - 10) + digit));
            }
        }
        cbuf[ptr++] = ';';
        mOutputPtr = ptr;
    }
}
