package hu.telekom.lwi.plugin.validation;

import java.util.ArrayList;

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
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlValidator;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlResponseMessageExchange;
import com.eviware.soapui.model.workspace.WorkspaceFactory;
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
    private boolean forceValidation = false;

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

                SoapUI.getSettings().setBoolean(WsdlSettings.STRICT_SCHEMA_TYPES, false);
                WsdlContext wsdlcontext = new WsdlContext(wsdlLocation/*, new WsdlInterface(null, null)*/);

                try {
	                wsdlcontext.load();
	                
	                WsdlValidator validator = new WsdlValidator(wsdlcontext);

	                validator.validateXml(reqContent, new ArrayList<>()); // ez csak a wsdl validacio -> az megy

//	                final WsdlProject wsdlProject = new WsdlProject();
//        			final WsdlInterface[] wsdlInterfaces = WsdlInterfaceFactory.importWsdl(wsdlProject, wsdlLocation, true);

	                WsdlOperation operation = wsdlcontext.getInterface().getOperationAt(0); // itt sajnos az interface mindig null -> fent talan kellene inicializalni, de akkor meg nem fordul, mert nem latja a WsdlInterfaceConfig-ot
	                WsdlRequest request = operation.addNewRequest("request");
	            	
	                WsdlResponseMessageExchange messageExchange = new WsdlResponseMessageExchange(request);
	                messageExchange.setRequestContent(reqContent);
	                
		            ValidationError[] errors = validator.assertRequest(messageExchange, false);
	                
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
