package hu.telekom.lwi.plugin.hello;

import org.jboss.logging.Logger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;


/**
 * 
 * Minta handler
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class HelloPlugin implements HttpHandler {
	
	private HttpHandler next = null;
	
	private Logger logger = null;
	
	public HelloPlugin(HttpHandler next) {

		
		this.next = next;
		this.logger = Logger.getLogger(getClass());
		
	}
	

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		
		//System.out.println("Hello " + exchange.getRequestURI() + " request!");
		logger.info("Hello " + exchange.getRequestURI() + " request!");
		
		// Perform the exchange
        next.handleRequest(exchange);
	}

}
