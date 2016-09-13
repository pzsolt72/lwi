package hu.telekom.lwi.plugin.hello;

import org.jboss.logging.Logger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.QueryParameterUtils;


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

		logger.info("Hello " + exchange.getRequestURI() + " request!");
		
		try {
			long delay = Long.valueOf(exchange.getQueryParameters().get("delay").peek());
			logger.info("Delayed for "+delay+"ms");
			Thread.sleep(delay);
		} catch (Exception e) {}
		
		// Perform the exchange
        next.handleRequest(exchange);
	}

}
