package hu.telekom.lwi.plugin.data;

import java.text.SimpleDateFormat;
import java.util.Date;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import io.undertow.server.HttpServerExchange;

public class LwiCall {
	
	private static final String REQUEST_LOG_FORMAT = "[%s][%s][%s > %s.%s]";
	private static final String RESPONSE_LOG_FORMAT = "[%s][%s][%s < %s.%s]";
	private static final String DURATION_LOG_FORMAT = "[call: %sms, servicecall: %sms, overhead: %sms]";
	private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
	private static final String PROVIDER_SERVICE_SEPARATOR = "/";
	
	private String lwiRequestId;
	private String caller;
	private String provider;
	private String operation;
	private long requestStarted;
	private long serviceCalled;
	private long serviceReturned;
	private boolean partial = false;
	
	public LwiCall(final HttpServerExchange exchange, final String lwiRequestId) {
		this.requestStarted = System.currentTimeMillis();
		this.lwiRequestId = lwiRequestId;
		
		String[] requestPath = exchange.getRequestPath().split("/");
		
		caller = LwiLogAttribute.EMPTY;
		if (exchange.getSecurityContext() != null && exchange.getSecurityContext().getAuthenticatedAccount() != null && exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() != null) {
			caller = exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName();
		}

		final int firstElementOnPath = 1; // set which part of the url should be parsed as provider and operation
		provider = LwiLogAttribute.EMPTY;
		operation = LwiLogAttribute.EMPTY;
		for (int i = firstElementOnPath; i < requestPath.length; i++) {
			if (i >= requestPath.length - 1) {
				operation = requestPath[i];
			} else if (i == firstElementOnPath) {
				provider = requestPath[i];
			} else {
				provider += "."+requestPath[i];
			}
		}
	}

	public String getLwiRequestId() {
		return lwiRequestId;
	}

	public String getCaller() {
		return caller;
	}

	public String getProvider() {
		return provider;
	}

	public String getOperation() {
		return operation;
	}
	
	public String getAccessPoint() {
		return provider + PROVIDER_SERVICE_SEPARATOR + operation;
	}
	
	public long getRequestStarted() {
		return requestStarted;
	}

	public boolean isPartial() {
		return partial;
	}

	public void setPartial() {
		this.partial = true;
	}

	public String toRequestLog() {
		return String.format(REQUEST_LOG_FORMAT, lwiRequestId, getTimestamp(requestStarted), caller, provider, operation);
	}

	public String toResponseLog(long timeInMillis) {
		return String.format(RESPONSE_LOG_FORMAT, lwiRequestId, getTimestamp(timeInMillis), caller, provider, operation);
	}
	
	public String toDurationLog(long responseFinished) {
		long call = responseFinished - requestStarted;
		long service = serviceReturned - serviceCalled;
		long overhead = call - (service > 0 ? service : 0);
		return String.format(DURATION_LOG_FORMAT, call>0?Long.toString(call):LwiLogAttribute.EMPTY, service>0?Long.toString(service):LwiLogAttribute.EMPTY, overhead>0?Long.toString(overhead):LwiLogAttribute.EMPTY);
	}

	public static String getTimestamp(long timeInMillis) {
		SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_FORMAT);
		return sdf.format(new Date(timeInMillis));
	}
	
	public void setServiceCalled() {
		this.serviceCalled = System.currentTimeMillis();
	}

	public void setServiceReturned() {
		this.serviceReturned = System.currentTimeMillis();
	}

}
