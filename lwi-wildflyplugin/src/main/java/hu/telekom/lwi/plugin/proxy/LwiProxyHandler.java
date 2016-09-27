package hu.telekom.lwi.plugin.proxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.xnio.ssl.XnioSsl;

import hu.telekom.lwi.plugin.Connectors;
import hu.telekom.lwi.plugin.LwiHandler;
import hu.telekom.lwi.plugin.data.LwiCall;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;

public class LwiProxyHandler implements HttpHandler, ExchangeCompletionListener {

	private static String MUTEX = "MUTEX";
	private static Map<String, LoadBalancingProxyClient> proxyMap = new HashMap<>();

	private static final Logger log = Logger.getLogger(LwiProxyHandler.class);	

	private ProxyHandler proxyHandler;
	private String backEndServiceUrl;
	private Integer backEndConnections;

	public LwiProxyHandler(String backEndServiceUrl, Integer backEndConnections, Integer requestTimeout) {
		this.backEndConnections = backEndConnections;
		this.backEndServiceUrl = backEndServiceUrl;
		this.proxyHandler = new ProxyHandler(getProxyClient(), requestTimeout, ResponseCodeHandler.HANDLE_404);
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		LwiCall lwiCall = LwiHandler.getLwiCall(exchange);
		
		log.info(String.format("[%s] LwiProxyHandler - proxy request...", lwiCall.getLwiRequestId()));
		exchange.addExchangeCompleteListener(this);

		Connectors.pushRequest(exchange);
		lwiCall.setServiceCalled();
		proxyHandler.handleRequest(exchange);
	}

	@Override
	public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
		try {
			LwiCall lwiCall = LwiHandler.getLwiCall(exchange);
			lwiCall.setServiceReturned();
			log.info(String.format("[%s] LwiProxyHandler - proxy completed!", lwiCall.getLwiRequestId()));
		} finally {
			nextListener.proceed();
		}
	}

	private LoadBalancingProxyClient getProxyClient() {

		LoadBalancingProxyClient retval = proxyMap.get(backEndServiceUrl);

		if (retval == null) {
			synchronized (MUTEX) {
				retval = new LoadBalancingProxyClient();
				try {									
					retval.addHost(new URI(backEndServiceUrl)).setConnectionsPerThread(backEndConnections);
					proxyMap.put(backEndServiceUrl, retval);
				} catch (URISyntaxException e) {
					log.fatal(e);
				}
			}

		}

		return retval;
	}

}
