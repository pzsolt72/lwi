package hu.telekom.lwi.plugin.log;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.LwiHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiLogHandler implements HttpHandler {

	private HttpHandler next = null;

	private final Logger log = Logger.getLogger(this.getClass());
	private final Logger messageLog = Logger.getLogger("LWI_LOG_MESSAGE");

	private LwiLogLevel logLevel = LwiLogLevel.CTX;

	public LwiLogHandler(HttpHandler next) {
		this.next = next;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.info(String.format("[%s] LwiLogHandler > start request/response log handling (%s)...", lwiRequestId, logLevel.name()));
		
		LwiCall lwiCall = new LwiCall(exchange, lwiRequestId);
		LwiRequestData lwiRequestData = null;

		if (logLevel != LwiLogLevel.MIN) {
			lwiRequestData = new LwiRequestData(exchange);
		}

		final LwiConduitWrapper conduitHandler = new LwiConduitWrapper(messageLog, lwiCall, lwiRequestData, logLevel == LwiLogLevel.FULL);

		conduitHandler.applyConduits(exchange);

		exchange.addExchangeCompleteListener(conduitHandler);

		lwiCall.setRequestFinished();
		next.handleRequest(exchange);
		lwiCall.setResponseStarted();

		log.info(String.format("[%s] LwiLogHandler > handle complete!", lwiRequestId));
	}
	
	
	public void setLogLevel(String logLevel) {
		this.logLevel = LwiLogLevel.valueOf(logLevel);
	}

}
