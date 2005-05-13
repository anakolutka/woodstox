package org.codehaus.staxmate.samples;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMIteratorFactory;
import org.codehaus.staxmate.sr.SMIterator;


/**
 * Simple demonstration of using StaxMate on top of StAX, to simplify
 * nested XML parsing: implements a converter from well-formed HTML 
 * to a Wiki-line textual output format.
 *<p>
 * General rules for output Wiki-like markup are:
 * <ul>
 *  <li>Blocks (~= paragraphs) are separated by one or more empty lines
 *    (two or more consequtive linefeeds)
 *   </li>
 *  <li>There are 4 inline markups; bolding, italics, underline and
 *    hyperlink; these are marked by (respectively), ***text***,
 *    **text**, __text__, [[url | desc ]].
 *   </li>
 *  <li>Lists are marked lines that start with '*' (unordered) or '#'
 *    chars (ordered), followed by one or more spaces and list contents;
 *    nested lists are marked by indentation of 2 spaces per nesting level.
 *    Only inline markup is allowed inside list items, in addition to
 *    sub-lists.
 *   </li>
 *  <li>Non-nested tables are marked by pipe ('|') character starting a
 *    line; each text row represents a table row, and cells are separated
 *    by pipe chars as well. Cell or row spans are not supported, nor
 *    nested tables; inline markup is allowed inside cells
 *   </li>
 * </ul>
 *
 * @author Tatu Saloranta
 */
public final class HTMLConverter
{
    private HTMLConverter() { }

    private void convert(String filename)
        throws IOException, XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        // Let's configure factory 'optimally'...
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
        // just so it won't try to load DTD in if there's DOCTYPE
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        InputStream in = new java.io.FileInputStream(filename);
        XMLStreamReader sr = f.createXMLStreamReader(in);

        SMIterator it = SMIteratorFactory.rootElementIterator(sr);
        it.setElementTracking(SMIterator.TRACK_ELEM_VISIBLE_SIBLINGS);

        Writer out = new PrintWriter(System.out);

