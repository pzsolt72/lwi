package hu.telekom.lwi.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import hu.telekom.lwi.plugin.limit.RequestLimitHandler;
import hu.telekom.lwi.plugin.log.LogRequestHandler;
import hu.telekom.lwi.plugin.log.LogResponseHandler;
import hu.telekom.lwi.plugin.log.LwiLogHandler;
import hu.telekom.lwi.plugin.security.SecurityHandler;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.builder.HandlerBuilder;

public class LwiHandler implements HttpHandler {

	private HttpHandler next;
	private HttpHandler first;

	private String maxRequest;
	private String queueSize;
	private String logLevel;
	private String validationType;

	public enum LogLevel {
		NONE, MIN, CTX, FULL
	};

	public enum ValidationType {
		NO, CTX, MSG
	};

	private String requestData;
	private File requestFile;

	/**
	 * 
	 * @param next
	 */
	
	public LwiHandler(HttpHandler next) {

		this.next = next;
	}
	
	public LwiHandler(HttpHandler next, String maxRequest, String queueSize, String logLevel, String validationType) {

		this.next = next;
		this.maxRequest = maxRequest;
		this.queueSize = queueSize;
		this.logLevel = logLevel;
		this.validationType = validationType;

		
		if (maxRequest == null || Integer.parseInt(maxRequest) < -1) {
			throw new RuntimeException("Set the LwiHandler.maxRequest value properly! e.g.:  20");
		}

		if (queueSize == null || Integer.parseInt(queueSize) < -1) {
			throw new RuntimeException("Set the LwiHandler.queueSize value properly! e.g.:  20");
		}

		boolean validLogLevel = false;
		for (int i = 0; i < LogLevel.values().length; i++) {
			LogLevel ll = LogLevel.values()[i];
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
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

	LwiContext lwiContext = new LwiContext();

//		LogResponseHandler logResponseHandler = new LogResponseHandler(next);
	
//		LogRequestHandler logRequestHandler = new LogRequestHandler(lwiContext, logResponseHandler);

		LwiLogHandler lwiLogHandler = new LwiLogHandler(next);
	
		SecurityHandler securityHandler = new SecurityHandler(lwiLogHandler);
/*
		RequestLimitHandler requestLimitHandler = new RequestLimitHandler(securityHandler);
		requestLimitHandler.setMaximumConcurrentRequests(Integer.parseInt(maxRequest));
		requestLimitHandler.setQueueSize(Integer.parseInt(queueSize));

		// first = requestLimitHandler;

		requestLimitHandler.handleRequest(exchange);
*/
		
		RequestLimit requestLimit = new RequestLimit(Integer.parseInt(maxRequest), Integer.parseInt(queueSize));
		requestLimit.setFailureHandler(new ResponseCodeHandler(503));
		
		requestLimit.handleRequest(exchange, securityHandler);
	}

	public String getMaxRequest() {
		return maxRequest;
	}

	public void setMaxRequest(String maxRequest) {
		this.maxRequest = maxRequest;
	}

	public String getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(String queueSize) {
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

	
	/*
	public static final class Wrapper implements HandlerWrapper {

		private String maxRequest;
		private String queueSize;
		private String logLevel;
		private String validationType;

		public Wrapper(String maxRequest, String queueSize, String logLevel, String validationType) {
			super();
			this.maxRequest = maxRequest;
			this.queueSize = queueSize;
			this.logLevel = logLevel;
			this.validationType = validationType;
		}

		@Override
		public HttpHandler wrap(HttpHandler exchange) {
			// TODO Auto-generated method stub
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
			return new HashSet<>(Arrays.asList("maxRequest", "queueSize","logLevel","validationType"));
		}

		@Override
		public String defaultParameter() {
			return null;
		}

		@Override
		public HandlerWrapper build(Map<String, Object> config) {
			return new Wrapper((String) config.get("maxRequest"), (String) config.get("queueSize"),
					(String) config.get("logLevel"), (String) config.get("validationType"));
		}
	}
	*/

}
