/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.dtd;

import java.util.Map;

import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.AttributeCollector;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.SymbolTable;

/**
 * Base class for objects that contain attribute definitions from DTD.
 * Sub-classes exists for specific typed attributes (enumeration-valued,
 * non-CDATA ones); base class itself is used for attributes of type
 * CDATA.
 */
public class DTDAttribute
{
    /*
    ///////////////////////////////////////////////////
    // Type constants
    ///////////////////////////////////////////////////
     */

    // // // Value types

    public final static int TYPE_CDATA = 0; // default...
    public final static int TYPE_ENUMERATED = 1;

    public final static int TYPE_ID = 2;
    public final static int TYPE_IDREF = 3;
    public final static int TYPE_IDREFS = 4;

    public final static int TYPE_ENTITY = 5;
    public final static int TYPE_ENTITIES = 6;

    public final static int TYPE_NOTATION = 7;
    public final static int TYPE_NMTOKEN = 8;
    public final static int TYPE_NMTOKENS = 9;

    // // // Default value types

    public final static int DEF_DEFAULT = 1;
    public final static int DEF_IMPLIED = 2;
    public final static int DEF_REQUIRED = 3;
    public final static int DEF_FIXED = 4;

    /**
     * Array that has String constants matching above mentioned
     * value types
     */
    final static String[] sTypes = new String[] {
        "CDATA",
        "ENUMERATED", // !!! 23-Jan-2005, TSa: What's the official type? CDATA?
        "ID",
        "IDREF",
        "IDREFS",
        "ENTITY",
        "ENTITIES",
        "NOTATION",
        "NMTOKEN",
        "NMTOKENS",
    };

    /*
    ///////////////////////////////////////////////////
    // Information about the attribute itself
    ///////////////////////////////////////////////////
     */

    protected final NameKey mName;

    /**
     * Index number amongst "special" attributes (required ones, attributes
     * that have default values), if attribute is one: -1 if not.
     */
    protected final int mSpecialIndex;

    protected final int mDefValueType;

    /**
     *<p>
     * Note: Can not be made final since validation code may want to trim
     * it down a bit...
     */
    protected String mDefValue;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public DTDAttribute(NameKey name, int defValueType,
                        String defValue, int specIndex)
    {
        mName = name;
        mDefValueType = defValueType;
        mDefValue = defValue;
        mSpecialIndex = specIndex;
    }

    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDAttribute(mName, mDefValueType, mDefValue, specIndex);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////////
     */

    public final NameKey getName() { return mName; }

    public final String toString() {
        return mName.toString();
    }

    public final int getDefaultType() {
        return mDefValueType;
    }

    public final String getDefaultValue() {
        return mDefValue;
    }

    /**
     * Method used by the element to figure out if attribute needs "special"
     * checking; basically if it's required, and/or has a default value.
     * In both cases missing the attribute has specific consequences, either
     * exception or addition of a default value.
     */
    public final boolean isSpecial() {
        return isSpecial(mDefValueType);
    }

    public static boolean isSpecial(int defValueType) {
        return (defValueType == DEF_DEFAULT)
            || (defValueType == DEF_FIXED)
            || (defValueType == DEF_REQUIRED);
    }

    public final boolean isRequired() {
        return (mDefValueType == DEF_REQUIRED);
    }

    public final boolean isFixed() {
        return (mDefValueType == DEF_FIXED);
    }

    public final int getSpecialIndex() {
        return mSpecialIndex;
    }

    public final boolean needsValidation() {
        return (getValueType() != TYPE_CDATA);
    }

    /**
     * Returns the value type of this attribute as an enumerated int
     * to match type (CDATA, ...)
     *<p>
     * Note: 
     */
    public int getValueType() {
        return TYPE_CDATA;
    }

    public String getValueTypeString()
    {
        return sTypes[getValueType()];
    }

    public boolean typeIsId() {
        return false;
    }

