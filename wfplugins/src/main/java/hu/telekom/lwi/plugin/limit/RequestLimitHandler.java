package hu.telekom.lwi.plugin.limit;

import org.jboss.logging.Logger;

import io.undertow.Handlers;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.ResponseCodeHandler;

public class RequestLimitHandler implements HttpHandler {

	private static final Logger log = Logger.getLogger(RequestLimitHandler.class);

	private static final String REQUEST_LIMIT_ERROR_MSG = "Request limit exceeded! max request allowed: %s, queue size: %s";

	private static final int REQUEST_LIMIT_ERROR_CODE = 509;

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
			requestLimitHandler.setFailureHandler(new LwiRequestLimitExceededHandler());
		}

		log.info(String.format("RequestLimitHandler.handleRequest invoked (%d, %d)", maximumConcurrentRequests,
				queueSize));
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

	private class LwiRequestLimitExceededHandler implements HttpHandler {

		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {

			exchange.setStatusCode(REQUEST_LIMIT_ERROR_CODE);
			Sender sender = exchange.getResponseSender();
			sender.send(String.format(REQUEST_LIMIT_ERROR_MSG, String.valueOf(maximumConcurrentRequests), String.valueOf(queueSize)));

		}

	}

}
