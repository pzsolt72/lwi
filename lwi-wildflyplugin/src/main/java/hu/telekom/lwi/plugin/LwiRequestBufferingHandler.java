package hu.telekom.lwi.plugin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpContinue;

public class LwiRequestBufferingHandler implements HttpHandler {

	private static final Logger log = Logger.getLogger(LwiRequestBufferingHandler.class);

	private HttpHandler next = null;
	private boolean requestBuffering;
	
	private int maxBuffers = 5;

	public LwiRequestBufferingHandler(HttpHandler next) {
		this.next = next;
	}

	public LwiRequestBufferingHandler(HttpHandler next, int maxBuffers, boolean requestBuffering) {
		this.next = next;
		this.maxBuffers = maxBuffers;
		this.requestBuffering = requestBuffering;
	}

	public void setRequestBuffering(boolean requestBuffering) {
		this.requestBuffering = requestBuffering;
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);

		log.info(String.format("[%s] LwiRequestBufferingHandler - start request buffering (active: %s)...", lwiRequestId, Boolean.toString(requestBuffering)));

		if (requestBuffering) {
			bufferRequest(exchange);
			
			LwiRequestData requestData = LwiHandler.getLwiRequestData(exchange);
			String request = LwiHandler.getLwiRequest(exchange);
			Stack<String> qNames = new Stack<>();
			qNames.push("ROOT");
			LwiLogAttributeUtil.getMessageAttributes(qNames, requestData, request);

			log.info(String.format("[%s] LwiRequestBufferingHandler - buffering completed!", lwiRequestId));
		} else {
			log.info(String.format("[%s] LwiRequestBufferingHandler - ended without buffering.", lwiRequestId));
		}
		
		next.handleRequest(exchange);
	}

	@SuppressWarnings("resource") // INFO: it will be closed by Connectors.removeBufferedRequest
	public void bufferRequest(final HttpServerExchange exchange) throws Exception {
		if (!exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
			final StreamSourceChannel channel = exchange.getRequestChannel();
			int readBuffers = 0;
			final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
			PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
			do {
				int r;
				ByteBuffer b = buffer.getBuffer();
				r = channel.read(b);
				if (r == -1) { // TODO: listener read
					if (b.position() == 0) {
						buffer.close();
					} else {
						b.flip();
						bufferedData[readBuffers] = buffer;
					}
					break;
				} else if (r == 0) {
					final PooledByteBuffer finalBuffer = buffer;
					final int finalReadBuffers = readBuffers;
					channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {

						PooledByteBuffer buffer = finalBuffer;
						int readBuffers = finalReadBuffers;

						@Override
						public void handleEvent(StreamSourceChannel channel) {
							try {
								do {
									int r;
									ByteBuffer b = buffer.getBuffer();
									r = channel.read(b);
									if (r == -1) { // TODO: listener read
										if (b.position() == 0) {
											buffer.close();
										} else {
											b.flip();
											bufferedData[readBuffers] = buffer;
										}
										Connectors.ungetRequestBytes(exchange, bufferedData);
										Connectors.resetRequestChannel(exchange);
										Connectors.executeRootHandler(next, exchange);
										channel.getReadSetter().set(null);
										return;
									} else if (r == 0) {
										return;
									} else if (!b.hasRemaining()) {
										b.flip();
										bufferedData[readBuffers++] = buffer;
										if (readBuffers == maxBuffers) {
											Connectors.ungetRequestBytes(exchange, bufferedData);
											Connectors.resetRequestChannel(exchange);
											Connectors.executeRootHandler(next, exchange);
											channel.getReadSetter().set(null);
											return;
										}
										buffer = exchange.getConnection().getByteBufferPool().allocate();
									}
								} while (true);
							} catch (IOException e) {
								UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
								for (int i = 0; i < bufferedData.length; ++i) {
									IoUtils.safeClose(bufferedData[i]);
								}
								exchange.endExchange();
							}
						}
					});
					channel.resumeReads();
				} else if (!b.hasRemaining()) {
					b.flip();
					bufferedData[readBuffers++] = buffer;
					if (readBuffers == maxBuffers) {
						LwiHandler.getLwiCall(exchange).setPartial();
						break;
					}
					buffer = exchange.getConnection().getByteBufferPool().allocate(); 
				}
			} while (true);
			Connectors.ungetRequestBytes(exchange, bufferedData);
			Connectors.resetRequestChannel(exchange);
		}
	}

}
