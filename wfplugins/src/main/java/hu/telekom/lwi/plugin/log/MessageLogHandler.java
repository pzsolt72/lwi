package hu.telekom.lwi.plugin.log;

import org.jboss.logging.Logger;

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
		
		String caller = "#CALLER#";
		String provider = "#PROVIDER#";
		String operation = "#OPERATION#";
		
		StringBuilder requestLogMessage = new StringBuilder(String.format("[%s > %s.%s]", caller, provider, operation));
		StringBuilder responseLogMessage = new StringBuilder(String.format("[%s < %s.%s]", caller, provider, operation));
		
		if (logLevel != MessageLogLevel.MIN) {
			String requestId = getAttribute(exchange, MessageLogAttribute.RequestId);
			String correlationId = getAttribute(exchange, MessageLogAttribute.CorrelationId);
			String userId = getAttribute(exchange, MessageLogAttribute.UserId);
			
			requestLogMessage.append(String.format("[RequestId: %s CorrelationId: %s UserId: %s]", requestId, correlationId, userId));
			responseLogMessage.append(String.format("[RequestId: %s CorrelationId: %s UserId: %s]", requestId, correlationId, userId));
			
			if (logLevel == MessageLogLevel.FULL) {
				String message = RereadableRequestBufferingUtil.handleRequest(exchange, 5, next);
				requestLogMessage.append(String.format("[%s]", message));
			}
		}

		messageLog.info(requestLogMessage);
	
		next.handleRequest(exchange);

		if (logLevel == MessageLogLevel.FULL) {
			String message = "#RESPONSE#";
			responseLogMessage.append(String.format("[%s]", message));
		}

		messageLog.info(responseLogMessage);
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = MessageLogLevel.valueOf(logLevel);
	}

	
	private String getAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		String value = null; 
		if (	(value = getSoapAttribute(exchange, attribute)) != null ||
				(value = getNewOSBAttribute(exchange, attribute)) != null ||
				(value = getTechOSBAttribute(exchange, attribute)) != null ||
				(value = getHttpHeaderAttribute(exchange, attribute)) != null) {
			
			log.debug(String.format("MessageLogHandler.getAttribute(%s) found - %s", attribute.name(), value));
			return value;
		}
		log.debug(String.format("MessageLogHandler.getAttribute(%s) is empty", attribute.name()));
		return MessageLogAttribute.EMPTY;
	}

	private String getSoapAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		// FIXME: implement me
		log.warn("MessageLogHandler.getSoapAttribute not implemented yet!");
		return null;
	}
	
	private String getNewOSBAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		// FIXME: implement me
		log.warn("MessageLogHandler.getNewOSBAttribute not implemented yet!");
		return null;
	}

	private String getTechOSBAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		// FIXME: implement me
		log.warn("MessageLogHandler.getTechOSBAttribute not implemented yet!");
		return null;
	}

	private String getHttpHeaderAttribute(HttpServerExchange exchange, MessageLogAttribute attribute) {
		log.warn("MessageLogHandler.getTechOSBAttribute not implemented yet!");
		return exchange.getRequestHeaders().getFirst(attribute.getHttpHeaderAttribute());
	}
}
