package hu.telekom.lwi.plugin;

import java.io.UnsupportedEncodingException;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public class Connectors extends io.undertow.server.Connectors {

    static final AttachmentKey<PooledByteBuffer[]> BUFFERED_REQUEST_DATA = AttachmentKey.create(PooledByteBuffer[].class);
	
    /**
     * Attached buffered data to the exchange. The will generally be used to allow data to be re-read.
     *
     * @param exchange The HTTP server exchange
     * @param buffers  The buffers to attach
     */
    public static void ungetRequestBytes(final HttpServerExchange exchange, PooledByteBuffer... buffers) {
        PooledByteBuffer[] existing = exchange.getAttachment(BUFFERED_REQUEST_DATA);
        PooledByteBuffer[] newArray;
        if (existing == null) {
            newArray = new PooledByteBuffer[buffers.length];
            System.arraycopy(buffers, 0, newArray, 0, buffers.length);
        } else {
            newArray = new PooledByteBuffer[existing.length + buffers.length];
            System.arraycopy(existing, 0, newArray, 0, existing.length);
            System.arraycopy(buffers, 0, newArray, existing.length, buffers.length);
        }
        exchange.putAttachment(BUFFERED_REQUEST_DATA, newArray);
        
        // FIXME check whether it is really necessary
        io.undertow.server.Connectors.ungetRequestBytes(exchange, buffers);
        
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                PooledByteBuffer[] bufs = exchange.getAttachment(BUFFERED_REQUEST_DATA);
                if (bufs != null) {
                    for (PooledByteBuffer i : bufs) {
                        if(i != null && i.isOpen()) {
                            i.close();
                        }
                    }
                }
                nextListener.proceed();
            }
        });
    }

    public static String getRequest(HttpServerExchange exchange, String charset) throws UnsupportedEncodingException {
        PooledByteBuffer[] bufferedData = exchange.getAttachment(BUFFERED_REQUEST_DATA);
        if (bufferedData != null) {
    		StringBuilder sb = new StringBuilder();
	        for (int i = 0; i < bufferedData.length; i++) {
	        	if (bufferedData[i] == null) break;
	        	byte[] byteBuffer = new byte[bufferedData[i].getBuffer().remaining()];
	        	bufferedData[i].getBuffer().get(byteBuffer);
	        	
	        	// IMPORTANT: this is the key - to flip back buffer to starting point
	        	// 			  because ungetRequestBytes only puts back the same buffers into attachment again (in their current state)
	        	bufferedData[i].getBuffer().flip();
	        	
	        	sb.append(new String(byteBuffer, charset));
	        }
	        return sb.toString();
        }
        return null;
	}
    
    public static void pushRequest(final HttpServerExchange exchange) {
    	// FIXME uncomment when ungetRequestBytes method not adding request to original location
//        PooledByteBuffer[] bufferedData = exchange.getAttachment(BUFFERED_REQUEST_DATA);
//        exchange.removeAttachment(BUFFERED_REQUEST_DATA);
//        if (bufferedData != null) {
//        	io.undertow.server.Connectors.ungetRequestBytes(exchange, bufferedData);
//        }
    }
    
}
