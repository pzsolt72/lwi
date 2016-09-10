package hu.telekom.lwi.plugin.log;

import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.Receiver;
import io.undertow.io.Receiver.ErrorCallback;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.Connectors;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ExchangeCompletionListener.NextListener;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.ConduitFactory;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import hu.telekom.lwi.plugin.LwiAbstractHandler;
import hu.telekom.lwi.plugin.LwiContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

@Deprecated
public class LogRequestHandler extends LwiAbstractHandler {

	private static final int MAXBUFFER = 100000;

	private int partCounter = 0;

	private StringBuffer sb = null;
	
    private final int maxBuffers;
    
	private final Logger log = Logger.getLogger(this.getClass());
	private final Logger messageLog = Logger.getLogger("hu.telekom.lwi.message.log");
	
	
    public LogRequestHandler( LwiContext lwiContext, HttpHandler next) {
    	
    	super(lwiContext, next);
    	
        this.maxBuffers = 1000000;
    }

	    
    public void handleRequest(final HttpServerExchange exchange) throws Exception {    	
    	
    	log.debug("LogRequestHandler > handle start...");
    	
		sb = new StringBuffer();

    	exchange.addRequestWrapper(new ConduitWrapper<StreamSourceConduit>() {
			
			@Override
			public StreamSourceConduit wrap(ConduitFactory<StreamSourceConduit> factory, HttpServerExchange exch) {
				
				return new LwiRequestConduit(factory.create());
			}
		});

		exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
			@Override
			public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
				
				if ( partCounter > 0 ) {
					partCounter++;
					messageLog.info("Partial request part: " + partCounter + " last > " + sb.toString());					
				} else {
					messageLog.info("REQUEST > " + sb.toString());					
				}

				sb.setLength(0);
				nextListener.proceed();

			}
		});

    	
    	
    	/*
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
                                for(int i = 0; i < bufferedData.length; ++i) {
                                    IoUtils.safeClose(bufferedData[i]);
                                }
                                exchange.endExchange();
                            }
                        }
                    });
                    channel.resumeReads();
                    return;
                } else if (!b.hasRemaining()) {
                    b.flip();
                    bufferedData[readBuffers++] = buffer;
                    if(readBuffers == maxBuffers) {
                        break;
                    }
                    buffer = exchange.getConnection().getByteBufferPool().allocate();
                }
            } while (true);
            
            StringBuffer sb = new StringBuffer();
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
            
            messageLog.info("LOG  > " + sb.toString() );
            
            lwiContext.setRequestContentString(sb.toString());
            
                       
            Connectors.ungetRequestBytes(exchange, bufferedData);
            Connectors.resetRequestChannel(exchange);
            
            
            
        }
        
        log.debug("LogRequestHandler > handle complete");
        */
    	
        next.handleRequest(exchange);
    }
    
    
    
    private class LwiRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

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
				sb.append(new String(d));

			}			
			
			
			if (sb.length() > MAXBUFFER) {
				partCounter++;
				messageLog.info("Partial request part: " + partCounter + " > " + sb.toString());
				sb.setLength(0);
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

}