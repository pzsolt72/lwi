package hu.telekom.lwi.plugin.util;

import org.xnio.conduits.Conduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;

public class LwiConduitWrapper implements ExchangeCompletionListener {

	protected static final int MAXBUFFER = 100000;

	private LwiConduitTarget target;
	
	private boolean processRequest;
	private boolean processResponse;
	
	private LwiConduit<StreamSourceConduit, LwiRequestConduit> requestConduit;
	private LwiConduit<StreamSinkConduit, LwiResponseConduit> responseConduit;
	
	public LwiConduitWrapper(LwiConduitTarget target, boolean processRequest, boolean processResponse) {
		this.target = target;
		this.processRequest = processRequest;
		this.processResponse = processResponse;
	}

	public void applyConduits(HttpServerExchange exchange) {
		if (processRequest) {
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
			processRequest(true);
		}
		if (processResponse) {
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
		try {
			processResponse(true);
			target.exchangeCompleted();
		} finally {
			nextListener.proceed();
		}
	}


	public void processRequest(boolean lastPart) {
		if (processRequest && requestConduit.getConduit().isDataAvailable()) {
			target.processRequest(requestConduit.getConduit().getMessage(), lastPart);
		} else {
			target.acceptRequest(null, lastPart);
		}
	}

	public void processResponse(boolean lastPart) {
		processRequest(true);
		
		if (processResponse && responseConduit.getConduit().isDataAvailable()) {
			target.processResponse(responseConduit.getConduit().getMessage(), lastPart);
		} else {
			target.acceptResponse(null, lastPart);
		}
	}

	
	public interface LwiConduit<T extends Conduit, S extends T> extends ConduitWrapper<T> {
		public S getConduit();
	}

}
