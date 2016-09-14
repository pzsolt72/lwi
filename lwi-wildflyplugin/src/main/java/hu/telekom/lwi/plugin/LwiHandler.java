package hu.telekom.lwi.plugin;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import hu.telekom.lwi.plugin.validation.ValidationHandler;
import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.limit.RequestLimitHandler;
import hu.telekom.lwi.plugin.log.LwiLogHandler;
import hu.telekom.lwi.plugin.log.LwiLogLevel;
import hu.telekom.lwi.plugin.security.LwiSecurityHandler;
import io.undertow.io.Sender;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider;
import io.undertow.util.HttpString;

/**
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class LwiHandler implements HttpHandler {

	
	private static final int PROVIDER_POS_ON_URI = 2;
    private static final int SERVICE_POS_ON_URI = 3;
			
	private static String MUTEX = "MUTEX";
	private static Map<String, LoadBalancingProxyClient> proxyMap = new HashMap<>();

	private static final String LWI_REQUEST_ID_KEY = "X-Lwi-RequestId";

	private static final String REQUEST_LIMIT_ERROR_MSG = "Request limit exceeded! max request allowed: %s, queue size: %s";
	private static final int REQUEST_LIMIT_ERROR_CODE = 509;

	private RequestLimit requestLimitHandler = null;

	private boolean parametersAreValidated = false;
	
	private final Logger log = Logger.getLogger(LwiHandler.class);	

	private HttpHandler next;

	private Integer maxRequest;
	private Integer queueSize;
	private String logLevel;
	private String validationType;
	private Boolean skipAuthentication = false;
	private String backEndServiceUrl;
	private Integer backEndConnections;

	/*
	 * public enum LogLevel { NONE, MIN, CTX, FULL };
	 */

	public enum ValidationType {
		NO, CTX, MSG
	}

	/**
	 * 
	 * @param next
	 */

	public LwiHandler(HttpHandler next) {
		
		log.debug("Init LwiHandler");

		this.next = next;
	}

	public LwiHandler(HttpHandler next, Integer maxRequest, Integer queueSize, String logLevel, String validationType,
			Boolean skipAuthentication) {

		log.debug("Init LwiHandler");

		this.next = next;
		this.maxRequest = maxRequest;
		this.queueSize = queueSize;
		this.logLevel = logLevel;
		this.validationType = validationType;
		this.skipAuthentication = skipAuthentication;

		validateHandlerParameters();

	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		String lwiRequestId = generateLwiReqId();

		exchange.getRequestHeaders().add(new HttpString(LWI_REQUEST_ID_KEY), lwiRequestId);

		if (!parametersAreValidated)
			validateHandlerParameters();

		log.info(String.format(
				"[%s] LwiHandler->handle %s maxRequests: %s, queueSize: %s, logLevel: %s,  validationType: %s, skipAuth: %s",
				lwiRequestId, exchange.getRequestURL(), maxRequest, queueSize, logLevel, validationType,
				skipAuthentication));



		if (requestLimitHandler == null) {
			requestLimitHandler = new RequestLimit(maxRequest, queueSize);
			requestLimitHandler.setFailureHandler(new LwiRequestLimitExceededHandler());
		}

		HttpHandler nnnext = next;

		// proxy
		LoadBalancingProxyClient lbpc = getProxyClient();

		ProxyHandler proxyhandler = new ProxyHandler(lbpc, 30000, ResponseCodeHandler.HANDLE_404);
		nnnext = proxyhandler;

		if ( true ) {
			ValidationHandler validationHandler = new ValidationHandler(nnnext);
			validationHandler.setValidationType(validationType);
			validationHandler.setWsdlLocation(backEndServiceUrl + "?WSDL");
			nnnext = validationHandler;
		}

		if ( true ) {
			LwiLogHandler lwiLogHandler = new LwiLogHandler(nnnext);
			lwiLogHandler.setLogLevel(logLevel);
			nnnext = lwiLogHandler;
		}

		// can be skipped!!
		if ( !skipAuthentication ) {			
			LwiSecurityHandler securityHandler = new LwiSecurityHandler(nnnext);			
			nnnext = securityHandler;
		}

		requestLimitHandler.handleRequest(exchange, nnnext);

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

	public static String getLwiRequestId(HttpServerExchange exchange) {
		try {
			return exchange.getRequestHeaders().get(LWI_REQUEST_ID_KEY).getFirst();
		} catch (Exception e) {
			return "N/A";
		}
	}
	
	public static String getProvider(HttpServerExchange exchange) {
		String retval = "NOTAVAILABLE";
		
		String[] requestPath = exchange.getRequestPath().split("/");
		
		try {
			retval = requestPath[PROVIDER_POS_ON_URI];
		} catch (Exception e) {		}
		
		return retval;
	}
	
	public static String getCalledService(HttpServerExchange exchange) {
		String retval = "NOTAVAILABLE";
		
		String[] requestPath = exchange.getRequestPath().split("/");
		
		try {
			retval = requestPath[SERVICE_POS_ON_URI];
		} catch (Exception e) {		}
		
		return retval;
	}	

	public HttpHandler getNext() {
		return next;
	}

	public void setNext(HttpHandler next) {
		this.next = next;
	}

	public Integer getMaxRequest() {
		return maxRequest;
	}

	public void setMaxRequest(Integer maxRequest) {
		this.maxRequest = maxRequest;
	}

	public Integer getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(Integer queueSize) {
		this.queueSize = queueSize;
	}

	public String getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
	}

	public String getValidationType() {
		return validationType;
	}

	public void setValidationType(String validationType) {
		this.validationType = validationType;
	}
	
	

	public Boolean getSkipAuthentication() {
		return skipAuthentication;
	}

	public void setSkipAuthentication(Boolean skipAuthentication) {
		this.skipAuthentication = skipAuthentication;
	}

	public String getBackEndServiceUrl() {
		return backEndServiceUrl;
	}

	public void setBackEndServiceUrl(String backEndServiceUrl) {
		this.backEndServiceUrl = backEndServiceUrl;
	}

	public Integer getBackEndConnections() {
		return backEndConnections;
	}

	public void setBackEndConnections(Integer backEndConnections) {
		this.backEndConnections = backEndConnections;
	}

	private void validateHandlerParameters() {
		if (maxRequest == null) {
			throw new RuntimeException("Set the LwiHandler.maxRequest value properly! e.g.:  20");
		}

		if (queueSize == null) {
			throw new RuntimeException("Set the LwiHandler.queueSize value properly! e.g.:  20");
		}

		if (backEndConnections == null) {
			throw new RuntimeException("Set the LwiHandler.backEndConnections value properly! e.g.:  10");
		}
		
		if (backEndServiceUrl == null) {
			throw new RuntimeException("Set the LwiHandler.backEndServiceUrl value properly! e.g.:  http://localhost:8091/lwi/cnr/getMsisdn");
		}
		
		
		boolean validLogLevel = false;
		for (int i = 0; i < LwiLogLevel.values().length; i++) {
			LwiLogLevel ll = LwiLogLevel.values()[i];
			if (ll.toString().equals(logLevel)) {
				validLogLevel = true;
				break;
			}
		}
		if (!validLogLevel) {
			throw new RuntimeException("Set the LwiHandler.logLevel value logLevel! e.g.:  NONE, MIN, CTX, FULL");
		}

		boolean validvalidationType = false;
		for (int i = 0; i < ValidationType.values().length; i++) {
			ValidationType vt = ValidationType.values()[i];
			if (vt.toString().equals(validationType)) {
				validvalidationType = true;
				break;
			}
		}
		if (!validvalidationType) {
			throw new RuntimeException("Set the LwiHandler.validationType value logLevel! e.g.: NO, CTX, MSG");
		}

		parametersAreValidated = true;
	}

	private class LwiRequestLimitExceededHandler implements HttpHandler {

		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {

			log.warn(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequest, queueSize) + " " + exchange.getRequestURI());

			exchange.setStatusCode(REQUEST_LIMIT_ERROR_CODE);
			Sender sender = exchange.getResponseSender();
			sender.send(String.format(REQUEST_LIMIT_ERROR_MSG, maxRequest, queueSize));

		}

	}

	private String generateLwiReqId() {
		char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
		StringBuilder sb = new StringBuilder("lwiId-");
		Random random = new Random();
		for (int i = 0; i < 3; i++) {
			char c = chars[random.nextInt(chars.length)];
			sb.append(c);
		}
		sb.append(System.currentTimeMillis());
		String output = sb.toString();

		return output;
	}

	/*
	 * EZ MÉG NEM MŰKÖDIK A CONFIGBÓL!!!
	 */

	public static final class Wrapper implements HandlerWrapper {

		private Integer maxRequest;
		private Integer queueSize;
		private String logLevel;
		private String validationType;
		private Boolean skipAuthentication;

		public Wrapper(Integer maxRequest, Integer queueSize, String logLevel, String validationType,
				Boolean skipAuthentication) {
			super();
			this.maxRequest = maxRequest;
			this.queueSize = queueSize;
			this.logLevel = logLevel;
			this.validationType = validationType;
			this.skipAuthentication = skipAuthentication;
		}

		@Override
		public HttpHandler wrap(HttpHandler exchange) {
			return new LwiHandler(exchange, maxRequest, queueSize, logLevel, validationType, skipAuthentication);
		}

	}

	public static final class Builder implements HandlerBuilder {

		@Override
		public String name() {
			return "lwi";
		}

		@Override
		public Map<String, Class<?>> parameters() {
			Map<String, Class<?>> ret = new HashMap<>();
			ret.put("maxRequest", Integer.class);
			ret.put("queueSize", Integer.class);
			ret.put("logLevel", String.class);
			ret.put("validationType", String.class);
			ret.put("skipAuthentication", String.class);

			return ret;
		}

		@Override
		public Set<String> requiredParameters() {
			return new HashSet<>(Arrays.asList("maxRequest", "queueSize", "logLevel", "validationType"));
		}

		@Override
		public String defaultParameter() {
			return null;
		}

		@Override
		public HandlerWrapper build(Map<String, Object> config) {
			return new Wrapper((Integer) config.get("maxRequest"), (Integer) config.get("queueSize"),
					(String) config.get("logLevel"), (String) config.get("validationType"),
					(Boolean) config.get("skipAuthentication"));
		}
	}

}
