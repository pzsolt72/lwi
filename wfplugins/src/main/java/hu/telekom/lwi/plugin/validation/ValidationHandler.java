package hu.telekom.lwi.plugin.validation;

import hu.telekom.lwi.plugin.LwiAbstractHandler;
import hu.telekom.lwi.plugin.LwiContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;
import org.reficio.ws.SoapValidationException;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;

public class ValidationHandler extends LwiAbstractHandler {

    private final Logger log = Logger.getLogger(this.getClass());

    private ValidationType validationType = ValidationType.MSG;
    private String wsdlLocation = null;
    private final String logPrefix = "LogRequestHandler > ";

    public ValidationHandler(LwiContext lwiContext, HttpHandler next) {
        super(lwiContext, next);
    }

    public void setValidationType(String validationType) {
        this.validationType = ValidationType.valueOf(validationType);
    }

    public void setWsdlLocation(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        log.debug(logPrefix + "handle start...");

        if (validationType == ValidationType.MSG) {
            String reqContent = lwiContext.getRequestContentString();
            if (reqContent == null) {
                log.error(logPrefix + "no msg found in lwi context");
            } else {
                if (wsdlLocation == null) {
                    log.error(logPrefix + "no wsdlLocation filter param defined");
                } else {
                    log.debug(logPrefix + "parsing wsdl: " + wsdlLocation);
                    Wsdl wsdl = Wsdl.parse(wsdlLocation); // "file:///d:/work/wsdl-validator/data/GetBillingProfileId_v1.wsdl"
                    log.debug(logPrefix + "wsdl parsed");
                    if (wsdl.getBindings() == null || wsdl.getBindings().size() == 0) {
                        log.error(logPrefix + "no bindings found in wsdl");
                    } else {
                        String localPart = wsdl.getBindings().get(0).getLocalPart();
                        SoapBuilder builder = wsdl.binding().localPart(localPart).find();
                        SoapOperation op = builder.getOperations().get(0);
                        try {
                            builder.validateInputMessage(op, reqContent);
                            log.debug(logPrefix + "request is VALID");
                        } catch (SoapValidationException e) {
                            log.debug(logPrefix + "request is NOT VALID. Found " + e.getErrors().size() + "errors: ");
                            int errCnt = 1;
                            for(AssertionError err : e.getErrors()) {
                                log.debug(logPrefix + "request ERROR #" + (errCnt++) + ": " + err.getMessage());
                            }
                        }

                        // TODO 500-as hiba, ha nem valid
                    }

                }
            }
        }


        log.debug(logPrefix + "handle complete");

        next.handleRequest(httpServerExchange);

    }
}
