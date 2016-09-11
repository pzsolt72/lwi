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

import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;

public class LwiConduitWrapper {

	private static final int MAXBUFFER = 100000;

	private Logger messageLog;
	private LwiLogLevel logLevel;
	
	private LwiConduit<StreamSourceConduit, LwiRequestConduit> requestConduit;
	private LwiConduit<StreamSinkConduit, LwiResponseConduit> responseConduit;
	
	public LwiConduitWrapper(Logger messageLog, LwiLogLevel logLevel, String requestLogMessage, String responseLogMessage, boolean contextFromMessage) {
		this.messageLog = messageLog;
		this.logLevel = logLevel;
		this.requestConduit = new LwiConduit<StreamSourceConduit, LwiRequestConduit>() {
			
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

		this.responseConduit = new LwiConduit<StreamSinkConduit, LwiResponseConduit>() {
			
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
	}

	public void log(boolean partial) {
		log(false, partial);
	}
	
	public void log(boolean onlyRequest, boolean partial) {
		// if we are logging not onlyRequest then request must have been at the end (not partial)
		getRequestConduit().getConduit().logRequest(onlyRequest && partial);
		if (!onlyRequest) {
			getResponseConduit().getConduit().logResponse(partial);
		}
	}
	
	public LwiConduit<StreamSourceConduit, LwiRequestConduit> getRequestConduit() {
		return requestConduit;
	}

	public LwiConduit<StreamSinkConduit, LwiResponseConduit> getResponseConduit() {
		return responseConduit;
	}


	public interface LwiConduit<T extends Conduit, S extends T> extends ConduitWrapper<T> {
		public S getConduit();
	}

	public class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

		private String requestLog;
		private StringBuffer requestBuffer;
		private int partCounter = 0;
		private boolean contextFromMessage;
		
		private boolean logAvailable = false;
		
		protected LwiRequestConduit(StreamSourceConduit next, String requestLog, boolean contextFromMessage) {
			super(next);
			this.requestLog = requestLog;
			this.requestBuffer = new StringBuffer();
			this.contextFromMessage = contextFromMessage;
		}

		public void logRequest(boolean partial) {
			if (logAvailable) {
				if (contextFromMessage) {
					String requestId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.RequestId, requestBuffer.toString());
					String correlationId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.CorrelationId, requestBuffer.toString());
					String userId = LwiLogAttributeUtil.getMessageAttribute(LwiLogAttribute.UserId, requestBuffer.toString());
	
					requestLog += String.format(LwiLogHandler.CTX_LOG, requestId, correlationId, userId);
					contextFromMessage = false;
				}
				
				if (logLevel == LwiLogLevel.FULL) {
					if (partCounter++ > 0 || partial) {
						messageLog.info(String.format("%s[REQUEST (partial request part - %s) > %s]", requestLog, (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
					} else {
						messageLog.info(String.format("%s[REQUEST > %s]", requestLog, LwiLogAttributeUtil.cleanseMessage(requestBuffer.toString())));
					}
				} else if (!partial) {
					messageLog.info(requestLog);
				}
				requestBuffer.setLength(0);
				logAvailable = false;
			}
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

			logAvailable = true;

			if (requestBuffer.length() > MAXBUFFER) {
				log(true, true);
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

	}

	public class LwiResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		private String responseLog;
		private StringBuffer responseBuffer;
		private int partCounter = 0;
		
		private boolean logAvailable = false;

		protected LwiResponseConduit(StreamSinkConduit next, String responseLog) {
			super(next);
			this.responseLog = responseLog;
			this.responseBuffer = new StringBuffer();
		}

		public void logResponse(boolean partial) {
			if (logAvailable) {
				if (logLevel == LwiLogLevel.FULL) {
					if (partCounter++ > 0 || partial) {
						messageLog.info(String.format("%s[RESPONSE (partial response part - %s) > %s]", String.format(responseLog, LwiLogHandler.getTimestamp()), (partial ? partCounter : "last"), LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
					} else {
						messageLog.info(String.format("%s[RESPONSE > %s]", String.format(responseLog, LwiLogHandler.getTimestamp()), LwiLogAttributeUtil.cleanseMessage(responseBuffer.toString())));
					}
				} else if (!partial) {
					messageLog.info(String.format(responseLog, LwiLogHandler.getTimestamp()));
				}
				logAvailable = false;
				responseBuffer.setLength(0);
			}
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

			logAvailable = true;

			if (responseBuffer.length() > MAXBUFFER) {
				log(true);
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

	}

}
