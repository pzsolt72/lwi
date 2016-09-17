package hu.telekom.lwi.plugin.log;

import org.jboss.logging.Logger;
import org.xnio.conduits.Conduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;

public class LwiConduitWrapper implements ExchangeCompletionListener {

	protected static final int MAXBUFFER = 100000;

	private Logger messageLog;
	
	private LwiCall lwiCall;
	private LwiRequestData lwiRequestData;
	private boolean logMessages;
	
	private LwiConduit<StreamSourceConduit, LwiRequestConduit> requestConduit;
	private LwiConduit<StreamSinkConduit, LwiResponseConduit> responseConduit;
	
	public LwiConduitWrapper(Logger messageLog, LwiCall lwiCall, LwiRequestData lwiRequestData, boolean logMessages) {
		this.messageLog = messageLog;
		this.lwiCall = lwiCall;
		this.lwiRequestData = lwiRequestData;
		this.logMessages = logMessages;
	}

	public void applyConduits(HttpServerExchange exchange) {
		if (processRequestRequired()) {
			requestConduit = new LwiConduit<StreamSourceConduit, LwiRequestConduit>() {
				
				private LwiRequestConduit requestConduit;
	
				@Override
				public LwiRequestConduit getConduit() {
					return requestConduit;
				}
	
				@Override
				public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
					requestConduit = new LwiRequestConduit(factory.create(), LwiConduitWrapper.this);
					return requestConduit;
				}
			};
			exchange.addRequestWrapper(requestConduit);
		} else {
			logRequest(true);
		}
		if (processResponseRequired()) {
			responseConduit = new LwiConduit<StreamSinkConduit, LwiResponseConduit>() {
				
				private LwiResponseConduit responseConduit;
	
				@Override
				public LwiResponseConduit getConduit() {
					return responseConduit;
				}
	
				public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
					responseConduit = new LwiResponseConduit(factory.create(), LwiConduitWrapper.this);
					return responseConduit;
				}
			};
			exchange.addResponseWrapper(responseConduit);
		}
	}

	public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

		logResponse(false);

		nextListener.proceed();
	}


	public void logRequest(boolean partial) {
		logRequest(partial, false);
	}
	
	public void logRequest(boolean partial, boolean lastChance) {
		if (logMessages) {
			requestConduit.getConduit().log(messageLog, getRequestLogPrefix(), partial);
		} else if (!processRequestRequired() || lastChance) {
			messageLog.info(getRequestLogPrefix());
		}
	}

	public void logResponse(boolean partial) {
		// check if last part of request has been logged already
		logRequest(false, true); 
		
		if (logMessages) {
			responseConduit.getConduit().log(messageLog, getResponseLogPrefix(), partial);
		} else {
			messageLog.info(getResponseLogPrefix());
		}
	}

	
	public String getRequestLogPrefix() {
		StringBuilder sb = new StringBuilder(lwiCall.toRequestLog());
		if (lwiRequestData != null) {
			sb.append(lwiRequestData.toLog());
		}
		return sb.toString();
	}

	public String getResponseLogPrefix() {
		long responseFinished = System.currentTimeMillis();
		StringBuilder sb = new StringBuilder(lwiCall.toResponseLog(responseFinished));
		if (lwiRequestData != null) {
			sb.append(lwiRequestData.toLog());
		}
		if (requestConduit != null && responseConduit != null) {
			sb.append(lwiCall.toDurationLog(requestConduit.getConduit().getRequestFinished(), responseConduit.getConduit().getResponseStarted(), responseFinished));
		} else {
			sb.append(lwiCall.toDurationLog(lwiCall.getRequestFinished(), lwiCall.getResponseStarted(), responseFinished));
		}
		return sb.toString();
	}

	public boolean processRequestRequired() {
		return logMessages || (lwiRequestData != null && lwiRequestData.parseRequestRequired());
	}

	public boolean processResponseRequired() {
		return logMessages;
	}

	protected LwiRequestData getLwiRequestData() {
		return lwiRequestData;
	}
	
	
	public interface LwiConduit<T extends Conduit, S extends T> extends ConduitWrapper<T> {
		public S getConduit();
	}

}