    public boolean typeIsNotation() {
        return false;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    /**
     * Method called by the {@link ElementValidator}
     * to let the attribute do necessary normalization and/or validation
     * for the value.
     */
    public void validate(ElementValidator v, boolean normalize, AttributeCollector ac,
                         int index)
        throws WstxException
    {
        /* Nothing to do for the base class; all values are fine...
         * except if the value has to be fixed
         */
    }

    /**
     * Method called by the {@link ElementValidator}
     * to ask attribute to verify that the default it has (if any) is
     * valid for such type.
     */
    public void validateDefault(InputProblemReporter rep, boolean normalize)
        throws WstxException
    {
        // Nothing to do for the base class; all values are fine
    }

    /*
    ///////////////////////////////////////////////////
    // Package methods, validation helper methods
    ///////////////////////////////////////////////////
     */

    protected String validateDefaultName(InputProblemReporter rep, boolean normalize)
        throws WstxException
    {
        String defValue = mDefValue.trim();

        if (defValue.length() == 0) {
            reportParseError(rep, "Invalid default value '"+defValue
                             +"'; empty String is not a valid name");
        }

        // Ok, needs to be a valid XML name:
        char c = defValue.charAt(0);
        if (!WstxInputData.is11NameChar(c) && c != ':') {
            reportParseError(rep, "Invalid default value '"+defValue+"'; character "
                                +WstxInputData.getCharDesc(c)
                                +") not valid first character of a name");
        }

        for (int i = 1, len = defValue.length(); i < len; ++i) {
            if (!WstxInputData.is11NameChar(defValue.charAt(i))) {
                reportParseError(rep, "Invalid default value '"+defValue+"'; character #"+i+" ("
                                    +WstxInputData.getCharDesc(defValue.charAt(i))
                                   +") not valid name character");
            }
        }

        // Ok, cool it's ok...
        return normalize ? defValue : mDefValue;
    }

    protected String validateDefaultNames(InputProblemReporter rep, boolean normalize)
        throws WstxException
    {
        String defValue = mDefValue;
        int len = defValue.length();

        // Then code similar to actual value validation:
        StringBuffer sb = null;
        int count = 0;
        int start = 0;

        main_loop:
        while (start < len) {
            char c = defValue.charAt(start);

            // Ok, any white space to skip?
            while (true) {
                if (!WstxInputData.isSpaceChar(c)) {
                    break;
                }
                if (++start >= len) {
                    break main_loop;
                }
                c = defValue.charAt(start);
            }

            if (!WstxInputData.is11NameStartChar(c) && c != ':') {
                reportParseError(rep, "Invalid default value '"+defValue
                                 +"'; character "
                                 +WstxInputData.getCharDesc(c)
                                 +") not valid first character of a name token");
            }
            int i = start+1;
            for (; i < len; ++i) {
                c = defValue.charAt(i);
                if (WstxInputData.isSpaceChar(c)) {
                    break;
                }
                if (!WstxInputData.is11NameChar(c)) {
                    reportParseError(rep, "Invalid default value '"+defValue
                                     +"'; character "
                                     +WstxInputData.getCharDesc(c)
                                     +") not a valid name character");
                }
            }

            ++count;

            if (normalize) {
                if (sb == null) {
                    sb = new StringBuffer(i - start + 32);
                } else {
                    sb.append(' ');
                }
                sb.append(defValue.substring(start, i));
            }
            start = i+1;
        }

        if (count == 0) {
            reportParseError(rep, "Invalid default value '"+defValue
                             +"'; empty String is not a valid name value");
        }

        return normalize ? sb.toString() : mDefValue;
    }

    protected String validateDefaultNmToken(InputProblemReporter rep, boolean normalize)
        throws WstxException
    {
        String defValue = mDefValue.trim();

        if (defValue.length() == 0) {
            reportParseError(rep, "Invalid default value '"+defValue+"'; empty String is not a valid NMTOKEN");
        }

        // Ok, needs to be a valid NMTOKEN:
        for (int i = 0, len = defValue.length(); i < len; ++i) {
            if (!WstxInputData.is11NameChar(defValue.charAt(i))) {
                reportParseError(rep, "Invalid default value '"+defValue
                                    +"'; character #"+i+" ("
                                   +WstxInputData.getCharDesc(defValue.charAt(i))
                                   +") not valid NMTOKEN character");
            }
        }
        // Ok, cool it's ok...
        return normalize ? defValue : mDefValue;
    }

    protected EntityDecl findEntityDecl(ElementValidator v,
                                        char[] ch, int start, int len, int hash)
        throws WstxException
    {
        // Let's assume symbol should have been intern'ed earlier...
        SymbolTable st = v.getSymbolTable();
        Map entMap = v.getEntityMap();
        String id = st.findSymbolIfExists(ch, start, len, hash);
        EntityDecl ent;

        if (id != null) {
            ent = (EntityDecl) entMap.get(id);
        } else {
            // But if not interned, it's still possible it's valid
            id = new String(ch, start, len);
            ent = (EntityDecl) entMap.get(id);
            if (ent != null) {
                // ok, somehow symbol table wasn't up to date; let's fix that
                id = st.findSymbol(id);
            }
        }

        if (ent == null) {
            reportParseError(v, "Referenced entity '"+id+"' not defined");
        }
        if (ent.isParsed()) {
            reportParseError(v, "Referenced entity '"+id+"' is not an unparsed entity");
        }
        return ent;
    }

    /* Too bad this method can not be combined with previous segment --
     * the reason is that ElementValidator does not implement
     * InputProblemReporter...
     */

    protected void checkEntity(InputProblemReporter rep, String id, EntityDecl ent)
        throws WstxException
    {
        if (ent == null) {
            rep.throwParseError("Referenced entity '"+id+"' not defined");
        }
        if (ent.isParsed()) {
            rep.throwParseError("Referenced entity '"+id+"' is not an unparsed entity");
        }
    }

    /*
    ///////////////////////////////////////////////////
    // Package methods, error reporting
    ///////////////////////////////////////////////////
     */

    protected void reportInvalidChar(ElementValidator v, char c, String msg)
        throws WstxException
    {
        reportParseError(v, "Invalid character "+WstxInputData.getCharDesc(c)+": "+msg);
    }

    protected void reportParseError(ElementValidator v, String msg)
        throws WstxException
    {
        v.throwParseError("Attribute '"+mName+"': "+msg);
    }

    protected void reportParseError(InputProblemReporter rep, String msg)
        throws WstxException
    {
        rep.throwParseError("Attribute '"+mName+"': "+msg);
    }

    /*
    ///////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////
     */

}
