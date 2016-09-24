package hu.telekom.lwi.plugin.log;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.LwiHandler;
import hu.telekom.lwi.plugin.data.LwiCall;
import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.util.LwiConduitTarget;
import hu.telekom.lwi.plugin.util.LwiConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiLogHandler implements HttpHandler {

	private static final Logger log = Logger.getLogger(LwiLogHandler.class);
	private static final Logger messageLog = Logger.getLogger("LWI_LOG_MESSAGE");

	private HttpHandler next = null;
	private LwiLogLevel logLevel = LwiLogLevel.CTX;

	public LwiLogHandler(HttpHandler next) {
		this.next = next;
	}

	public LwiLogHandler(HttpHandler next, LwiLogLevel logLevel) {
		this.next = next;
		this.logLevel = logLevel;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.info(String.format("[%s] LwiLogHandler - start request/response log handling (%s)...", lwiRequestId, logLevel.name()));

		LwiCall lwiCall = LwiHandler.getLwiCall(exchange);
		LwiRequestData lwiRequestData = null;
		if (logLevel != LwiLogLevel.MIN) {
			lwiRequestData = LwiHandler.getLwiRequestData(exchange);
		}

		boolean processMessages = logLevel == LwiLogLevel.FULL;

		LwiConduitTarget target = new LwiLogConduitTarget(messageLog, lwiCall, lwiRequestData);
		LwiConduitWrapper conduitHandler;
		
		String request = LwiHandler.getLwiRequest(exchange);
		if (request != null) {
			conduitHandler = new LwiConduitWrapper(target, false, processMessages);
			if (processMessages) {
				target.processRequest(request, true);
			} else {
				target.acceptRequest(request, true);
			}
		} else {
			conduitHandler = new LwiConduitWrapper(target, processMessages, processMessages);
		}
		conduitHandler.applyConduits(exchange);

		exchange.addExchangeCompleteListener(conduitHandler);

		next.handleRequest(exchange);
	}
	
	
	public void setLogLevel(String logLevel) {
		this.logLevel = LwiLogLevel.valueOf(logLevel);
	}

}
