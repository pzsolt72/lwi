package hu.telekom.lwi.plugin.util;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import io.undertow.server.HttpServerExchange;

public class LwiLogAttributeUtil {
	
	private static final Logger log = Logger.getLogger(LwiLogAttributeUtil.class);
	
	private static final String CLEANSE_REGEX = "(\n|\t|\r|\\s{2,})";

	public static String getMessageAttribute(LwiLogAttribute attribute, String message) {
		String value = null;
		Document doc = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(new ByteArrayInputStream(message.getBytes()));
		} catch (Exception e) {
			// not a soap message
		}
		
		if ((doc != null && (  
					(value = getSoapAttribute(doc, attribute)) != null ||
					(value = getNewOSBAttribute(doc, attribute)) != null ||
					(value = getTechOSBAttribute(doc, attribute)) != null))) {
			
			log.debug(String.format("MessageLogHandler.getMessageAttribute(%s) found - %s", attribute.name(), value));
			return value;
		}
		log.debug(String.format("MessageLogHandler.getMessageAttribute(%s) is empty", attribute.name()));
		return LwiLogAttribute.EMPTY;
	}

	public static String getSoapAttribute(Document doc, LwiLogAttribute attribute) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			String value = (String) xpath.compile(attribute.getSoapAttribute()).evaluate(doc, XPathConstants.STRING);
			if (value != null && !value.isEmpty()) {
				return value;
			}
		} catch (Exception e) {
			log.error("Unable to parse general soap message", e);
		}
		return null;
	}
	
	public static String getNewOSBAttribute(Document doc, LwiLogAttribute attribute) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			String value = (String) xpath.compile(attribute.getNewOSBAttribute()).evaluate(doc, XPathConstants.STRING);
			if (value != null && !value.isEmpty()) {
				return value;
			}
		} catch (Exception e) {
			log.error("Unable to parse new osb soap message", e);
		}
		return null;
	}

	public static String getTechOSBAttribute(Document doc, LwiLogAttribute attribute) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			String value = (String) xpath.compile(attribute.getTechOSBAttribute()).evaluate(doc, XPathConstants.STRING);
			if (value != null && !value.isEmpty()) {
				return value;
			}
		} catch (Exception e) {
			log.error("Unable to parse tech osb soap message", e);
		}
		return null;
	}
	
	public static String getHttpHeaderAttribute(HttpServerExchange exchange, LwiLogAttribute attribute) {
		return exchange.getRequestHeaders().getFirst(attribute.getHttpHeaderAttribute());
	}

	public static String cleanseMessage(String message) {
		return message != null ? message.replaceAll(CLEANSE_REGEX, "") : "N/A";
	}
}
