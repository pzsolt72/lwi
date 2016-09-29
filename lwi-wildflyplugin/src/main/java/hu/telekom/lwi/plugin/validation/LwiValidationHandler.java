package hu.telekom.lwi.plugin.validation;

import java.util.ArrayList;
import java.util.List;

import org.apache.xmlbeans.XmlError;
import org.jboss.logging.Logger;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.WsdlInterfaceFactory;
import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlProjectFactory;
import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.submit.WsdlMessageExchange;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlContext;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlImporter;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlLoader;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlValidator;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlResponseMessageExchange;
import com.eviware.soapui.model.iface.Operation;
import com.eviware.soapui.settings.WsdlSettings;
import com.eviware.soapui.support.editor.xml.support.ValidationError;

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

    public LwiValidationHandler(HttpHandler next) {
        this.next = next;
    }

    public LwiValidationHandler(HttpHandler next, LwiValidationType validationType, String wsdlLocation) {
        this.next = next;
        this.validationType = validationType;
        this.wsdlLocation = wsdlLocation;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String lwiRequestId = LwiHandler.getLwiRequestId(exchange);
        
        log.info(String.format("[%s] LwiValidationHandler - start request validation (%s)...", lwiRequestId, validationType.name()));

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
					validateByContext(LwiHandler.getLwiRequestData(exchange), !lwiCall.isPartial());
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
        		log.error(String.format("[%s] LwiValidationHandler - validation error!", lwiRequestId));
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
                log.info(String.format("[%s] LwiValidationHandler - parsing wsdl (%s)...", lwiRequestId, wsdlLocation));

//                List<XmlError> xmlErrors = new ArrayList<>();
                SoapUI.getSettings().setBoolean(WsdlSettings.STRICT_SCHEMA_TYPES, false);
                WsdlContext wsdlcontext = new WsdlContext(wsdlLocation);
//                
//                validator.validateXml(reqContent, xmlErrors);
//                if (!xmlErrors.isEmpty()) {
//                	String error = "request is NOT VALID. Found " + errors.size() + "errors.";
//                    int errCnt = 1;
//                    for (XmlError err : xmlErrors) {
//                    	error += "\n#" + (errCnt++) + ": " + err.getMessage();
//                    }
//                	throw new LwiValidationException(error);
//                }
                
                try {
	                wsdlcontext.load();
	                
	                WsdlOperation operation = (WsdlOperation) wsdlcontext.getInterface().getOperationList().get(0);
	                WsdlRequest request = operation.addNewRequest("request");
	
	                WsdlValidator validator = new WsdlValidator((WsdlContext) (operation.getInterface()).getDefinitionContext());
	
	                WsdlResponseMessageExchange wsdlResponseMessageExchange = new WsdlResponseMessageExchange(request);
	                wsdlResponseMessageExchange.setRequestContent(reqContent);
	
	                ValidationError[] errors = validator.assertRequest(wsdlResponseMessageExchange, false);
	                
	                if (errors.length > 0) {
	                	String error = "request is NOT VALID. Found " + errors.length + "errors.";
	                    int errCnt = 1;
	                    for (ValidationError err : errors) {
	                    	error += "\n#" + (errCnt++) + ": " + err.toString();
	                    }
	                	throw new LwiValidationException(error);
	                }
                } catch (Exception e) {
                	e.printStackTrace();
                	throw e;
                }
//                
//                
//                System.out.println(org.reficio.ws.legacy.SoapLegacyFacade.class.getResource("/xsds/xop.xsd"));
//                Wsdl wsdl = Wsdl.parse(wsdlLocation);
//
//                if (wsdl.getBindings() == null || wsdl.getBindings().size() == 0) {
//                	throw new Exception("no bindings found in wsdl");
//                } else {
//                    String localPart = wsdl.getBindings().get(0).getLocalPart();
//                    SoapBuilder builder = wsdl.binding().localPart(localPart).find();
//                    SoapOperation op = builder.getOperations().get(0);
//                    try {
//                        builder.validateInputMessage(op, reqContent);
//                    } catch (SoapValidationException e) {
//                    	String error = "request is NOT VALID. Found " + e.getErrors().size() + "errors.";
//                        int errCnt = 1;
//                        for (AssertionError err : e.getErrors()) {
//                        	error += "\n#" + (errCnt++) + ": " + err.getMessage();
//                        }
//                    	throw new LwiValidationException(error);
//                    }
//                }
            }
        }
    }

    private void validateByContext(LwiRequestData lwiRequestData, boolean failOnMissing) throws Exception {
        if (lwiRequestData == null) {
        	throw new Exception("Context parse failed!");
        }

        if (lwiRequestData.parseRequestRequired() && failOnMissing) {
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
