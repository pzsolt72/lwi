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
			bufferRequest(lwiRequestId, exchange);
			
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
	public void bufferRequest(String lwiRequestId, final HttpServerExchange exchange) throws Exception {
		if (!exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
			final StreamSourceChannel channel = exchange.getRequestChannel();
			int readBuffers = 0;
			final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
			PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();

			log.info(String.format("[%s] LwiRequestBufferingHandler - handleRead", lwiRequestId));
			do {
				int r;
				ByteBuffer b = buffer.getBuffer();
				r = channel.read(b);
				if (r == -1) { // TODO: listener read
					log.info(String.format("[%s] LwiRequestBufferingHandler - read finished (buffered data: %d)", lwiRequestId, b.position()));
					if (b.position() == 0) {
						log.info(String.format("[%s] LwiRequestBufferingHandler - read empty", lwiRequestId));
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
										log.info(String.format("[%s] LwiRequestBufferingHandler - (event) read finished (bytes: %d, buffer: %d)", lwiRequestId, r, b.position()));
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
										log.info(String.format("[%s] LwiRequestBufferingHandler - (event) read empty", lwiRequestId));
										return;
									} else if (!b.hasRemaining()) {
										log.info(String.format("[%s] LwiRequestBufferingHandler - (event) buffer full (bytes: %d, buffer: %d)", lwiRequestId, r, b.position()));
										if (readIntoBuffer(lwiRequestId, b, bufferedData, buffer, readBuffers, exchange, true)) {
											readBuffers++;
										} else {
											channel.getReadSetter().set(null);
											return;
										}
									} else {
										log.info(String.format("[%s] LwiRequestBufferingHandler - (event) read (bytes: %d, buffer: %d)", lwiRequestId, r, b.position()));
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
					log.info(String.format("[%s] LwiRequestBufferingHandler - buffer full", lwiRequestId));
					if (readIntoBuffer(lwiRequestId, b, bufferedData, buffer, readBuffers, exchange, false)) {
						readBuffers++;
					} else {
						break;
					}
				} else {
					log.info(String.format("[%s] LwiRequestBufferingHandler - read (bytes: %d, buffer: %d)", lwiRequestId, r, b.position()));
				}
			} while (true);
			Connectors.ungetRequestBytes(exchange, bufferedData);
			Connectors.resetRequestChannel(exchange);
		}
	}
	

	
	private void handleRead(String lwiRequestId, final StreamSourceChannel channel, final PooledByteBuffer[] bufferedData, PooledByteBuffer buffer, HttpServerExchange exchange, boolean fromEvent) {
		log.info(String.format("[%s] LwiRequestBufferingHandler - handleRead(fromEvent: %s)", lwiRequestId, Boolean.toString(fromEvent)));
		try {
			int readBuffers = 0;
			do {
				int r;
				ByteBuffer b = buffer.getBuffer();
				r = channel.read(b);
				if (r == -1) {
					log.info(String.format("[%s] LwiRequestBufferingHandler - read finished (buffered data: %d)", lwiRequestId, b.position()));
					if (b.position() == 0) {
						buffer.close();
					} else {
						b.flip();
						bufferedData[readBuffers] = buffer;
					}
					
					if (fromEvent) {
						suspendRead(lwiRequestId, bufferedData, exchange);
						channel.getReadSetter().set(null);
					}
					return;
				} else if (r == 0) {
					log.info(String.format("[%s] LwiRequestBufferingHandler - read empty", lwiRequestId));
					if (!fromEvent) {
						channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {

							@Override
							public void handleEvent(StreamSourceChannel channel) {
								handleRead(lwiRequestId, channel, bufferedData, buffer, exchange, true);
							}
						});
					} else {
						return;
					}
				} else if (!b.hasRemaining()) {
					log.info(String.format("[%s] LwiRequestBufferingHandler - buffer full", lwiRequestId));
					if (readIntoBuffer(lwiRequestId, b, bufferedData, buffer, readBuffers, exchange, true)) {
						readBuffers++;
					} else {
						if (fromEvent) {
							channel.getReadSetter().set(null);
						}
						return;
					}
				} else {
					log.info(String.format("[%s] LwiRequestBufferingHandler - read (bytes: %d, buffer: %d)", lwiRequestId, r, b.position()));
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
	
	private boolean readIntoBuffer(String lwiRequestId, ByteBuffer b, final PooledByteBuffer[] bufferedData, PooledByteBuffer buffer, int readBuffers, HttpServerExchange exchange, boolean fromEvent) {
		b.flip();
		bufferedData[readBuffers++] = buffer;
		if (readBuffers == maxBuffers) {
			LwiHandler.getLwiCall(exchange).setPartial();
			if (fromEvent) {
				suspendRead(lwiRequestId, bufferedData, exchange);
			}
			return false;
		}
		buffer = exchange.getConnection().getByteBufferPool().allocate(); 
		return true;
	}

	
	private void suspendRead(String lwiRequestId, final PooledByteBuffer[] bufferedData, HttpServerExchange exchange) {
		log.info(String.format("[%s] LwiRequestBufferingHandler - suspend read", lwiRequestId));
		Connectors.ungetRequestBytes(exchange, bufferedData);
		Connectors.resetRequestChannel(exchange);
		// perhaps duplicate message for exchange here
		Connectors.executeRootHandler(next, exchange);
	}
	
}
