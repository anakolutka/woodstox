package com.ctc.wstx.stax.io;

import javax.xml.stream.Location;

import com.ctc.wstx.util.StringUtil;

/**
 * Basic implementation of {@link Location}, used by Wstx readers.
 */
public class WstxInputLocation
    implements Location
{
    private final static WstxInputLocation sEmptyLocation
        = new WstxInputLocation(null, "", "", -1, -1, -1);

    /**
     * Enclosing (parent) input location; location from which current
     * location is derived.
     */
    final WstxInputLocation mContext;

    final String mPublicId, mSystemId;
    
    final int mCharOffset, mCol, mRow;

    transient String mDesc = null;

    /**
     * @param ctxt Enclosing input location, if any
     */
    public WstxInputLocation(WstxInputLocation ctxt,
                             String pubId, String sysId,
                             int charOffset, int row, int col)
    {
        mContext = ctxt;
        mPublicId = pubId;
        mSystemId = sysId;
        mCharOffset = charOffset;
        mCol = col;
        mRow = row;
    }

    public static WstxInputLocation getEmptyLocation() {
        return sEmptyLocation;
    }
    
    public int getCharacterOffset() { return mCharOffset; }
    public int getColumnNumber() { return mCol; }
    public int getLineNumber() { return mRow; }
    
    public String getPublicId() { return mPublicId; }
    public String getSystemId() { return mSystemId; }

    public WstxInputLocation getContext() { return mContext; }
    
    public String toString()
    {
        if (mDesc == null) {
            StringBuffer sb;
            if (mContext != null) {
                sb = new StringBuffer(200);
            } else {
                sb = new StringBuffer(80);
            }
            appendDesc(sb);
            mDesc = sb.toString();
        }
        return mDesc;
    }
    
    public int hashCode() {
        return mCharOffset ^ mRow ^ mCol + (mCol << 3);
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof Location)) {
            return false;
        }
        Location other = (Location) o;
        // char offset should be good enough, without row/col:
        if (other.getCharacterOffset() != getCharacterOffset()) {
            return false;
        }
        String otherPub = other.getPublicId();
        if (otherPub == null) {
            otherPub = "";
        }
        if (!otherPub.equals(mPublicId)) {
            return false;
        }
        String otherSys = other.getSystemId();
        if (otherSys == null) {
            otherSys = "";
        }
        return otherSys.equals(mSystemId);
    }

    /*
    ////////////////////////////////////////////////////////
    // Internal methods:
    ////////////////////////////////////////////////////////
     */

    private void appendDesc(StringBuffer sb)
    {
        String srcId;

        if (mSystemId != null) {
            sb.append("[row,col,system-id]: ");
            srcId = mSystemId;
        } else if (mPublicId != null) {
            sb.append("[row,col,public-id]: ");
            srcId = mPublicId;
        } else {
            sb.append("[row,col {unknown-source}]: ");
            srcId = null;
        }
        sb.append('[');
        sb.append(mRow);
        sb.append(',');
        sb.append(mCol);
        if (srcId != null) {
            sb.append(',');
            sb.append('"');
            sb.append(srcId);
            sb.append('"');
        }
        sb.append(']');
        if (mContext != null) {
            StringUtil.appendLF(sb);
            sb.append(" from ");
            mContext.appendDesc(sb);
        }
    }
}
