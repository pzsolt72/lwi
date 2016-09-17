package hu.telekom.lwi.plugin.log;

import java.text.SimpleDateFormat;
import java.util.Date;

import io.undertow.server.HttpServerExchange;

public class LwiCall {
	
	private static final String REQUEST_LOG_FORMAT = "[%s][%s][%s > %s.%s]";
	private static final String RESPONSE_LOG_FORMAT = "[%s][%s][%s < %s.%s]";
	private static final String DURATION_LOG_FORMAT = "[call: %sms, servicecall: %sms, overhead: %sms]";
	private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";

	private String lwiRequestId;
	private String caller;
	private String provider;
	private String operation;
	private long requestStarted;
	private long requestFinished;
	private long responseStarted;
	
	
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

	public String getCaller() {
		return caller;
	}

	public String getProvider() {
		return provider;
	}

	public String getOperation() {
		return operation;
	}
	
	public long getRequestStarted() {
		return requestStarted;
	}

	public long getRequestFinished() {
		return requestFinished;
	}

	public long getResponseStarted() {
		return responseStarted;
	}

	public String toRequestLog() {
		return String.format(REQUEST_LOG_FORMAT, lwiRequestId, getTimestamp(requestStarted), caller, provider, operation);
	}

	public String toResponseLog(long timeInMillis) {
		return String.format(RESPONSE_LOG_FORMAT, lwiRequestId, getTimestamp(timeInMillis), caller, provider, operation);
	}
	
	public String toDurationLog(long requestFinished, long responseStarted, long responseFinished) {
		long call = responseFinished - requestStarted;
		long service = responseStarted - requestFinished;
		long overhead = call - service;
		return String.format(DURATION_LOG_FORMAT, call>0?Long.toString(call):LwiLogAttribute.EMPTY, service>0?Long.toString(service):LwiLogAttribute.EMPTY, overhead>0?Long.toString(overhead):LwiLogAttribute.EMPTY);
	}

	public static String getTimestamp(long timeInMillis) {
		SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_FORMAT);
		return sdf.format(new Date(timeInMillis));
	}
	
	public void setRequestFinished() {
		this.requestFinished = System.currentTimeMillis();
	}

	public void setResponseStarted() {
		this.responseStarted = System.currentTimeMillis();
	}

}
