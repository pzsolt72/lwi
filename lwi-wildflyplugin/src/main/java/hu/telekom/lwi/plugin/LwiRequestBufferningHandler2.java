package hu.telekom.lwi.plugin;

import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.Connectors;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.log.LwiLogHandler;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class LwiRequestBufferningHandler2 implements HttpHandler {
	
	
	public static final AttachmentKey<AttachmentList<String>> LWI_ATTACHED_MSG = AttachmentKey.createList( String.class);
	private static final Logger log = Logger.getLogger(LwiRequestBufferningHandler2.class);

    private final HttpHandler next;
    private final int maxBuffers;
    private final boolean needBuffering;

    public LwiRequestBufferningHandler2 (HttpHandler next, int maxBuffers, boolean needBuffering) {
        this.next = next;
        this.maxBuffers = maxBuffers;
        this.needBuffering = needBuffering;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
    	
		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.info(String.format("[%s] LwiRequestBufferningHandler2 - start request reading...", lwiRequestId));


        if(needBuffering && !exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
        	
        	StringBuffer sb = new StringBuffer();
        	
        	
        	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - buffering...", lwiRequestId));
        	
        	
            final StreamSourceChannel channel = exchange.getRequestChannel();
            int readBuffers = 0;
            final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
            PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
            do {
                int r;
                ByteBuffer b = buffer.getBuffer();
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - reading... ", lwiRequestId));
                r = channel.read(b);
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - read " + b.capacity(), lwiRequestId));
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - r= " + r, lwiRequestId));
                if (r == -1) { //TODO: listener read
                    if (b.position() == 0) {
                        buffer.close();
                    } else {
                        b.flip();
                        bufferedData[readBuffers] = buffer;
                    }
                    break;
                    
                
             // ez nem kell, szerintem!!!!
                /*
                } else if(r == 99999999) {
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
                    
                    */
                    
                } else {//if (!b.hasRemaining()) {
                	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - add to buffer ", lwiRequestId));
                    b.flip();
                    bufferedData[readBuffers++] = buffer;
                                        
                    /* log the data */
    	        	byte[] bytes = new byte[buffer.getBuffer().remaining()];
    	        	buffer.getBuffer().get(bytes);
    	        	buffer.getBuffer().flip();
    	        	
                    sb.append(new String(bytes,"UTF-8"));
                    
                    if(readBuffers == maxBuffers) {
                    	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - max buffer reached	 ", lwiRequestId));
         	
                    	LwiHandler.getLwiCall(exchange).setPartial();
                        break;
                    }
                    log.debug(String.format("[%s] LwiRequestBufferningHandler2 - allocate.. ", lwiRequestId));
                    buffer = exchange.getConnection().getByteBufferPool().allocate();
                    log.debug(String.format("[%s] LwiRequestBufferningHandler2 - allocated ", lwiRequestId));
                //} else {
                	//log.debug(String.format("[%s] LwiRequestBufferningHandler2 - there is nothing to read b=" + b + " pos: " + b.position(), lwiRequestId));
                }
            } while (true);

            log.debug(String.format("[%s] LwiRequestBufferningHandler2 - push to attachment ", lwiRequestId));
            
            // put for lwi
            exchange.addToAttachmentList(LWI_ATTACHED_MSG, sb.toString());                        
            
            // ez nem optimális!!!!
			LwiRequestData requestData = LwiHandler.getLwiRequestData(exchange);
			//String request = LwiHandler.getLwiRequest(exchange);
			Stack<String> qNames = new Stack<>();
			qNames.push("ROOT");
			LwiLogAttributeUtil.getMessageAttributes(qNames, requestData, sb.toString());
            

            
            // put for undertow
            Connectors.ungetRequestBytes(exchange, bufferedData);
            Connectors.resetRequestChannel(exchange);
            
            
        }
        next.handleRequest(exchange);
    }

}
