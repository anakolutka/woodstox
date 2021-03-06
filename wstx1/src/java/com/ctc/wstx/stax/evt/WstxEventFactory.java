/* Woodstox XML processor.
 *<p>
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
 *<p>
 * You can redistribute this work and/or modify it under the terms of
 * LGPL (Lesser Gnu Public License), as published by
 * Free Software Foundation (http://www.fsf.org). No warranty is
 * implied. See LICENSE for details about licensing.
 */

package com.ctc.wstx.stax.evt;

import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Basic implementation of {@link XMLEventFactory} to be used with Wstx.
 */
public final class WstxEventFactory
    extends XMLEventFactory
{
    /**
     * "Current" location of this factory; ie. location assigned for all
     * events created by this factory.
     */
    private Location mLocation;

    public WstxEventFactory() {
    }

    /*
    /////////////////////////////////////////////////////////////
    // XMLEventFactory API
    /////////////////////////////////////////////////////////////
     */

    public Attribute createAttribute(QName name, String value) {
        return new WAttribute(mLocation, name, value);
    }

    public Attribute createAttribute(String localName, String value) {
        return new WAttribute(mLocation, localName, null, null, value);
    }

    public Attribute createAttribute(String prefix, String nsURI,
                                     String localName, String value)
    {
        return new WAttribute(mLocation, localName, nsURI, prefix, value);
    }

    public Characters createCData(String content) {
        return new WCharacters(mLocation, content, true);
    }

    public Characters createCharacters(String content) {
        return new WCharacters(mLocation, content, false);
    }

    public Comment createComment(String text) {
        return new WComment(mLocation, text);
    }

    /**
     * Note: constructing DTD events this way means that there will be no
     * internal presentation of actual DTD; no parsing is implied by
     * construction.
     */
    public DTD createDTD(String dtd) {
        return new WDTD(mLocation, dtd, null);
    }

    public EndDocument createEndDocument() {
        return new WEndDocument(mLocation);
    }

    public EndElement createEndElement(QName name, Iterator namespaces) {
        return new WEndElement(mLocation, name, namespaces);
    }

    public EndElement createEndElement(String prefix, String nsURI,
                                       String localName)
    {
        return createEndElement(new QName(nsURI, localName), null);
    }

    public EndElement createEndElement(String prefix, String nsURI,
                                       String localName, Iterator ns)
    {
        return createEndElement(new QName(nsURI, localName, prefix), ns);
    }

    public EntityReference createEntityReference(String name, EntityDeclaration decl)
    {
        return new WEntityReference(mLocation, name, decl);
    }

    public Characters createIgnorableSpace(String content) {
        return WCharacters.createIgnorableWS(mLocation, content);
    }

    public Namespace createNamespace(String nsURI) {
        return new WNamespace(mLocation, nsURI);
    }
    
    public Namespace createNamespace(String prefix, String nsUri) {
        return new WNamespace(mLocation, prefix, nsUri);
    }

    public ProcessingInstruction createProcessingInstruction(String target, String data) {
        return new WProcInstr(mLocation, target, data);
    }
    
    public Characters createSpace(String content) {
        return WCharacters.createNonIgnorableWS(mLocation, content);
    }

    public StartDocument createStartDocument() {
        return new WStartDocument(mLocation);
    }

    public StartDocument createStartDocument(String encoding) {
        return new WStartDocument(mLocation, encoding);
    }

    public StartDocument createStartDocument(String encoding, String version) {
        return new WStartDocument(mLocation, encoding, version);
    }

    public StartDocument createStartDocument(String encoding, String version, boolean standalone)
    {
        return new WStartDocument(mLocation, encoding, version,
                                  true, standalone);
    }

    public StartElement createStartElement(QName name, Iterator attr, Iterator ns)
    {
        return createStartElement(name, attr, ns, null);
    }

    public StartElement createStartElement(String prefix, String nsURI, String localName)
    {
        return createStartElement(new QName(nsURI, localName, prefix), null, null, null);
    }

    public StartElement createStartElement(String prefix, String nsURI,
                                           String localName, Iterator attr,
                                           Iterator ns)
    {
        return createStartElement(new QName(nsURI, localName, prefix), attr, ns, null);
    }

    public StartElement createStartElement(String prefix, String nsURI,
                                           String localName, Iterator attr,
                                           Iterator ns, NamespaceContext context)
    {
        /* Note: we don't have any use for the namespace context... why does
         * API want to pass it?
         */
        return createStartElement(new QName(nsURI, localName, prefix), attr, ns);
    }

    protected StartElement createStartElement(QName name, Iterator attr,
                                           Iterator ns, NamespaceContext ctxt)
    {
        return SimpleStartElement.construct(mLocation, name, attr, ns, ctxt);
    }

    public void setLocation(Location loc) {
        mLocation = loc;
    }

    /*
    /////////////////////////////////////////////////////////////
    // Private methods
    /////////////////////////////////////////////////////////////
     */
}
