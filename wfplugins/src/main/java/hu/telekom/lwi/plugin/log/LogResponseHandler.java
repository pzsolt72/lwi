package hu.telekom.lwi.plugin.log;

import io.undertow.Handlers;
import io.undertow.conduits.DebuggingStreamSinkConduit;
import io.undertow.conduits.DebuggingStreamSourceConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.cache.CachedHttpRequest;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.server.handlers.cache.ResponseCachingStreamSinkConduit;
import io.undertow.server.handlers.encoding.AllowedContentEncodings;
import io.undertow.server.protocol.http.ServerFixedLengthStreamSinkConduit;
import io.undertow.util.ConduitFactory;

import org.jboss.logging.Logger;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import static io.undertow.util.Headers.CONTENT_LENGTH;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class LogResponseHandler implements HttpHandler {

    //private final DirectBufferCache cache;
    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;
    
	private final Logger log = Logger.getLogger(this.getClass());
	private final Logger messageLog = Logger.getLogger("hu.telekom.lwi.message.log");
    
    private static final List<byte[]> data = new CopyOnWriteArrayList<>();

    public LogResponseHandler( final HttpHandler next) {

        this.next = next;
    }


    public void handleRequest(final HttpServerExchange exchange) throws Exception {

    	
        exchange.addResponseWrapper( 
        		new ConduitWrapper<StreamSinkConduit>() {
					
		
					public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
						
						//return new DebuggingStreamSinkConduit(factory.create());
						return new MyConduit(factory.create()); //new MyConduit(exchange.getResponseChannel().transferFrom(null, null, null));
					}
				});
        
        
        
        next.handleRequest(exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public LogResponseHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }
    
    
    
    public class MyConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

		protected MyConduit(StreamSinkConduit next) {
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
		            data.add(d);
		        }
		        		        
		        StringBuffer sb = new StringBuffer();
		        for (byte[] bs : data) {
					sb.append(new String(bs));
				}
		        System.out.println(sb.length());
		        messageLog.info(sb.toString());
		        //System.out.println(sb.toString());
		        
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

		    public  void dump() {

		        for (int i = 0; i < data.size(); ++i) {
		            System.out.println("Write Buffer " + i);
		            StringBuilder sb = new StringBuilder();
		            try {
		                Buffers.dump(ByteBuffer.wrap(data.get(i)), sb, 0, 20);
		            } catch (IOException e) {
		                throw new RuntimeException(e);
		            }
		            System.out.println(sb);
		            System.out.println();
		        }

		    }		
		
		
    	
    }
    
    
}