package hu.telekom.lwi.plugin;

import java.util.Stack;

import org.jboss.logging.Logger;
import org.xnio.channels.StreamSourceChannel;

import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpContinue;


public class LwiRequestBufferningHandler implements HttpHandler {
	
	private static final Logger log = Logger.getLogger(LwiRequestBufferningHandler.class);

    private final HttpHandler next;
    private final int maxBuffers;
    private final boolean needBuffering;

    public LwiRequestBufferningHandler (HttpHandler next, int maxBuffers, boolean needBuffering) {
        this.next = next;
        this.maxBuffers = maxBuffers;
        this.needBuffering = needBuffering;
    }

    @SuppressWarnings("resource") // resources will be managed by the pool and closed by exchange complete listener
	@Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
    	
		String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
		
		log.info(String.format("[%s] LwiRequestBufferningHandler2 - start request reading...", lwiRequestId));

        if(needBuffering && !exchange.isRequestComplete() && !HttpContinue.requiresContinueResponse(exchange.getRequestHeaders())) {
        	
        	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - buffering...", lwiRequestId));
        	
            final StreamSourceChannel channel = exchange.getRequestChannel();
            int readBuffers = 0;
            final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
            PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
            do {
                int r;
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - reading... ", lwiRequestId));
                r = channel.read(buffer.getBuffer());
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - read " + buffer.getBuffer().capacity(), lwiRequestId));
                log.debug(String.format("[%s] LwiRequestBufferningHandler2 - r= " + r, lwiRequestId));
                if (r == -1) {
                    if (buffer.getBuffer().position() == 0) {
                        buffer.close();
                    } else {
                    	buffer.getBuffer().flip();
                        bufferedData[readBuffers++] = buffer;
                    }
                    break;
                } else if (!buffer.getBuffer().hasRemaining()) {
                	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - store against buffer array ", lwiRequestId));
                	buffer.getBuffer().flip();
                    bufferedData[readBuffers++] = buffer;
                                        
                    if(readBuffers == maxBuffers) {
                    	log.debug(String.format("[%s] LwiRequestBufferningHandler2 - max buffer reached	 ", lwiRequestId));
         	
                    	LwiHandler.getLwiCall(exchange).setPartial();
                        break;
                    }
                    log.debug(String.format("[%s] LwiRequestBufferningHandler2 - allocate.. ", lwiRequestId));
                    buffer = exchange.getConnection().getByteBufferPool().allocate();
                    log.debug(String.format("[%s] LwiRequestBufferningHandler2 - allocated ", lwiRequestId));
                }
            } while (true);

            log.debug(String.format("[%s] LwiRequestBufferningHandler2 - push to attachment ", lwiRequestId));

            Connectors.ungetRequestBytes(exchange, bufferedData);
            Connectors.resetRequestChannel(exchange);
        }

        // ez nem optimális!!!!
		LwiRequestData requestData = LwiHandler.getLwiRequestData(exchange);
		//String request = LwiHandler.getLwiRequest(exchange);
		Stack<String> qNames = new Stack<>();
		qNames.push("ROOT");
		LwiLogAttributeUtil.getMessageAttributes(qNames, requestData, LwiHandler.getLwiRequest(exchange));

		next.handleRequest(exchange);
    }

}
