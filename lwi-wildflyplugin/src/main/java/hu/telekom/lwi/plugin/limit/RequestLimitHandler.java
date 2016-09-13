package hu.telekom.lwi.plugin.limit;

import org.jboss.logging.Logger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.ResponseCodeHandler;

public class RequestLimitHandler implements HttpHandler {
	
	private static final Logger log = Logger.getLogger(RequestLimitHandler.class);
	
	private RequestLimit requestLimitHandler = null;
	private HttpHandler next;
	
	private int maximumConcurrentRequests = 0;
	private int queueSize = 0;

	public RequestLimitHandler(HttpHandler next) {
		this.next = next;
	}
	
	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		if (requestLimitHandler == null) {
			requestLimitHandler = new RequestLimit(maximumConcurrentRequests, queueSize);
			requestLimitHandler.setFailureHandler(new ResponseCodeHandler(503));
		}
		
		log.info(String.format("RequestLimitHandler.handleRequest invoked (%d, %d)", maximumConcurrentRequests, queueSize));
		requestLimitHandler.handleRequest(exchange, next);
	}

	public void setMaximumConcurrentRequests(int maximumConcurrentRequests) {
		this.maximumConcurrentRequests = maximumConcurrentRequests;
		if (requestLimitHandler != null) {
			requestLimitHandler.setMaximumConcurrentRequests(maximumConcurrentRequests);
		}
	}

	public void setQueueSize(int queueSize) {
		if (requestLimitHandler != null) {
			log.error("Cannot set queueSize after first init!");
		} else {
			this.queueSize = queueSize;
		}
	}

}
