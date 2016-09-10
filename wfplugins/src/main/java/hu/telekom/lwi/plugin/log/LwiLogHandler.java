package hu.telekom.lwi.plugin.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.logging.Logger;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.Conduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import hu.telekom.lwi.plugin.LwiHandler;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import io.undertow.util.ConduitFactory;

public class LwiLogHandler implements HttpHandler {

	private static final int MAXBUFFER = 100000;

	private static final String REQUEST_LOG_MAIN = "[%s][%s > %s.%s]";
	private static final String RESPONSE_LOG_MAIN = "[%s][%s < %s.%s]";
	protected static final String CTX_LOG = "[RequestId: %s CorrelationId: %s UserId: %s]";
	
	private HttpHandler next = null;

	private final Logger log = Logger.getLogger(this.getClass());
	private final Logger messageLog = Logger.getLogger("hu.telekom.lwi.message.log");

	private LwiLogLevel logLevel = LwiLogLevel.CTX;

	public LwiLogHandler(HttpHandler next) {
		this.next = next;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.debug(String.format("[%s] LwiLogHandler > start request/response log handling...", lwiRequestId));

		String[] requestPath = exchange.getRequestPath().split("/");
		
		String caller = LwiLogAttribute.EMPTY;
		if (exchange.getSecurityContext() != null && exchange.getSecurityContext().getAuthenticatedAccount() != null && exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() != null) {
			caller = exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName();
		}
		
		String provider = requestPath.length >= 2 ? requestPath[1] : LwiLogAttribute.EMPTY;
		String operation = requestPath.length >= 3 ? requestPath[2] : LwiLogAttribute.EMPTY;

		boolean infoFromHeaders = false;

		StringBuilder requestLogMessage = new StringBuilder(String.format(REQUEST_LOG_MAIN, lwiRequestId, caller, provider, operation));
		StringBuilder responseLogMessage = new StringBuilder(String.format(RESPONSE_LOG_MAIN, lwiRequestId, caller, provider, operation));
		
		final LwiConduitWrapper<StreamSourceConduit, LwiRequestConduit> requestConduit;
		final LwiConduitWrapper<StreamSinkConduit, LwiResponseConduit> responseConduit;
		
		if (logLevel != LwiLogLevel.MIN) {
			
			String requestId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.RequestId);
			String correlationId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.CorrelationId);
			String userId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.UserId);

			if (requestId != null && correlationId != null && userId != null) {
				infoFromHeaders = true;
				requestLogMessage.append(String.format(CTX_LOG, requestId, correlationId, userId));
				responseLogMessage.append(String.format(CTX_LOG, requestId, correlationId, userId));
			}
		}

		if (logLevel == LwiLogLevel.FULL || !infoFromHeaders) {
			final boolean contextFromMessage = !infoFromHeaders;
			
			requestConduit = new LwiConduitWrapper<StreamSourceConduit, LwiRequestConduit>() {
	
				private LwiRequestConduit requestConduit;

				@Override
				public LwiRequestConduit getConduit() {
					return requestConduit;
				}

				@Override
				public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exchange) {
					requestConduit = new LwiRequestConduit(factory.create(), requestLogMessage.toString(), contextFromMessage);
					return requestConduit;
				}
			};
			exchange.addRequestWrapper(requestConduit);
	
			responseConduit = new LwiConduitWrapper<StreamSinkConduit, LwiResponseConduit>() {
	
				private LwiResponseConduit responseConduit;

				@Override
				public LwiResponseConduit getConduit() {
					return responseConduit;
				}

				public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
					responseConduit = new LwiResponseConduit(factory.create(), responseLogMessage.toString());
					return responseConduit;
				}
			};
			exchange.addResponseWrapper(responseConduit);
		} else {
			messageLog.info(requestLogMessage);
			requestConduit = null;
			responseConduit = null;
		}

		exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
			@Override
			public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

				if (requestConduit != null) {
					requestConduit.getConduit().logRequest(false);
				}
				if (responseConduit != null) {
					responseConduit.getConduit().logResponse(false);
				} else {
					messageLog.info(responseLogMessage);
				}

				nextListener.proceed();

				log.debug(String.format("[%s] LwiLogHandler > handle complete!", lwiRequestId));

			}
		});

		next.handleRequest(exchange);
	}
	
	public class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

		private String requestLog;
		private StringBuffer requestBuffer;
		private int partCounter = 0;
		private boolean contextFromMessage;
		
		protected LwiRequestConduit(StreamSourceConduit next, String requestLog, boolean contextFromMessage) {
			super(next);
			this.requestLog = requestLog;
			this.requestBuffer = new StringBuffer();
			this.contextFromMessage = contextFromMessage;
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {

			int pos = dst.position();
			int res = super.read(dst);

			if (res > 0) {
				byte[] d = new byte[res];
				for (int i = 0; i < res; ++i) {
					d[i] = dst.get(i + pos);

				}
				requestBuffer.append(new String(d));
			}

			if (requestBuffer.length() > MAXBUFFER) {
				logRequest(true);
			}

			return res;
		}

		@Override
		public long transferTo(long position, long count, FileChannel target) throws IOException {
			return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
		}

		@Override
		public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
			return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
		}

		@Override
		public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
			for (int i = offs; i < len; ++i) {
				if (dsts[i].hasRemaining()) {
					return read(dsts[i]);
				}
			}
			return 0;
		}

		public void logRequest(boolean partial) {
			if (contextFromMessage) {
				String requestId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.RequestId, requestBuffer.toString());
				String correlationId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.CorrelationId, requestBuffer.toString());
				String userId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.UserId, requestBuffer.toString());

				requestLog += String.format(CTX_LOG, requestId, correlationId, userId);
				contextFromMessage = false;
			}
			
			if (partial || partCounter++ > 0) {
				messageLog.info(String.format("%s[REQUEST (partial request part - %s) > %s]", requestLog, (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
			} else {
				messageLog.info(String.format("%s[REQUEST > %s]", requestLog, LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
			}
			requestBuffer.setLength(0);
		}
	}

	public class LwiResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		private String responseLog;
		private StringBuffer responseBuffer;
		private int partCounter = 0;

		protected LwiResponseConduit(StreamSinkConduit next, String responseLog) {
			super(next);
			this.responseLog = responseLog;
			this.responseBuffer = new StringBuffer();
		}

		@Override
		public int write(ByteBuffer src) throws IOException {

			int pos = src.position();
			int res = super.write(src);
			if (res > 0) {
				byte[] d = new byte[res];
				for (int i = 0; i < res; ++i) {
					d[i] = src.get(i + pos);

				}
				responseBuffer.append(new String(d));
			}

			if (responseBuffer.length() > MAXBUFFER) {
				logResponse(true);
			}

			return res;
		}

		@Override
		public long write(ByteBuffer[] dsts, int offs, int len) throws IOException {
			for (int i = offs; i < len; ++i) {
				if (dsts[i].hasRemaining()) {
					return write(dsts[i]);
				}
			}
			return 0;
		}

		@Override
		public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
			return src.transferTo(position, count, new ConduitWritableByteChannel(this));
		}

		@Override
		public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
			return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
		}

		@Override
		public int writeFinal(ByteBuffer src) throws IOException {
			return Conduits.writeFinalBasic(this, src);
		}

		@Override
		public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
			return Conduits.writeFinalBasic(this, srcs, offset, length);
		}

		public void logResponse(boolean partial) {
			if (partial || partCounter++ > 0) {
				messageLog.info(String.format("%s[RESPONSE (partial response part - %d) > %s]", responseLog, (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
			} else {
				messageLog.info(String.format("%s[RESPONSE > %s]", responseLog, LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
			}
			responseBuffer.setLength(0);
		}
	}
	
	public void setLogLevel(String logLevel) {
		this.logLevel = LwiLogLevel.valueOf(logLevel);
	}

	public interface LwiConduitWrapper<T extends Conduit, S extends T> extends ConduitWrapper<T> {
		public S getConduit();
	}
}
