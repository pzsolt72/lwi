package hu.telekom.lwi.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.limit.RequestLimitHandler;
import hu.telekom.lwi.plugin.log.LwiLogHandler;
import hu.telekom.lwi.plugin.log.MessageLogLevel;
import hu.telekom.lwi.plugin.security.SecurityHandler;
import io.undertow.io.Sender;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.builder.HandlerBuilder;

/**
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class LwiHandler implements HttpHandler {

	private static final String REQUEST_LIMIT_ERROR_MSG = "Request limit exceeded! max request allowed: %s, queue size: %s";
	private static final int REQUEST_LIMIT_ERROR_CODE = 509;

	private RequestLimit requestLimitHandler = null;

	private boolean parametersAreValidated = false;
	
	private final Logger log = Logger.getLogger(LwiHandler.class);	

	private HttpHandler next;

	private Integer maxRequest;
	private Integer queueSize;
	private String logLevel;
	private String validationType;

	/*
	 * public enum LogLevel { NONE, MIN, CTX, FULL };
	 */

	public enum ValidationType {
		NO, CTX, MSG
	};

	/**
	 * 
	 * @param next
	 */

	public LwiHandler(HttpHandler next) {
		
		log.debug("Init LwiHandler");

		this.next = next;
	}

	public LwiHandler(HttpHandler next, Integer maxRequest, Integer queueSize, String logLevel, String validationType) {

		log.debug("Init LwiHandler");
		
		this.next = next;		
		this.maxRequest = maxRequest;
		this.queueSize = queueSize;
		this.logLevel = logLevel;
		this.validationType = validationType;

		validateHandlerParameters(maxRequest, queueSize, logLevel, validationType);		
		

	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		if (!parametersAreValidated)
			validateHandlerParameters(maxRequest, queueSize, logLevel, validationType);

		
		log.debug("LwiHandler->handle " + String.format("maxRequests: %s, queueSize: %s, logLevel: %s,  validationType: %s", maxRequest, queueSize, logLevel, validationType));


		if (requestLimitHandler == null) {
			requestLimitHandler = new RequestLimit(maxRequest, queueSize);
			requestLimitHandler.setFailureHandler(new LwiRequestLimitExceededHandler());
		}

		LwiLogHandler lwiLogHandler = new LwiLogHandler(next);
		SecurityHandler securityHandler = new SecurityHandler(lwiLogHandler);

		requestLimitHandler.handleRequest(exchange, securityHandler);

	}

	public HttpHandler getNext() {
		return next;
	}

	public void setNext(HttpHandler next) {
		this.next = next;
	}

	public Integer getMaxRequest() {
		return maxRequest;
	}

	public void setMaxRequest(Integer maxRequest) {
		this.maxRequest = maxRequest;
	}

	public Integer getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(Integer queueSize) {
		this.queueSize = queueSize;
	}

	public String getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
	}

	public String getValidationType() {
		return validationType;
	}

	public void setValidationType(String validationType) {
		this.validationType = validationType;
	}

	private void validateHandlerParameters(Integer maxRequest, Integer queueSize, String logLevel,
			String validationType) {
		if (maxRequest == null) {
			throw new RuntimeException("Set the LwiHandler.maxRequest value properly! e.g.:  20");
		}

		if (queueSize == null) {
			throw new RuntimeException("Set the LwiHandler.queueSize value properly! e.g.:  20");
		}

		boolean validLogLevel = false;
		for (int i = 0; i < MessageLogLevel.values().length; i++) {
			MessageLogLevel ll = MessageLogLevel.values()[i];
			if (ll.toString().equals(logLevel)) {
				validLogLevel = true;
				break;
			}
		}
		if (!validLogLevel) {
			throw new RuntimeException("Set the LwiHandler.logLevel value logLevel! e.g.:  NONE, MIN, CTX, FULL");
		}

		boolean validvalidationType = false;
		for (int i = 0; i < ValidationType.values().length; i++) {
			ValidationType vt = ValidationType.values()[i];
			if (vt.toString().equals(validationType)) {
				validvalidationType = true;
				break;
			}
		}
		if (!validvalidationType) {
			throw new RuntimeException("Set the LwiHandler.validationType value logLevel! e.g.: NO, CTX, MSG");
		}

		parametersAreValidated = true;
	}

	private class LwiRequestLimitExceededHandler implements HttpHandler {

		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {
			
			log.warn(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequest, queueSize) + " " + exchange.getRequestURI());

			exchange.setStatusCode(REQUEST_LIMIT_ERROR_CODE);
			Sender sender = exchange.getResponseSender();
			sender.send(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequest, queueSize));

		}

	}

	
	/*
	 * EZ MÉG NEM MŰKÖDIK A CONFIGBÓL!!!
	 */
	
	public static final class Wrapper implements HandlerWrapper {

		private Integer maxRequest;
		private Integer queueSize;
		private String logLevel;
		private String validationType;

		public Wrapper(Integer maxRequest, Integer queueSize, String logLevel, String validationType) {
			super();
			this.maxRequest = maxRequest;
			this.queueSize = queueSize;
			this.logLevel = logLevel;
			this.validationType = validationType;
		}

		@Override
		public HttpHandler wrap(HttpHandler exchange) {
			return new LwiHandler(exchange, maxRequest, queueSize, logLevel, validationType);
		}

	}

	public static final class Builder implements HandlerBuilder {

		@Override
		public String name() {
			return "lwi";
		}

		@Override
		public Map<String, Class<?>> parameters() {
			Map<String, Class<?>> ret = new HashMap<>();
			ret.put("maxRequest", String.class);
			ret.put("queueSize", String.class);
			ret.put("logLevel", String.class);
			ret.put("validationType", String.class);
			return ret;
		}

		@Override
		public Set<String> requiredParameters() {
			return new HashSet<>(Arrays.asList("maxRequest", "queueSize", "logLevel", "validationType"));
		}

		@Override
		public String defaultParameter() {
			return null;
		}

		@Override
		public HandlerWrapper build(Map<String, Object> config) {
			return new Wrapper((Integer) config.get("maxRequest"), (Integer) config.get("queueSize"),
					(String) config.get("logLevel"), (String) config.get("validationType"));
		}
	}

}
