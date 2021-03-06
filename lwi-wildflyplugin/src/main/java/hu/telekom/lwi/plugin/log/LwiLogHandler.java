package hu.telekom.lwi.plugin.log;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.LwiHandler;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiLogHandler implements HttpHandler {

	private static final String REQUEST_LOG_MAIN = "[%s][%s][%s > %s.%s]";
	private static final String RESPONSE_LOG_MAIN = "[%s][%s][%s < %s.%s]";
	protected static final String CTX_LOG = "[RequestId: %s CorrelationId: %s UserId: %s]";
	
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

		String[] requestPath = exchange.getRequestPath().split("/");
		
		String caller = LwiLogAttribute.EMPTY;
		if (exchange.getSecurityContext() != null && exchange.getSecurityContext().getAuthenticatedAccount() != null && exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() != null) {
			caller = exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName();
		}

		final int firstElementOnPath = 1; // set which part of the url should be parsed as provider and operation
		String provider = LwiLogAttribute.EMPTY;
		String operation = LwiLogAttribute.EMPTY;
		for (int i = firstElementOnPath; i < requestPath.length; i++) {
			if (i >= requestPath.length - 1) {
				operation = requestPath[i];
			} else if (i == firstElementOnPath) {
				provider = requestPath[i];
			} else {
				provider += "."+requestPath[i];
			}
		}

		boolean infoFromHeaders = true;

		StringBuilder requestLogMessage = new StringBuilder(String.format(REQUEST_LOG_MAIN, lwiRequestId, getTimestamp(), caller, provider, operation));
		StringBuilder responseLogMessage = new StringBuilder(String.format(RESPONSE_LOG_MAIN, lwiRequestId, "%s", caller, provider, operation));
		
		if (logLevel != LwiLogLevel.MIN) {
			
			String requestId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.RequestId);
			String correlationId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.CorrelationId);
			String userId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.UserId);

			if (requestId != null && correlationId != null && userId != null) {
				infoFromHeaders = true;
				requestLogMessage.append(String.format(CTX_LOG, requestId, correlationId, userId));
				responseLogMessage.append(String.format(CTX_LOG, requestId, correlationId, userId));
			} else {
				infoFromHeaders = false;
			}
		}

		final LwiConduitWrapper conduitHandler;
		
		if (logLevel == LwiLogLevel.FULL || !infoFromHeaders) {
			conduitHandler = new LwiConduitWrapper(messageLog, logLevel, requestLogMessage.toString(), responseLogMessage.toString(), !infoFromHeaders);
			
			exchange.addRequestWrapper(conduitHandler.getRequestConduit());
			exchange.addResponseWrapper(conduitHandler.getResponseConduit());
		} else {
			conduitHandler = null;

			messageLog.info(requestLogMessage);
		}

		exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
			@Override
			public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

				if (conduitHandler != null) {
					conduitHandler.log(false);
				} else {
					messageLog.info(String.format(responseLogMessage.toString(), getTimestamp()));
				}

				nextListener.proceed();

				log.debug(String.format("[%s] LwiLogHandler > handle complete!", lwiRequestId));

			}
		});

		next.handleRequest(exchange);
	}
	
	
	public void setLogLevel(String logLevel) {
		this.logLevel = LwiLogLevel.valueOf(logLevel);
	}
	
	public static String getTimestamp() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
		return sdf.format(new Date());
	}

}
