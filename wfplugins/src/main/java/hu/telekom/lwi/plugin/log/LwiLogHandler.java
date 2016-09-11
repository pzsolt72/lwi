package hu.telekom.lwi.plugin.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import hu.telekom.lwi.plugin.LwiHandler;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import io.undertow.util.ConduitFactory;

public class LwiLogHandler implements HttpHandler {

	private static final int MAXBUFFER = 100000;

	private int partCounterReq = 0;
	private int partCounterRes = 0;
	private StringBuffer requestBuffer = null;
	private StringBuffer responseBuffer = null;

	private HttpHandler next = null;

	private final Logger log = Logger.getLogger(this.getClass());
	private final Logger messageLog = Logger.getLogger("LWI_LOG_MESSAGE");

	public LwiLogHandler(HttpHandler next) {
		this.next = next;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.debug(String.format("[%s] LwiLogHandler > start request/response log handling...",lwiRequestId));

		requestBuffer = new StringBuffer();
		responseBuffer = new StringBuffer();

		exchange.addRequestWrapper(new ConduitWrapper<StreamSourceConduit>() {

			@Override
			public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exch) {

				return new LwiRequestConduit(factory.create());
			}
		});

		exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {

			public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
				return new LwiResponseConduit(factory.create());
			}
		});

		exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
			@Override
			public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

				if (partCounterReq > 0) {
					partCounterReq++;
					messageLog.info("Partial request part: " + partCounterReq + " last > " + requestBuffer.toString());
				} else {
					messageLog.info("REQUEST > " + requestBuffer.toString());
				}

				if (partCounterRes > 0) {
					partCounterRes++;
					messageLog
							.info("Partial response part: " + partCounterRes + " last > " + responseBuffer.toString());
				} else {
					messageLog.info("RESPONSE > " + responseBuffer.toString());
				}

				responseBuffer.setLength(0);

				requestBuffer.setLength(0);
				nextListener.proceed();

				log.debug(String.format("[%s] LwiLogHandler > handle complete!",lwiRequestId));

			}
		});

		next.handleRequest(exchange);

	}

	public class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

		protected LwiRequestConduit(StreamSourceConduit next) {
			super(next);
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			// TODO Auto-generated method stub

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
				partCounterReq++;
				messageLog.info("Partial request part: " + partCounterReq + " > " + requestBuffer.toString());
				requestBuffer.setLength(0);
			}

			return res;
		}

		@Override
		public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
			// TODO Auto-generated method stub
			return super.read(dsts, offs, len);
		}

		@Override
		public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
			// TODO Auto-generated method stub
			return super.transferTo(count, throughBuffer, target);
		}

		@Override
		public long transferTo(long position, long count, FileChannel target) throws IOException {
			// TODO Auto-generated method stub
			return super.transferTo(position, count, target);
		}

	}

	public class LwiResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		private final List<byte[]> data = new CopyOnWriteArrayList<>();

		protected LwiResponseConduit(StreamSinkConduit next) {
			super(next);
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
				partCounterRes++;
				messageLog.info("Partial response part: " + partCounterRes + " > " + responseBuffer.toString());
				responseBuffer.setLength(0);
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
		public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer)
				throws IOException {
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
