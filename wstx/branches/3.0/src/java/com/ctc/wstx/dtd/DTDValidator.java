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

package com.ctc.wstx.dtd;

import java.text.MessageFormat;
import java.util.*;

import javax.xml.stream.Location;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.util.DataUtil;
import com.ctc.wstx.util.StringUtil;

/**
 * Woodstox implementation of {@link XMLValidator}; the class that
 * handles DTD-based validation.
 */
public class DTDValidator
    extends DTDValidatorBase
{
    /*
    /////////////////////////////////////////////////////
    // Constants
    /////////////////////////////////////////////////////
     */

    /*
    ///////////////////////////////////////
    // Configuration
    ///////////////////////////////////////
    */

    /**
     * Determines if identical problems (definition of the same element,
     * for example) should cause multiple error notifications or not:
     * if true, will get one error per instance, if false, only the first
     * one will get reported.
     */
    protected boolean mReportDuplicateErrors = false;

    /*
    ///////////////////////////////////////
    // Id/idref state
    ///////////////////////////////////////
    */

    /**
     * Information about declared and referenced element ids (unique
     * ids that attributes may defined, as defined by DTD)
     */
    protected ElementIdMap mIdMap = null;

    /*
    ///////////////////////////////////////////
    // Element def/spec/validator stack, state
    ///////////////////////////////////////////
    */

    /**
     * Stack of validators for open elements
     */
    protected StructValidator[] mValidators = null;

    /**
     * Bitset used for keeping track of required and defaulted attributes
     * for which values have been found.
     */
    protected BitSet mCurrSpecialAttrs = null;

    boolean mCurrHasAnyFixed = false;

    /*
    ///////////////////////////////////////
    // Temporary helper objects
    ///////////////////////////////////////
    */

    /**
     * Reusable lazily instantiated BitSet; needed to keep track of
     * missing 'special' attributes (required ones, ones with default
     * values)
     */
    BitSet mTmpSpecialAttrs;

    /*
    ///////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////
    */

    public DTDValidator(DTDSubset schema, ValidationContext ctxt, boolean hasNsDefaults,
                        Map elemSpecs, Map genEntities)
    {
        super(schema, ctxt, hasNsDefaults, elemSpecs, genEntities);
        mValidators = new StructValidator[DEFAULT_STACK_SIZE];
    }

    /*
    ///////////////////////////////////////
    // XMLValidator implementation
    ///////////////////////////////////////
    */

    //public XMLValidationSchema getSchema();

    /**
     * Method called to update information about the newly encountered (start)
     * element. At this point namespace information has been resolved, but
     * no DTD validation has been done. Validator is to do these validations,
     * including checking for attribute value (and existence) compatibility.
     */
    public void validateElementStart(String localName, String uri, String prefix)
        throws XMLValidationException
    {
        /* Ok, need to find the element definition; if not found (or
         * only implicitly defined), need to throw the exception.
         */
        mTmpKey.reset(prefix, localName);

        DTDElement elem = (DTDElement) mElemSpecs.get(mTmpKey);

        /* Let's add the entry in (even if it's a null); this is necessary
         * to keep things in-sync if allowing graceful handling of validity
         * errors
         */
        int elemCount = mElemCount++;
        if (elemCount >= mElems.length) {
            mElems = (DTDElement[]) DataUtil.growArrayBy50Pct(mElems);
            mValidators = (StructValidator[]) DataUtil.growArrayBy50Pct(mValidators);
        }
        mElems[elemCount] = mCurrElem = elem;
        if (elem == null || !elem.isDefined()) {
            reportValidationProblem(ErrorConsts.ERR_VLD_UNKNOWN_ELEM, mTmpKey.toString());
        }

        // Is this element legal under the parent element?
        StructValidator pv = (elemCount > 0) ? mValidators[elemCount-1] : null;

        if (pv != null) {
            String msg = pv.tryToValidate(elem.getName());
            if (msg != null) {
                int ix = msg.indexOf("$END");
                String pname = mElems[elemCount-1].toString();
                if (ix >= 0) {
                    msg = msg.substring(0, ix) + "</"+pname+">"
                        +msg.substring(ix+4);
                }
                reportValidationProblem("Validation error, encountered element <"
                                        +elem.getName()+"> as a child of <"
                                        +pname+">: "+msg);
            }
        }

        mAttrCount = 0;
        mIdAttrIndex = -2; // -2 as a "don't know yet" marker

        // Ok, need to get the child validator, then:
        if (elem == null) {
            mValidators[elemCount] = null;
            mCurrAttrDefs = EMPTY_MAP;
            mCurrHasAnyFixed = false;
            mCurrSpecialAttrs = null;
        } else {
            mValidators[elemCount] = elem.getValidator();
            mCurrAttrDefs = elem.getAttributes();
            if (mCurrAttrDefs == null) {
                mCurrAttrDefs = EMPTY_MAP;
            }
            mCurrHasAnyFixed = elem.hasFixedAttrs();
            int specCount = elem.getSpecialCount();
            if (specCount == 0) {
                mCurrSpecialAttrs = null;
            } else {
                BitSet bs = mTmpSpecialAttrs;
                if (bs == null) {
                    mTmpSpecialAttrs = bs = new BitSet(specCount);
                } else {
                    bs.clear();
                }
                mCurrSpecialAttrs = bs;
            }
        }
    }

    public String validateAttribute(String localName, String uri,
                                    String prefix, String value)
        throws XMLValidationException
    {
        DTDAttribute attr = (DTDAttribute) mCurrAttrDefs.get(mTmpKey.reset(prefix, localName));
        if (attr == null) {
            // Only report error if not already covering from an error:
            if (mCurrElem == null) {
                return null;
            }
            reportValidationProblem(ErrorConsts.ERR_VLD_UNKNOWN_ATTR,
                                    mCurrElem.toString(), mTmpKey.toString());
        }
        int index = mAttrCount++;
        if (index >= mAttrSpecs.length) {
            mAttrSpecs = (DTDAttribute[]) DataUtil.growArrayBy50Pct(mAttrSpecs);
        }
        mAttrSpecs[index] = attr;
        if (mCurrSpecialAttrs != null) { // Need to mark that we got it
            int specIndex = attr.getSpecialIndex();
            if (specIndex >= 0) {
                mCurrSpecialAttrs.set(specIndex);
            }
        }
        String result = attr.validate(this, value, mNormAttrs);
        if (mCurrHasAnyFixed && attr.isFixed()) {
            String act = (result == null) ? value : result;
            String exp = attr.getDefaultValue(mContext);
            if (!act.equals(exp)) {
                reportValidationProblem("Value of attribute \""+attr+"\" (element <"+mCurrElem+">) not \""+exp+"\" as expected, but \""+act+"\"");
            }
        }
        return result;
    }

    public String validateAttribute(String localName, String uri,
                                    String prefix,
                                    char[] valueChars, int valueStart,
                                    int valueEnd)
        throws XMLValidationException
    {
        DTDAttribute attr = (DTDAttribute) mCurrAttrDefs.get(mTmpKey.reset(prefix, localName));
        if (attr == null) {
            // Only report error if not already covering from an error:
            if (mCurrElem == null) {
                return null;
            }
            reportValidationProblem(ErrorConsts.ERR_VLD_UNKNOWN_ATTR,
                                    mCurrElem.toString(), mTmpKey.toString());
        }
        int index = mAttrCount++;
        if (index >= mAttrSpecs.length) {
            mAttrSpecs = (DTDAttribute[]) DataUtil.growArrayBy50Pct(mAttrSpecs);
        }
        mAttrSpecs[index] = attr;
        if (mCurrSpecialAttrs != null) { // Need to mark that we got it
            int specIndex = attr.getSpecialIndex();
            if (specIndex >= 0) {
                mCurrSpecialAttrs.set(specIndex);
            }
        }
        String result = attr.validate(this, valueChars, valueStart, valueEnd, mNormAttrs);
        if (mCurrHasAnyFixed && attr.isFixed()) {
            String exp = attr.getDefaultValue(mContext);
            boolean match;
            if (result == null) {
                match = StringUtil.matches(exp, valueChars, valueStart, valueEnd - valueStart);
            } else {
                match = exp.equals(result);
            }
            if (!match) {
                String act = (result == null) ? 
                    new String(valueChars, valueStart, valueEnd) : result;
                reportValidationProblem("Value of #FIXED attribute \""+attr+"\" (element <"+mCurrElem+">) not \""+exp+"\" as expected, but \""+act+"\"");
            }
        }
        return result;
    }
    
    public int validateElementAndAttributes()
        throws XMLValidationException
    {
        // Ok: are we fine with the attributes?
        DTDElement elem = mCurrElem;
        if (elem == null) { // had an error, most likely no such element defined...
            // need to just return, nothing to do here
            return XMLValidator.CONTENT_ALLOW_ANY_TEXT;
        }
        
        // Any special attributes missing?
        if (mCurrSpecialAttrs != null) {
            BitSet specBits = mCurrSpecialAttrs;
            int specCount = elem.getSpecialCount();
            int ix = specBits.nextClearBit(0);
            while (ix < specCount) { // something amiss!
                List specAttrs = elem.getSpecialAttrs();
                DTDAttribute attr = (DTDAttribute) specAttrs.get(ix);

                if (attr.isRequired()) {
                    reportValidationProblem("Required attribute '"+attr+"' missing from element <"+elem+">");
                }
                doAddDefaultValue(attr);
                ix = specBits.nextClearBit(ix+1);
            }
        }

        return elem.getAllowedContent();
    }

    /**
     * @return Validation state that should be effective for the parent
     *   element state
     */
    public int validateElementEnd(String localName, String uri, String prefix)
        throws XMLValidationException
    {
        // First, let's remove the top:
        int ix = --mElemCount;
        DTDElement closingElem = mElems[ix];
        mElems[ix] = null;
        StructValidator v = mValidators[ix];
        mValidators[ix] = null;

        // Validation?
        if (v != null) {
            String msg = v.fullyValid();
            if (msg != null) {
                reportValidationProblem("Validation error, element </"
                                        +closingElem+">: "+msg);
            }
        }

        // Then let's get info from parent, if any
        if (ix < 1) { // root element closing..
            checkIdRefs();

            // doesn't really matter; epilog/prolog differently handled:
            return XMLValidator.CONTENT_ALLOW_WS;
        }
        return mElems[ix-1].getAllowedContent();
    }

    //public void validateText(String text, boolean lastTextSegment) ;
    //public void validateText(char[] cbuf, int textStart, int textEnd, boolean lastTextSegment) ;

    public void validationCompleted(boolean eod)
        throws XMLValidationException
    {
        // 18-Dec-2005, TSa: Is there something we should do here...?
    }

    /*
    ///////////////////////////////////////
    // Package methods, accessors
    ///////////////////////////////////////
    */

    protected ElementIdMap getIdMap() {
        if (mIdMap == null) {
            mIdMap = new ElementIdMap();
        }
        return mIdMap;
    }

    /*
    ///////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////
    */

    protected void checkIdRefs()
        throws XMLValidationException
    {
        /* 02-Oct-2004, TSa: Now we can also check that all id references
         *    pointed to ids that actually are defined
         */
        if (mIdMap != null) {
            ElementId ref = mIdMap.getFirstUndefined();
            if (ref != null) { // problem!
                reportValidationProblem("Undefined id '"+ref.getId()
                                        +"': referenced from element <"
                                        +ref.getElemName()+">, attribute '"
                                        +ref.getAttrName()+"'",
                                        ref.getLocation());
            }
        }
    }

}
