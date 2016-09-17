package hu.telekom.lwi.plugin.util;

import java.util.Stack;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import hu.telekom.lwi.plugin.log.LwiRequestData;
import io.undertow.server.HttpServerExchange;

public class LwiLogAttributeUtil {

	private static final Logger log = Logger.getLogger(LwiLogAttributeUtil.class);

	private static final String CLEANSE_REGEX = "(\n|\t|\r|\\s{2,})";

	public static void getMessageAttributes(Stack<String> qNames, LwiRequestData lwiRequestData, String message) {
		try {
			XMLEventReader eventReader = LwiXmlUtil.getXmlEventReader(qNames, message);
	
			while (eventReader.hasNext() && lwiRequestData.parseRequestRequired()) {
				XMLEvent event = eventReader.nextEvent();
				switch (event.getEventType()) {
				case XMLStreamConstants.START_ELEMENT:
					StartElement startElement = event.asStartElement();
					qNames.push(qNames.peek()+"/"+startElement.getName().getLocalPart());
					if (qNames.size() < 5) {
						setRequestDataFromAttribute(lwiRequestData, startElement);
					}
					break;
				case XMLStreamConstants.END_ELEMENT:
					qNames.pop();
					break;
				case XMLStreamConstants.CHARACTERS:
					Characters characters = event.asCharacters();
					setRequestDataFromElement(lwiRequestData, qNames.peek(), characters.getData());
					break;
				}
			}
		} catch (XMLStreamException e) {
			log.error("LwiLogAttributeUtil > Invalid xml!", e);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void setRequestDataFromAttribute(LwiRequestData lwiRequestData, StartElement element) {
		Attribute attribute = null;
		if (lwiRequestData.isNullRequestId() && (attribute = element.getAttributeByName(new QName(LwiLogAttribute.RequestId.name()))) != null) {
			lwiRequestData.setRequestId(attribute.getValue());
		}
		if (lwiRequestData.isNullCorrelationId() && (attribute = element.getAttributeByName(new QName(LwiLogAttribute.CorrelationId.name()))) != null) {
			lwiRequestData.setCorrelationId(attribute.getValue());
		}
		if (lwiRequestData.isNullUserId() && (attribute = element.getAttributeByName(new QName(LwiLogAttribute.UserId.name()))) != null) {
			lwiRequestData.setUserId(attribute.getValue());
		}
	}
	
	private static void setRequestDataFromElement(LwiRequestData lwiRequestData, String qName, String value) {
		if (lwiRequestData.isNullRequestId() && LwiLogAttribute.RequestId.isKeyXmlElement(qName)) {
			lwiRequestData.setRequestId(value);
		}
		if (lwiRequestData.isNullCorrelationId() && LwiLogAttribute.CorrelationId.isKeyXmlElement(qName)) {
			lwiRequestData.setCorrelationId(value);
		}
		if (lwiRequestData.isNullUserId() && LwiLogAttribute.UserId.isKeyXmlElement(qName)) {
			lwiRequestData.setUserId(value);
		}
	}

	public static String getHttpHeaderAttribute(HttpServerExchange exchange, LwiLogAttribute attribute) {
		return exchange.getRequestHeaders().getFirst(attribute.getHttpHeaderAttribute());
	}

	public static String cleanseMessage(String message) {
		return message != null ? message.replaceAll(CLEANSE_REGEX, "") : "N/A";
	}
}
