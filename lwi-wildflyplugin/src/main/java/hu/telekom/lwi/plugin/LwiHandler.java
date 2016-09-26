package hu.telekom.lwi.plugin;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.data.LwiCall;
import hu.telekom.lwi.plugin.data.LwiRequestData;
import hu.telekom.lwi.plugin.limit.LwiRequestLimitExceededHandler;
import hu.telekom.lwi.plugin.log.LwiLogHandler;
import hu.telekom.lwi.plugin.log.LwiLogLevel;
import hu.telekom.lwi.plugin.proxy.LwiProxyHandler;
import hu.telekom.lwi.plugin.security.LwiSecurityHandler;
import hu.telekom.lwi.plugin.validation.LwiValidationHandler;
import hu.telekom.lwi.plugin.validation.LwiValidationType;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

/**
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class LwiHandler implements HttpHandler {

	private static final String LWI_REQUEST_ID_KEY = "X-Lwi-RequestId";
	private static final AttachmentKey<LwiCall> LWI_CALL_DATA = AttachmentKey.create(LwiCall.class);
	private static final AttachmentKey<LwiRequestData> LWI_REQUEST_DATA = AttachmentKey.create(LwiRequestData.class);

	private static final int LWI_ERROR_CODE = 500;

	private static final Logger log = Logger.getLogger(LwiHandler.class);	

	private RequestLimit requestLimitHandler = null;

	private boolean parametersAreValidated = false;
	

	private HttpHandler next;

	private Integer maxRequests;
	private Integer queueSize;
	private LwiLogLevel logLevel;
	private LwiValidationType validationType;
	private Boolean skipAuthentication = false;
	private String backEndServiceUrl;
	private Integer backEndConnections;
	private Integer requestTimeout;


	/**
	 * 
	 * @param next
	 */
	public LwiHandler(HttpHandler next) {
		
		log.debug("Init LwiHandler");

		this.next = next;
	}

	public LwiHandler(HttpHandler next, Integer maxRequests, Integer queueSize, LwiLogLevel logLevel, LwiValidationType validationType,
			Boolean skipAuthentication) {

		log.debug("Init LwiHandler");

		this.next = next;
		this.maxRequests = maxRequests;
		this.queueSize = queueSize;
		this.skipAuthentication = skipAuthentication;
		this.logLevel = logLevel;
		this.validationType = validationType;

		validateHandlerParameters();

	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		try {
			
			String lwiRequestId = generateLwiReqId();

			exchange.getRequestHeaders().add(new HttpString(LWI_REQUEST_ID_KEY), lwiRequestId);

			log.info(String.format(
					"[%s] LwiHandler - start handle %s maxRequestss: %s, queueSize: %s, logLevel: %s,  validationType: %s, skipAuth: %s, requestTimeout: %s" ,
					lwiRequestId, exchange.getRequestURL(), maxRequests, queueSize, logLevel, validationType,
					skipAuthentication, requestTimeout));

			if (!parametersAreValidated) {
				validateHandlerParameters();
			}

			if (requestLimitHandler == null) {
				requestLimitHandler = new RequestLimit(maxRequests, queueSize);
				requestLimitHandler.setFailureHandler(new LwiRequestLimitExceededHandler(maxRequests, queueSize));
			}
			
			boolean requestBuffering = validationType != LwiValidationType.NO;

			HttpHandler nnnext = next;

			// proxy
			LwiProxyHandler proxyhandler = new LwiProxyHandler(backEndServiceUrl, backEndConnections, requestTimeout);
			nnnext = proxyhandler;

			LwiValidationHandler validationHandler = new LwiValidationHandler(nnnext, validationType, backEndServiceUrl + "?WSDL");
			nnnext = validationHandler;

			LwiLogHandler lwiLogHandler = new LwiLogHandler(nnnext, logLevel);
			nnnext = lwiLogHandler;

			LwiRequestBufferingHandler lwiMessageHandler = new LwiRequestBufferingHandler(nnnext, 2, requestBuffering);
			nnnext = lwiMessageHandler;
			
			LwiSecurityHandler securityHandler = new LwiSecurityHandler(nnnext);			
			nnnext = securityHandler;
			
			requestLimitHandler.handleRequest(exchange, nnnext);
			
		} catch (Throwable e) {
			log.error(String.format("[%s] LwiHandler->error : " + e.getMessage(), LwiHandler.getLwiRequestId(exchange)),e);
			LwiHandler.handleExcetption(exchange, e);
		}

	}

	public static void handleExcetption(HttpServerExchange exchange, Throwable e) {
		String reqId = LwiHandler.getLwiRequestId(exchange);
		LwiCall lwiCall = LwiHandler.getLwiCall(exchange);
		
		exchange.setResponseCode(LWI_ERROR_CODE);
		exchange.getResponseSender().send(createSoapFault(lwiCall.getProvider(), lwiCall.getOperation(), reqId, "LWI internal error : " + e.getMessage()));
	}

	public static String getLwiRequestId(HttpServerExchange exchange) {
		try {
			return exchange.getRequestHeaders().get(LWI_REQUEST_ID_KEY).getFirst();
		} catch (Exception e) {
			return "N/A";
		}
	}
	
	public static LwiCall getLwiCall(HttpServerExchange exchange) {
		LwiCall lwiCall = exchange.getAttachment(LWI_CALL_DATA);
		if (lwiCall == null) {
			lwiCall = new LwiCall(exchange, getLwiRequestId(exchange));
			exchange.putAttachment(LWI_CALL_DATA, lwiCall);
		}
		return lwiCall;
	}

	public static LwiRequestData getLwiRequestData(HttpServerExchange exchange) {
		LwiRequestData lwiRequestData = exchange.getAttachment(LWI_REQUEST_DATA);
		if (lwiRequestData == null) {
			lwiRequestData = new LwiRequestData(exchange);
			exchange.putAttachment(LWI_REQUEST_DATA, lwiRequestData);
		}
		return lwiRequestData;
	}

	public static String getLwiRequest(HttpServerExchange exchange) throws UnsupportedEncodingException {
		return Connectors.getRequest(exchange, "UTF-8");
	}
	
	public Integer getMaxRequests() {
		return maxRequests;
	}

	public void setMaxRequests(Integer maxRequests) {
		this.maxRequests = maxRequests;
	}

	public Integer getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(Integer queueSize) {
		this.queueSize = queueSize;
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = LwiLogLevel.valueOf(logLevel);
	}

	public void setValidationType(String validationType) {
		this.validationType = LwiValidationType.valueOf(validationType);
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

	public Integer getRequestTimeout() {
		return requestTimeout;
	}

	public void setRequestTimeout(Integer requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	private void validateHandlerParameters() {
		if (maxRequests == null) {
			throw new RuntimeException("Set the LwiHandler.maxRequests value properly! e.g.:  20");
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

		if (requestTimeout == null) {
			throw new RuntimeException("Set the LwiHandler.requestTimeout value properly! e.g.:  10000");
		}
		
		if (logLevel == null) {
			throw new RuntimeException("Set the LwiHandler.logLevel value logLevel! e.g.:  NONE, MIN, CTX, FULL");
		}

		if (validationType == null) {
			throw new RuntimeException("Set the LwiHandler.validationType value logLevel! e.g.: NO, CTX, MSG");
		}

		parametersAreValidated = true;
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

    private static String createSoapFault(String provider, String service, String lwiId, String msg) {
        String template = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <SOAP-ENV:Body>\n" +
                "      <SOAP-ENV:Fault>\n" +
                "         <faultcode>SOAP-ENV:Client</faultcode>\n" +
                "         <faultstring>\n" +
                "          %s\n" +
                "          %s\n" +
                "          LWI internal request id: %s\n" +                
                "          %s\n" +                                                
                "         </faultstring>\n" +
                "      </SOAP-ENV:Fault>\n" +
                "   </SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
        return String.format(template, provider, service, lwiId, msg);
    }

	
	
	
	/*
	 * EZ MÉG NEM MŰKÖDIK A CONFIGBÓL!!!
	 */

	public static final class Wrapper implements HandlerWrapper {

		private Integer maxRequests;
		private Integer queueSize;
		private String logLevel;
		private String validationType;
		private Boolean skipAuthentication;

		public Wrapper(Integer maxRequests, Integer queueSize, String logLevel, String validationType, Boolean skipAuthentication) {
			super();
			this.maxRequests = maxRequests;
			this.queueSize = queueSize;
			this.logLevel = logLevel;
			this.validationType = validationType;
			this.skipAuthentication = skipAuthentication;
		}

		@Override
		public HttpHandler wrap(HttpHandler exchange) {
			return new LwiHandler(exchange, maxRequests, queueSize, LwiLogLevel.valueOf(logLevel), LwiValidationType.valueOf(validationType), skipAuthentication);
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
			ret.put("maxRequests", Integer.class);
			ret.put("queueSize", Integer.class);
			ret.put("logLevel", String.class);
			ret.put("validationType", String.class);
			ret.put("skipAuthentication", String.class);

			return ret;
		}

		@Override
		public Set<String> requiredParameters() {
			return new HashSet<>(Arrays.asList("maxRequests", "queueSize", "logLevel", "validationType"));
		}

		@Override
		public String defaultParameter() {
			return null;
		}

		@Override
		public HandlerWrapper build(Map<String, Object> config) {
			return new Wrapper((Integer) config.get("maxRequests"), (Integer) config.get("queueSize"),
					(String) config.get("logLevel"), (String) config.get("validationType"),
					(Boolean) config.get("skipAuthentication"));
		}
	}

}
