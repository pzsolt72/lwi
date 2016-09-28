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
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

/**
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class LwiHandler2 implements HttpHandler {

	private static final String LWI_REQUEST_ID_KEY = "X-Lwi-RequestId";
	private static final AttachmentKey<LwiCall> LWI_CALL_DATA = AttachmentKey.create(LwiCall.class);
	private static final AttachmentKey<LwiRequestData> LWI_REQUEST_DATA = AttachmentKey.create(LwiRequestData.class);

	private static final int LWI_ERROR_CODE = 500;

	private static final Logger log = Logger.getLogger(LwiHandler2.class);	

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
	private Integer bufferSize=20;


	/**
	 * 
	 * @param next
	 */
	public LwiHandler2(HttpHandler next) {
		
		log.debug("Init LwiHandler");

		this.next = next;
	}


	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {

		try {			
			
			String lwiRequestId = generateLwiReqId();

			exchange.getRequestHeaders().add(new HttpString(LWI_REQUEST_ID_KEY), lwiRequestId);

			log.info(String.format(
					"[%s] LwiHandler - start handle %s maxRequestss: %s, queueSize: %s, logLevel: %s,  validationType: %s, skipAuth: %s, requestTimeout: %s, bufferSize: %s" ,
					lwiRequestId, exchange.getRequestURL(), maxRequests, queueSize, logLevel, validationType,
					skipAuthentication, requestTimeout, bufferSize));

			if (!parametersAreValidated) {
				validateHandlerParameters();
			}

			
			boolean requestBuffering = validationType != LwiValidationType.NO;

			HttpHandler nnnext = next;

			// proxy
			LwiProxyHandler proxyhandler = new LwiProxyHandler(backEndServiceUrl, backEndConnections, requestTimeout,next);
			nnnext = proxyhandler;

			// request buffer
			RequestBufferingHandler lwiMessageHandler = new RequestBufferingHandler(nnnext, bufferSize);
			nnnext = lwiMessageHandler;
				
			nnnext.handleRequest(exchange);
			
		} catch (Throwable e) {
			log.error(String.format("[%s] LwiHandler->error : " + e.getMessage(), LwiHandler2.getLwiRequestId(exchange)),e);
			LwiHandler2.handleExcetption(exchange, e);
		}

	}

	public static void handleExcetption(HttpServerExchange exchange, Throwable e) {
		String reqId = LwiHandler2.getLwiRequestId(exchange);
		LwiCall lwiCall = LwiHandler2.getLwiCall(exchange);
		
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

	public Integer getBufferSize() {
		return bufferSize;
	}

	public void setBufferSize(Integer bufferSize) {
		this.bufferSize = bufferSize;
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


}