        try {
            processHTML(it, out);
        } finally {
            try {
                out.flush();
            } catch (Throwable t) { }
            sr.close();
            try {
                in.close();
            } catch (Throwable t) { }
        }
    }

    private void processHTML(SMIterator it, Writer out)
        throws IOException, XMLStreamException
    {
        it.getNext(); // has to be of type element now...

        String origName = it.getCurrentLocalName();
        String name = origName.toLowerCase();

        /* It _should_ be HTML... but let's also allow lone 'body'
         * as well, for additional robustness
         */
        if (name.equals("body")) {
            processBody(it, out);
        } else if (!name.equals("html")) {
            throw new XMLStreamException("Non-HTML document? Root element '"
                                         +origName+"'; excepted <HTML> or <html>");
        }

        SMIterator mainIt = it.childElementIterator();
        int type;

        while ((type = mainIt.getNext()) != SMIterator.SM_NODE_NONE) {
            origName = mainIt.getCurrentLocalName();
            name = origName.toLowerCase();

            // Should be 'head' or 'body'
            if (name.equals("head")) {
                processHead(mainIt, out);
            } else if (name.equals("body")) {
                processBody(mainIt, out);
            } else {
                throw new XMLStreamException("Non-HTML document? Unexpected element '"
                                             +origName+"'; under <HTML>.");
            }
        }
    }

    /**
     * Simple handler for HEAD section of a html document. Only looks for
     * title element (for now); returns as soon as that's gotten.
     */
    private void processHead(SMIterator parentIt, Writer out)
        throws IOException, XMLStreamException
    {
        SMIterator headIt = parentIt.childElementIterator();
        int type;
        while ((type = headIt.getNext()) != SMIterator.SM_NODE_NONE) {
            if (headIt.getCurrentLocalName().toLowerCase().equals("title")) {
                // Could capitalize it too...
                out.write("== ");
                String str = SMIteratorFactory.collectDescendantText(headIt, true);
                // Let's remove linefeeds if there was any
                addSingleLine(out, str);
                out.write(" ==\n\n");
                // Ok, that's it, we don't care about other stuff
                break;
            }
        }
    }

    /**
     * Simple handler for BODY section of a html document.
     * Has special handling for some elements (paragraphs, lists,
     * tables, links).
     */
    private void processBody(SMIterator parentIt, Writer out)
        throws IOException, XMLStreamException
    {
        /* We need both elements and text content (but not comments etc);
         * further, due to loose nesting of HTML, let's just do flat
         * iteration in general, as we can still do sub-scoping for
         * specific elements (tables etc)
         */
        SMIterator bodyIt = parentIt.descendantMixedIterator();
        int type;
        StringBuffer text = null; // for collected 'loose' text

        while ((type = bodyIt.getNext()) != SMIterator.SM_NODE_NONE) {
            // Let's weed out end elements right away...
            if (type == XMLStreamConstants.END_ELEMENT) {
                continue;
            }
            // And straight text as well:
            String inline;
            if (type == XMLStreamConstants.START_ELEMENT) {
                String tag = bodyIt.getCurrentLocalName().toLowerCase();
                if (processBlockElement(bodyIt, out, tag, text)) {
                    // true -> was succesfully handled
                    text = null;
                    continue;
                }
                /* Ok; not a block we recognized... but maybe a well-known
                 * inline element?
                 */
                inline = checkInlineMarkup(bodyIt, tag);
            } else {
                inline = bodyIt.getCurrentText();
            }

            if (inline != null) {
                if (text == null) {
                    text = new StringBuffer(inline);
                } else {
                    text.append(inline);
                }
            }
        } // while (...)

        if (text != null) {
            addPara(out, text);
            text = null;
        }
    }

    /**
     * Method that is used to figure out type and handling of a node,
     * at block level scope (but not from inside tables and lists)
     */
    private boolean processBlockElement(SMIterator it, Writer out, String tag,
                                        StringBuffer text)
        throws IOException, XMLStreamException
    {
        // We'll only get START_ELEMENT events here

        if (tag.charAt(0) == 'h' && tag.length() == 2) {
            char c = tag.charAt(1);
            // heading?
            if (c >= '1' && c <= '5') {
                if (text != null) {
                    addPara(out, text);
                }
                processHeading(it, out, (c - '1'));
                return true;
            }
        }
        
        /* Handling of paragraphs depends on whether it's a main level
         * thing or not
         */
        if (tag.equals("p") || tag.equals("blockquote")) {
            // (no special handling for blockquote currently)
            addPara(out, text);
            /* Let's recursively call the main loop, and then add an
             * empty line after it.
             */
            processBody(it, out);
            out.write("\n\n");
            return true;
        }
        if (tag.equals("pre")) {
            addPara(out, text);
            // Can't have any markup in there...
            String str = SMIteratorFactory.collectDescendantText(it, true);
            if (str.length() > 0) {
                addPara(out, str);
            }
            return true;
        }
        if (tag.equals("ul") || tag.equals("o")) {
            addPara(out, text);
            processList(it, out, (tag.charAt(0) == 'u') ? '*' : '#', 0);
            return true;
        }
        if (tag.equals("table")) {
            addPara(out, text);
            processTable(it, out, false);
            return true;
        }

        // Not a recognized (or handlable) block element
        return false;
    }

    private void processHeading(SMIterator it, Writer out, int depth)
        throws IOException, XMLStreamException
    {
        depth += 2;
        if (depth > 5) {
            depth = 5;
        }
        String prefix = "=====".substring(0, depth);
        out.write(prefix);
        out.write(' ');
        SMIteratorFactory.processDescendantText(it, out, true);
        out.write(' ');
        out.write(prefix);
        out.write("\n\n");
    }

    private void processList(SMIterator it, Writer out, char type, int depth)
        throws IOException, XMLStreamException
    {
        /* Let's assume child elements have to be 'li' elements or
         * sublists ('ul', 'ol'); and ignore everything else.
         */
        SMIterator listIt = it.childElementIterator();

        // We'll only get START_ELEMENTs here except for EOF:
        while (listIt.getNext() != SMIterator.SM_NODE_NONE) {
            String tag = listIt.getCurrentLocalName().toLowerCase();
            if (tag.equals("li")) {
                processListItem(listIt, out, type, depth);
            } else if (tag.equals("ul")) {
                processList(listIt, out, '*', depth+1);
            } else if (tag.equals("ol")) {
                processList(listIt, out, '#', depth+1);
            } else {
                /* could add warnings, or append content to previous item,
                 * or create a list heading... whatever
                 */
            }
        }

        // And finally, trailing empty line, but only for main-level lists
        if (depth == 0) {
            out.write('\n');
        }
    }

    private void processListItem(SMIterator it, Writer out, char listType, int depth)
        throws IOException, XMLStreamException
    {
        // Ok, list item marker:
        for (int i = 0; i < depth; ++i) {
            out.write("  "); // 2 space indentation
        }
        out.write(listType);
        out.write(' ');

        /* List item contents are more varied; text, inline markup; maybe
         * even sublists.
         */
        SMIterator itemIt = it.childMixedIterator();
        int type;

        while ((type = itemIt.getNext()) != SMIterator.SM_NODE_NONE) {
            if (type == XMLStreamConstants.START_ELEMENT) {
                String tag = itemIt.getCurrentLocalName().toLowerCase();
                // only care about sub-lists:
                if (tag.equals("ul") || tag.equals("ol")) {
                    out.write('\n'); // to finish off the current line
                    processList(itemIt, out, (tag.charAt(0) == 'u') ? '*' : '#',
                                depth+1);
                    /* Also, let's also ignore whatever came after the sublist,
                     * for this item, if anything; most likely just whitespace.
                     * Problem otherwise is how to handle "leftovers"; can't
                     * add them to this item any more, would need to start
                     * a new item or something.
                     */
                    return;
                } else { // can also process inline markup
                    String str = checkInlineMarkup(itemIt, tag);
                    if (str != null) {
                        addSingleLine(out, str);
                        continue;
                    }
                }
                // Otherwise, let's just collect and output text:
                addSingleLine(out, SMIteratorFactory.collectDescendantText(itemIt, true));
            } else {
                addSingleLine(out, itemIt.getCurrentText());
            }
        }
        out.write('\n');
    }

    private void processTable(SMIterator it, Writer out, boolean header)
        throws IOException, XMLStreamException
    {
        /* Let's assume child elements have to be 'tr', or one of grouping
         * elements ('thead', 'tfoot' or 'tbody'), and ignore everything else.
         */
        SMIterator tableIt = it.childElementIterator();
        // We'll only get START_ELEMENTs here except for EOF:
        while (tableIt.getNext() != SMIterator.SM_NODE_NONE) {
            String tag = tableIt.getCurrentLocalName().toLowerCase();
            if (tag.equals("thead") || tag.equals("tfoot")
                || tag.equals("tbody")) {
                /* Let's just recursively call this method, should be
                 * safe?
                 */
                processTable(tableIt, out, header || tag.equals("thead"));
            } else if (tag.equals("tr")) {
                processTableRow(tableIt, out, header);
            }
            // and ignore others....
        }
        // Let's add empty line as paragraph separator...
        out.write("\n");
    }

    private void processTableRow(SMIterator it, Writer out, boolean headerRow)
        throws IOException, XMLStreamException
    {
        // Let's assume only 'tr' elements are encountered...
        SMIterator rowIt = it.childElementIterator();
        out.write("|");
        // We'll only get START_ELEMENTs here except for EOF:
        while (rowIt.getNext() != SMIterator.SM_NODE_NONE) {
            String tag = rowIt.getCurrentLocalName().toLowerCase();
            if (tag.equals("td")) {
                processTableCell(rowIt, out, headerRow);
            } else if (tag.equals("th")) {
                processTableCell(rowIt, out, true);
            } else {
                continue;
            }
            out.write("|");
        }
        // Let's add lf, to separate rows...
        out.write("\n");
    }

    private void processTableCell(SMIterator it, Writer out, boolean headerCell)
        throws IOException, XMLStreamException
    {
        /* Cells can have varied content, though... generally we only care
         * about text and inline markup, though.
         */
        SMIterator cellIt = it.childMixedIterator();
        int type;
        while ((type = cellIt.getNext()) != SMIterator.SM_NODE_NONE) {
            if (type == XMLStreamConstants.START_ELEMENT) {
                String tag = cellIt.getCurrentLocalName().toLowerCase();
                // No sub-tables or lists allowed... just inline markup
                String str = checkInlineMarkup(cellIt, tag);
                if (str != null) {
                    addSingleLine(out, str);
                    continue;
                }
                // Otherwise, let's just collect and output text:
                addSingleLine(out, SMIteratorFactory.collectDescendantText(cellIt, true));
            } else { // just plain text
                addSingleLine(out, cellIt.getCurrentText());
            }
        }
    }

    private String checkInlineMarkup(SMIterator it, String tag)
        throws IOException, XMLStreamException
    {
        if (tag.equals("a")) {
            XMLStreamReader sr = it.getStreamReader();
            String url = sr.getAttributeValue(null, "href");
            String str = SMIteratorFactory.collectDescendantText(it, true);
            return "[["+url+" | "+str+" ]]";
        }
        if (tag.equals("b")) {
            String str = SMIteratorFactory.collectDescendantText(it, true);
            return "'''"+str+"'''";
        }
        if (tag.equals("i")) {
            String str = SMIteratorFactory.collectDescendantText(it, true);
            return "'''"+str+"'''";
        }
        if (tag.equals("u")) {
            String str = SMIteratorFactory.collectDescendantText(it, true);
            return "___"+str+"___";
        }
        if (tag.equals("hr")) {
            return "\n-----\n";
        }
        if (tag.equals("br")) {
            // Hmmh. This won't work too well...
            return "\n";
        }
        // Nope, inline markup not recognized (or no effect can be applied)
        return null;
    }

    /**
     * Method called to output "unwrapped" text (either not contained in
     * any element, or in unrecognized one). Let's just output it as
     * is, but add paragraph separator after the text.
     */
    private void addPara(Writer out, StringBuffer textBuf)
        throws IOException
    {
        addPara(out, textBuf.toString());
    }

    private void addPara(Writer out, String text)
        throws IOException
    {
        /* Let's remove all linefeeds from the start, and from the end,
         * to make sure we won't have excessive empty lines...
         */
        int len = text.length();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c != '\r' && c != '\n') {
                break;
            }
            ++i;
        }
        if (i > 0) {
            text = text.substring(i);
        }

        i = len = text.length()-1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c != '\r' && c != '\n') {
                break;
            }
            --i;
        }
        if (i < len) {
            text = text.substring(0, i+1);
        }

        // Also, let's see if there's any non-space stuff left?
        if (text.trim().length() > 0) {
            out.write(text);
            out.write("\n\n");
        }
    }

    /**
     * Simple (although not very efficient) method that'll replace linefeeds
     * with single space chars and output results
     */
    private void addSingleLine(Writer out, String text)
        throws IOException
    {
        // Need to replace linefeeds, that's all
        BufferedReader br = new BufferedReader(new StringReader(text));
        String line;
        boolean first = true;

        while ((line = br.readLine()) != null) {
            if (first) {
                first = false;
            } else {
                out.write(' ');
            }
            out.write(line);
        }
    }

    public static void main(String[] args)
        throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java "+SMIteratorFactory.class+" [input file]");
            System.exit(1);
        }
        new HTMLConverter().convert(args[0]);
    }
}
