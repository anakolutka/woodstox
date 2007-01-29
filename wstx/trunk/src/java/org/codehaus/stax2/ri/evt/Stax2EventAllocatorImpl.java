package org.codehaus.stax2.ri.evt;

import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.stream.util.XMLEventConsumer;

import org.codehaus.stax2.*;
import org.codehaus.stax2.ri.EmptyIterator;

/**
 * Base implementation of {@link XMLEventAllocator}, to be
 * used either as is, or as a base for more specialized Stax2
 * event allocator implementation.
 */
public class Stax2EventAllocatorImpl
    implements XMLEventAllocator, XMLStreamConstants
{
    /**
     * @param accurateLocation If true, allocator will construct instances
     *   that have accurate location information; if false, instances
     *   will only have some generic shared Location info. Latter option
     *   will reduce memory usage/thrashing a bit, and may improve speed.
     */
    public Stax2EventAllocatorImpl() { }

    /*
    //////////////////////////////////////////////////////////
    // XMLEventAllocator implementation
    //////////////////////////////////////////////////////////
     */

    public XMLEvent allocate(XMLStreamReader r)
        throws XMLStreamException
    {
        Location loc = getLocation(r);

        switch (r.getEventType()) {
        case CDATA:
            return new CharactersEventImpl(loc, r.getText(), true);
        case CHARACTERS:
            return new CharactersEventImpl(loc, r.getText(), false);
        case COMMENT:
            return new CommentEventImpl(loc, r.getText());
        case DTD:
            return createDTD(r, loc);

        case END_DOCUMENT:
            return new EndDocumentEventImpl(loc);

        case END_ELEMENT:
            return new EndElementEventImpl(loc, r);

        case PROCESSING_INSTRUCTION:
            return new ProcInstrEventImpl(loc, r.getPITarget(), r.getPIData());
        case SPACE:
            {
                CharactersEventImpl ch = new CharactersEventImpl(loc, r.getText(), false);
                ch.setWhitespaceStatus(true);
                return ch;
            }
        case START_DOCUMENT:
            return new StartDocumentEventImpl(loc, r);

        case START_ELEMENT:
            return createStartElement(r, loc);

        case ENTITY_REFERENCE:
            return createEntityReference(r, loc);

            /* Following 2 types should never get in here; they are directly
             * handled by DTDReader, and can only be accessed via DTD event
             * element.
             */
        case ENTITY_DECLARATION:
        case NOTATION_DECLARATION:
            /* Following 2 types should never get in here; they are directly
             * handled by the reader, and can only be accessed via start
             * element.
             */
        case NAMESPACE:
        case ATTRIBUTE:
        default:
            throw new XMLStreamException("Unrecognized event type "+r.getEventType()+".");
        }
    }
    
    public void allocate(XMLStreamReader r, XMLEventConsumer consumer)
        throws XMLStreamException
    {
        consumer.add(allocate(r));
    }

    public XMLEventAllocator newInstance() {
        return new Stax2EventAllocatorImpl();
    }

    /*
    //////////////////////////////////////////////////////////
    // Overridable methods
    //////////////////////////////////////////////////////////
     */

    /**
     * Method used to get the {@link Location} object to use for
     * an event to create. Base implementation just calls stream
     * reader's accessors, but sub-classes may choose to use other
     * methods (esp. when not in "preserve location" mode).
     */
    protected Location getLocation(XMLStreamReader r)
    {
        return r.getLocation();
    }

    protected XMLEvent createEntityReference(XMLStreamReader r, Location loc)
        throws XMLStreamException
    {
        /* !!! 28-Jan-2007, TSA: One major problem here: there is no way
         *    to access actual entity declaration via Stax 1.0 or Stax2
         *    stream reader, at not least not after DTD subset has been
         *    processed. Thus, we can not include that information.
         */
        //return new EntityReferenceEventImpl(loc, r.getLocalName());
        return new EntityReferenceEventImpl(loc, null);
    }

    protected XMLEvent createDTD(XMLStreamReader r, Location loc)
        throws XMLStreamException
    {
        // Not sure if we really need this defensive coding but...
        if (r instanceof XMLStreamReader2) {
            XMLStreamReader2 sr2 = (XMLStreamReader2) r;
            DTDInfo dtd = sr2.getDTDInfo();
            return new DTDEventImpl
                (loc,
                 dtd.getDTDRootName(),
                 dtd.getDTDSystemId(), dtd.getDTDPublicId(),
                 dtd.getDTDInternalSubset(),
                 dtd.getProcessedDTD());
        }
        /* No way to get all information... the real big problem is
         * that of how to access root name: it's obligatory for
         * DOCTYPE construct. :-/
         */
        return new DTDEventImpl(loc, null, r.getText());
    }

    protected XMLEvent createStartElement(XMLStreamReader r, Location loc)
        throws XMLStreamException
    {
        NamespaceContext nsCtxt = null;
        /* Note: there's no way to get non-transient namespace context via
         * Stax 1.0 interface -- the context you can access from reader
         * remains stable only during current event.
         */
        if (r instanceof XMLStreamReader2) {
            nsCtxt = ((XMLStreamReader2) r).getNonTransientNamespaceContext();
        }

        List attrs;
        {
            int attrCount = r.getAttributeCount();
            if (attrCount < 1) {
                attrs = null;
            } else {
                attrs = new ArrayList(attrCount);
                for (int i = 0; i < attrCount; ++i) {
                    QName aname = r.getAttributeName(i);
                    attrs.add(new AttributeEventImpl(loc, aname, r.getAttributeValue(i), r.isAttributeSpecified(i)));
                }
            }
        }
        List ns;
        {
            int nsCount = r.getNamespaceCount();
            if (nsCount < 1) {
                ns = null;
            } else {
                ns = new ArrayList(nsCount);
                for (int i = 0; i < nsCount; ++i) {
                    ns.add(NamespaceEventImpl.constructFor(loc, r.getNamespacePrefix(i), r.getNamespaceURI(i)));
                }
            }
        }
        
        return StartElementEventImpl.construct
            (loc, r.getName(), 
             ((attrs == null) ? EmptyIterator.getInstance() : attrs.iterator()),
             ((ns == null) ? EmptyIterator.getInstance() : ns.iterator()),
             nsCtxt);
    }
}
