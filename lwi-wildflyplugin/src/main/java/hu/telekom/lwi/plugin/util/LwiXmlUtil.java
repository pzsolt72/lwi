package hu.telekom.lwi.plugin.util;

import java.io.InputStream;
import java.io.StringReader;
import java.util.Stack;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

public class LwiXmlUtil {

	public static XMLEventReader getXmlEventReader(Stack<String> qNames, String message) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLEventReader eventReader = factory.createXMLEventReader(new StringReader(message));
		return eventReader;
	}

	public static XMLEventReader getXmlEventReader(Stack<String> qNames, InputStream stream) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLEventReader eventReader = factory.createXMLEventReader(stream);
		return eventReader;
	}

}
