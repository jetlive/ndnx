package com.parc.ccn.data.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.stream.XMLStreamException;

import com.parc.ccn.Library;

public class BinaryXMLEncoder implements XMLEncoder {
	
	static {
		XMLCodecFactory.registerEncoder(BinaryXMLCodec.codecName(), 
									    BinaryXMLEncoder.class);
	}
	
	protected OutputStream _ostream = null;
	protected BinaryXMLDictionary _dictionary = null;
	
	public BinaryXMLEncoder() {
		this(null);
	}

	public BinaryXMLEncoder(BinaryXMLDictionary dictionary) {
		if (null == dictionary)
			_dictionary = BinaryXMLDictionary.getDefaultDictionary();
		else
			_dictionary = dictionary;
	}
	
	public void beginEncoding(OutputStream ostream) throws XMLStreamException {
		if (null == ostream)
			throw new IllegalArgumentException("BinaryXMLEncoder: output stream cannot be null!");
		_ostream = ostream;		
	}
	
	public void endEncoding() throws XMLStreamException {
		_ostream.flush();
	}

	public void writeElement(String tag, String utf8Content)
			throws XMLStreamException {
		writeElement(tag, utf8Content, null);
	}

	public void writeElement(String tag, String utf8Content,
			TreeMap<String, String> attributes)
			throws XMLStreamException {
		try {
			writeStartElement(tag, attributes);
			BinaryXMLCodec.encodeUString(_ostream, utf8Content);
			writeEndElement();
		} catch (IOException e) {
			throw new XMLStreamException(e.getMessage(), e);
		}
	}

	public void writeElement(String tag, byte[] binaryContent)
			throws XMLStreamException {
		writeElement(tag, binaryContent, null);
	}

	public void writeElement(String tag, byte[] binaryContent,
			TreeMap<String, String> attributes)
			throws XMLStreamException {
		try {
			writeStartElement(tag, attributes);
			BinaryXMLCodec.encodeBlob(_ostream, binaryContent);
			writeEndElement();
		} catch (IOException e) {
			throw new XMLStreamException(e.getMessage(), e);
		}
	}

	public void writeStartElement(String tag) throws XMLStreamException {
		writeStartElement(tag, null);
	}
	
	public void writeStartElement(String tag, TreeMap<String,String> attributes) throws XMLStreamException {
		try {
			long dictionaryVal = _dictionary.encodeTag(tag);
			
			if (dictionaryVal < 0) {
				// not in dictionary
				BinaryXMLCodec.encodeUString(_ostream, tag, BinaryXMLCodec.XML_TAG);
				
			} else {
				BinaryXMLCodec.encodeTypeAndVal(BinaryXMLCodec.XML_DTAG, dictionaryVal);
			}
			
			if (null != attributes) {
				// the keySet of a TreeMap is sorted.
				Set<String> keySet = attributes.keySet();
				Iterator<String> it = keySet.iterator();
				
				while (it.hasNext()) {
					String strAttr = it.next();
					String strValue = attributes.get(strAttr);
					
					long dictionaryAttr = _dictionary.encodeAttr(strAttr);
					if (dictionaryAttr < 0) {
						// not in dictionary, encode as attr
						BinaryXMLCodec.encodeUString(_ostream, strAttr, BinaryXMLCodec.XML_ATTR);
					} else {
						BinaryXMLCodec.encodeTypeAndVal(BinaryXMLCodec.XML_DATTR, dictionaryAttr);
					}
					// Write value
					BinaryXMLCodec.encodeUString(_ostream, strValue);
				}
				
			}
			
		} catch (UnsupportedEncodingException e) {
			Library.logger().severe("We don't understand UTF-8! Giving up!");
			throw new RuntimeException("Do not know UTF-8 charset! Significant configuration error!");
		} catch (IOException e) {
			throw new XMLStreamException(e.getMessage(),e);
		}
	}
	
	public void writeEndElement() throws XMLStreamException {
		try {
			_ostream.write(BinaryXMLCodec.XML_CLOSE);
		} catch (IOException e) {
			throw new XMLStreamException(e.getMessage(),e);
		}
	}


}
