package hu.telekom.lwi.plugin.limit;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.LwiHandler;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiRequestLimitExceededHandler implements HttpHandler {

	private static final String REQUEST_LIMIT_ERROR_MSG = "Request limit exceeded! max request allowed: %s, queue size: %s";
	private static final int REQUEST_LIMIT_ERROR_CODE = 509;

	private final Logger log = Logger.getLogger(LwiRequestLimitExceededHandler.class);
	private Integer queueSize;
	private Integer maxRequests;

	
	
	public LwiRequestLimitExceededHandler(Integer maxRequests, Integer queueSize) {
		super();
		this.queueSize = queueSize;
		this.maxRequests = maxRequests;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		log.warn(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequests, queueSize) + " " + exchange.getRequestURI());

		exchange.setStatusCode(REQUEST_LIMIT_ERROR_CODE);
		Sender sender = exchange.getResponseSender();
		sender.send(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequests, queueSize));

	}

	public Integer getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(Integer queueSize) {
		this.queueSize = queueSize;
	}

	public Integer getMaxRequests() {
		return maxRequests;
	}

	public void setMaxRequests(Integer maxRequests) {
		this.maxRequests = maxRequests;
	}

}
