package hu.telekom.lwi.plugin.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpContinue;

public abstract class RereadableRequestBufferingUtil {

    public static String handleRequest(final HttpServerExchange exchange, final int maxBuffers, final HttpHandler next) throws Exception {
        StringBuilder sb = new StringBuilder();
        if(!exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
            final StreamSourceChannel channel = exchange.getRequestChannel();
            int readBuffers = 0;
            final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
            PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
            do {
                int r;
                ByteBuffer b = buffer.getBuffer();
                r = channel.read(b);
                if (r == -1) { //TODO: listener read
                    if (b.position() == 0) {
                        buffer.close();
                    } else {
                        b.flip();
                        bufferedData[readBuffers] = buffer;
                    }
                    break;
                } else if(r == 0) {
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
                                    if (r == -1) { //TODO: listener read
                                        if (b.position() == 0) {
                                            buffer.close();
                                        } else {
                                            b.flip();
                                            bufferedData[readBuffers] = buffer;
                                        }
                                        Connectors.ungetRequestBytes(exchange, bufferedData);
                                        Connectors.resetRequestChannel(exchange);
                                        // TODO check whether this is really unnecessary: Connectors.executeRootHandler(next, exchange);
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
                                            // TODO check whether this is really unnecessary: Connectors.executeRootHandler(next, exchange);
                                            channel.getReadSetter().set(null);
                                            return;
                                        }
                                        buffer = exchange.getConnection().getByteBufferPool().allocate();
                                    }
                                } while (true);
                            } catch (IOException e) {
                                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                                for(int i = 0; i < bufferedData.length; ++i) {
                                    IoUtils.safeClose(bufferedData[i]);
                                }
                                exchange.endExchange();
                            }
                        }
                    });
                    channel.resumeReads();
                    return null;
                } else if (!b.hasRemaining()) {
                    b.flip();
                    bufferedData[readBuffers++] = buffer;
                    if(readBuffers == maxBuffers) {
                        break;
                    }
                    buffer = exchange.getConnection().getByteBufferPool().allocate();
                }
            } while (true);
            
            String charset = exchange.getRequestCharset() != null ? exchange.getRequestCharset() : "UTF-8";
            for (int i = 0; i < bufferedData.length; i++) {
            	if (bufferedData[i] == null) break;
            	byte[] byteBuffer = new byte[bufferedData[i].getBuffer().remaining()];
            	bufferedData[i].getBuffer().get(byteBuffer);
            	
            	// IMPORTANT: this is the key - to flip back buffer to starting point
            	// 			  because ungetRequestBytes only puts back the same buffers into attachment again (in their current state)
            	bufferedData[i].getBuffer().flip();
            	
           		sb.append(new String(byteBuffer, charset));
            }
            
            Connectors.ungetRequestBytes(exchange, bufferedData);
            Connectors.resetRequestChannel(exchange);
        }
        return sb.toString();
    }

}
