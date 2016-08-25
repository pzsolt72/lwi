package hu.telekom.lwi.plugin.log;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;

import hu.telekom.lwi.plugin.util.RereadableRequestBufferingUtil;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class MessageLogHandler implements HttpHandler {

	private static final Logger log = Logger.getLogger(MessageLogHandler.class);
	private static final Logger messageLog = Logger.getLogger("hu.telekom.lwi.message.log");
	
	private HttpHandler next;
	
	private MessageLogLevel logLevel = MessageLogLevel.CTX;
	
	public MessageLogHandler(HttpHandler next) {
		this.next = next;
	}
	
	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		log.info(String.format("MessageLogHandler.handleRequest invoked (%s, %d)", logLevel.name(), exchange.getRequestContentLength()));
		
		String[] requestPath = exchange.getRequestPath().split("/");
		
		String caller = "#CALLER#";
		String provider = requestPath.length >= 2 ? requestPath[1] : MessageLogAttribute.EMPTY;
		String operation = requestPath.length >= 3 ? requestPath[2] : MessageLogAttribute.EMPTY;
		
		StringBuilder requestLogMessage = new StringBuilder(String.format("[%s > %s.%s]", caller, provider, operation));
		StringBuilder responseLogMessage = new StringBuilder(String.format("[%s < %s.%s]", caller, provider, operation));
		
		if (logLevel != MessageLogLevel.MIN) {
			String message = RereadableRequestBufferingUtil.handleRequest(exchange, 5, next);

			String requestId = getAttribute(exchange, MessageLogAttribute.RequestId, message);
			String correlationId = getAttribute(exchange, MessageLogAttribute.CorrelationId, message);
			String userId = getAttribute(exchange, MessageLogAttribute.UserId, message);
			
			requestLogMessage.append(String.format("[RequestId: %s CorrelationId: %s UserId: %s]", requestId, correlationId, userId));
			responseLogMessage.append(String.format("[RequestId: %s CorrelationId: %s UserId: %s]", requestId, correlationId, userId));
			
			if (logLevel == MessageLogLevel.FULL) {
				requestLogMessage.append(String.format("[%s]", message.replaceAll("\n", "")));
			}
		}

		messageLog.info(requestLogMessage);
	
		next.handleRequest(exchange);

		if (logLevel == MessageLogLevel.FULL) {
			String message = "#RESPONSE#";
			responseLogMessage.append(String.format("[%s]", message.replaceAll("\n", "")));
		}

		messageLog.info(responseLogMessage);
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = MessageLogLevel.valueOf(logLevel);
	}

	
	private String getAttribute(HttpServerExchange exchange, MessageLogAttribute attribute, String message) {
		String value = null;
		Document doc = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(new ByteArrayInputStream(message.getBytes()));
		} catch (Exception e) {
			log.warn("Unable to parse soap message", e);
		}
		
		if ((doc != null && (  
					(value = getSoapAttribute(doc, attribute)) != null ||
					(value = getNewOSBAttribute(doc, attribute)) != null ||
					(value = getTechOSBAttribute(doc, attribute)) != null))
				|| 
				(value = getHttpHeaderAttribute(exchange, attribute)) != null) {
			
			log.debug(String.format("MessageLogHandler.getAttribute(%s) found - %s", attribute.name(), value));
			return value;
		}
		log.debug(String.format("MessageLogHandler.getAttribute(%s) is empty", attribute.name()));
		return MessageLogAttribute.EMPTY;
	}

	private String getSoapAttribute(Document doc, MessageLogAttribute attribute) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			String value = (String) xpath.compile(attribute.getSoapAttribute()).evaluate(doc, XPathConstants.STRING);
			if (value != null && !value.isEmpty()) {
				return value;
			}
		} catch (Exception e) {
			log.error("Unable to parse new osb soap message", e);
		}
		return null;
	}
	
	private String getNewOSBAttribute(Document doc, MessageLogAttribute attribute) {
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

	private String getTechOSBAttribute(Document doc, MessageLogAttribute attribute) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			String value = (String) xpath.compile(attribute.getTechOSBAttribute()).evaluate(doc, XPathConstants.STRING);
			if (value != null && !value.isEmpty()) {
				return value;
			}
		} catch (Exception e) {
			log.error("Unable to parse new osb soap message", e);
		}
		return null;
	}

	private String getHttpHeaderAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		return exchange.getRequestHeaders().getFirst(attribute.getHttpHeaderAttribute());
	}
}
