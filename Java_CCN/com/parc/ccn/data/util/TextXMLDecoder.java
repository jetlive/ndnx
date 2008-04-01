package com.parc.ccn.data.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

public class TextXMLDecoder implements XMLDecoder {

	static {
		XMLCodecFactory.registerDecoder(TextXMLCodec.codecName(), 
				TextXMLDecoder.class);
	}
	
	protected InputStream _istream = null;
	protected XMLEventReader _reader = null;

	public TextXMLDecoder() {
	}
	
	public void beginDecoding(InputStream istream) throws XMLStreamException {
		if (null == istream)
			throw new IllegalArgumentException("TextXMLDecoder: input stream cannot be null!");
		_istream = istream;		
		XMLInputFactory factory = XMLInputFactory.newInstance();
		_reader = factory.createXMLEventReader(_istream);
		
		readStartDocument();
	}
	
	public void endDecoding() throws XMLStreamException {
		readEndDocument();
	}

	public void readStartDocument() throws XMLStreamException {
		XMLEvent event = _reader.nextEvent();
		if (!event.isStartDocument()) {
			throw new XMLStreamException("Expected start document, got: " + event.toString());
		}
	}

	public void readEndDocument() throws XMLStreamException {
		XMLEvent event = _reader.nextEvent();
		if (!event.isEndDocument()) {
			throw new XMLStreamException("Expected end document, got: " + event.toString());
		}
	}

	public void readStartElement(String startTag) throws XMLStreamException {
		readStartElement(startTag, null);
	}

	public void readStartElement(String startTag,
								TreeMap<String, String> attributes) throws XMLStreamException {

		XMLEvent event = _reader.nextEvent();
		// Use getLocalPart to strip namespaces.
		// Assumes we are working with a global default namespace of CCN.
		if (!event.isStartElement() || (!startTag.equals(event.asStartElement().getName().getLocalPart()))) {
			// Coming back with namespace decoration doesn't match
			throw new XMLStreamException("Expected start element: " + startTag + " got: " + event.toString());
		}	
		if (null != attributes) {
			// we might be expecting attributes
			Iterator<?> it = event.asStartElement().getAttributes();
			while (it.hasNext()) {
				Attribute a = (Attribute)it.next();
				// may need fancier namespace handling.
				attributes.put(a.getName().getLocalPart(), a.getValue());
			}
		}
	}

	public boolean peekStartElement(String startTag) throws XMLStreamException {
		XMLEvent event = _reader.peek();
		if ((null == event) || !event.isStartElement() || (!startTag.equals(event.asStartElement().getName().getLocalPart()))) {
			return false;
		}	
		return true;
	}

	public void readEndElement() throws XMLStreamException {
		XMLEvent event = _reader.nextEvent();
		if (!event.isEndElement()) {
			throw new XMLStreamException("Expected end element, got: " + event.toString());
		}
	}

	public String readUTF8Element(String startTag) throws XMLStreamException {
		return readUTF8Element(startTag, null);
	}

	public String readUTF8Element(String startTag,
								  TreeMap<String, String> attributes) throws XMLStreamException {
		readStartElement(startTag, attributes);
		String strElementText = _reader.getElementText();
		// readEndElement(); // getElementText eats the endElement
		return strElementText;
	}

	public byte[] readBinaryElement(String startTag) throws XMLStreamException {
		readBinaryElement(startTag, null);
	}

	public byte[] readBinaryElement(String startTag,
			TreeMap<String, String> attributes) throws XMLStreamException {
		readStartElement(startTag, attributes);
		String strElementText = _reader.getElementText();
		// readEndElement(); // getElementText eats the endElement
		return TextXMLCodec.decodeBinaryElement(strElementText);
	}

}
