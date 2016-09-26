package hu.telekom.lwi.plugin.log;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.data.LwiCall;
import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.util.LwiConduitTarget;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;

public class LwiLogConduitTarget implements LwiConduitTarget {

	private Logger messageLog;
	
	private LwiCall lwiCall;
	private LwiRequestData lwiRequestData;
	
	private int requestPart = 0;
	private int responsePart = 0;
	
	private boolean requestFinished = false;

	public LwiLogConduitTarget(Logger messageLog, LwiCall lwiCall, LwiRequestData lwiRequestData) {
		this.messageLog = messageLog;
		this.lwiCall = lwiCall;
		this.lwiRequestData = lwiRequestData;
	}
	
	public void processRequest(String request, boolean requestFinished) {
		if (!this.requestFinished) {
			this.requestFinished = requestFinished;
			if (requestPart++ > 0 || !requestFinished) {
				messageLog.info(String.format("%s[REQUEST (request part - %s) > %s]", getRequestLogPrefix(), (requestFinished ? "last" : Integer.toString(requestPart)), LwiLogAttributeUtil.cleanseMessage(request)));
			} else {
				messageLog.info(String.format("%s[REQUEST %s> %s]", getRequestLogPrefix(), lwiCall.isPartial()?"(partial - first part only!) ":"", LwiLogAttributeUtil.cleanseMessage(request)));
			}
		}
	}

	public void acceptRequest(String request, boolean requestFinished) {
		if (!this.requestFinished) {
			this.requestFinished = requestFinished;
			messageLog.info(getRequestLogPrefix());
		}
	}

	public void processResponse(String response, boolean responseFinished) {
		if (responsePart++ > 0 || !responseFinished) {
			messageLog.info(String.format("%s[RESPONSE (response part - %s) > %s]", getResponseLogPrefix(responseFinished), (responseFinished ? "last" : Integer.toString(responsePart)), LwiLogAttributeUtil.cleanseMessage(response)));
		} else {
			messageLog.info(String.format("%s[RESPONSE > %s]", getResponseLogPrefix(responseFinished), LwiLogAttributeUtil.cleanseMessage(response)));
		}
	}

	public void acceptResponse(String response, boolean responseFinished) {
		messageLog.info(getResponseLogPrefix(responseFinished));
	}

	public void exchangeCompleted() {}
	
	private String getRequestLogPrefix() {
		StringBuilder sb = new StringBuilder(lwiCall.toRequestLog());
		if (lwiRequestData != null) {
			sb.append(lwiRequestData.toLog());
		}
		return sb.toString();
	}

	private String getResponseLogPrefix(boolean finished) {
		long responseFinished = System.currentTimeMillis();
		StringBuilder sb = new StringBuilder(lwiCall.toResponseLog(responseFinished));
		if (lwiRequestData != null) {
			sb.append(lwiRequestData.toLog());
		}
		if (finished) {
			sb.append(lwiCall.toDurationLog(responseFinished));
		}
		return sb.toString();
	}

}
