package hu.telekom.lwi.plugin.validation;

import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.reficio.ws.SoapValidationException;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;

import hu.telekom.lwi.plugin.LwiHandler;
import hu.telekom.lwi.plugin.data.LwiCall;
import hu.telekom.lwi.plugin.data.LwiRequestData;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiValidationHandler implements HttpHandler {

    private static final int VALIDATION_ERROR_CODE = 400;
    private static final int INTERNAL_ERROR_CODE = 500;

    private static final Logger log = Logger.getLogger(LwiValidationHandler.class);

    private HttpHandler next = null;
    private LwiValidationType validationType = LwiValidationType.MSG;
    private String wsdlLocation = null;
    private boolean forceValidation = false;
    private ConcurrentHashMap<String, Wsdl> wsdlCache = new ConcurrentHashMap<>();
    
    
    
    static {
	    //for localhost testing only
	    javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
	    new javax.net.ssl.HostnameVerifier(){

	        public boolean verify(String hostname,
	                javax.net.ssl.SSLSession sslSession) {
	            if (hostname.equals("localhost")) {
	                return true;
	            }
	            return false;
	        }
	    });
	}

    public LwiValidationHandler(HttpHandler next) {
        this.next = next;
    }

    public LwiValidationHandler(HttpHandler next, LwiValidationType validationType, String wsdlLocation, boolean forceValidation) {
        this.next = next;
        this.validationType = validationType;
        this.wsdlLocation = wsdlLocation;
        this.forceValidation = forceValidation;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
        
        log.info(String.format("[%s] LwiValidationHandler - start request validation (%s, force: %s)...", lwiRequestId, validationType.name(), Boolean.toString(forceValidation)));

        LwiCall lwiCall = LwiHandler.getLwiCall(exchange);
        try {
        	switch (validationType) {
				case MSG:
					if (!lwiCall.isPartial()) {
						validateByMsg(exchange, lwiRequestId);
					} else {
			            log.warn(String.format("[%s] LwiValidationHandler - validation cannot be done on MSG level because the request is too long - fall back to CTX level!", lwiRequestId));
					}
				case CTX:
					validateByContext(LwiHandler.getLwiRequestData(exchange), !lwiCall.isPartial() || forceValidation);
		            log.info(String.format("[%s] LwiValidationHandler - validation completed!", lwiRequestId));
					break;
				default: 
		            log.info(String.format("[%s] LwiValidationHandler - ended without validation.", lwiRequestId));
			}
            next.handleRequest(exchange);
        } catch (Exception e) {
        	if (e instanceof LwiValidationException) {
                log.warn(String.format("[%s] LwiValidationHandler - validation failed!", lwiRequestId));
                exchange.setStatusCode(VALIDATION_ERROR_CODE);
        	} else {
        		log.error(String.format("[%s] LwiValidationHandler - validation error!", lwiRequestId),e);
                exchange.setStatusCode(INTERNAL_ERROR_CODE);
        	}
            Sender sender = exchange.getResponseSender();
            sender.send(createSoapFault(validationType, e.getMessage()));
        }
    }

    private void validateByMsg(HttpServerExchange exchange, String lwiRequestId) throws Exception {
        String reqContent = LwiHandler.getLwiRequest(exchange);
        
        if (reqContent.length() == 0) {
        	throw new Exception("no msg found in lwi context");
        } else {
            if (wsdlLocation == null) {
            	throw new Exception("no wsdlLocation filter param defined");
            } else {
                
            	log.debug(String.format("[%s] LwiValidationHandler - looking up wsdl key=(%s)...", lwiRequestId, wsdlLocation));
                Wsdl wsdl = wsdlCache.get(wsdlLocation);
                
                if ( wsdl == null ) {
                	log.debug(String.format("[%s] LwiValidationHandler - parsing wsdl (%s)...", lwiRequestId, wsdlLocation));
            		wsdl = Wsdl.parse(wsdlLocation);
            		log.debug(String.format("[%s] LwiValidationHandler - caching wsdl (%s)...", lwiRequestId, wsdlLocation));
            		wsdlCache.put(wsdlLocation, wsdl);
                } else {
                	log.debug(String.format("[%s] LwiValidationHandler - cached wsdl found!", lwiRequestId));
                } 

                if (wsdl.getBindings() == null || wsdl.getBindings().size() == 0) {
                	throw new Exception("no bindings found in wsdl");
                } else {
                    String localPart = wsdl.getBindings().get(0).getLocalPart();
                    SoapBuilder builder = wsdl.binding().localPart(localPart).find();
                    SoapOperation op = builder.getOperations().get(0);
                    try {
                        builder.validateInputMessage(op, reqContent);
                    } catch (SoapValidationException e) {
                    	String error = "request is NOT VALID. Found " + e.getErrors().size() + "errors.";
                        int errCnt = 1;
                        for (AssertionError err : e.getErrors()) {
                        	error += "\n#" + (errCnt++) + ": " + err.getMessage();
                        }
                    	throw new LwiValidationException(error);
                    }
                }
            }
        }
    }

    private void validateByContext(LwiRequestData lwiRequestData, boolean forceValidation) throws Exception {
        if (lwiRequestData == null) {
        	throw new Exception("Context parse failed!");
        }

        if (lwiRequestData.parseRequestRequired() && forceValidation) {
        	throw new LwiValidationException("Attribute validation failed!");
        }
    }

    private static String createSoapFault(LwiValidationType validationType, String msg) {
        String template = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <SOAP-ENV:Body>\n" +
                "      <SOAP-ENV:Fault>\n" +
                "         <faultcode>SOAP-ENV:Client</faultcode>\n" +
                "         <faultstring>\n" +
                "          %s %s\n" +
                "          %s\n" +
                "         </faultstring>\n" +
                "      </SOAP-ENV:Fault>\n" +
                "   </SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
        return String.format(template, validationType.toString(), "validation FAILED", msg);
    }


    public void setValidationType(String validationType) {
        this.validationType = LwiValidationType.valueOf(validationType);
    }

    public void setWsdlLocation(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

}
