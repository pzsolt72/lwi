package hu.telekom.lwi.plugin.validation;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.Sender;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;
import org.reficio.ws.SoapValidationException;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.w3c.dom.Document;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class ValidationHandler implements HttpHandler {

    private static final int VALIDATION_ERROR_CODE = 500;
    private static final String VALIDATION_ERROR_MSG = "Message validation failed";
    private final Logger log = Logger.getLogger(ValidationHandler.class);
    private final String logPrefix = "LogRequestHandler > ";
    private final int maxBuffers = 100000;
    private ValidationType validationType = ValidationType.MSG;
    private String wsdlLocation = null;
    private HttpHandler next = null;
    private String msg = null;
    private String failReason = null;


    public ValidationHandler(HttpHandler next) {
        this.next = next;
    }

    public void setValidationType(String validationType) {
        this.validationType = ValidationType.valueOf(validationType);
    }

    public void setWsdlLocation(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        log.info(logPrefix + "handle start...");
        boolean validationOk = true;

        try {
            if (validationType == ValidationType.MSG) {
                log.info(logPrefix + "validating by message...");
                validationOk = validateByMsg(httpServerExchange);
            } else if (validationType == ValidationType.CTX) {
                log.info(logPrefix + "validating by context...");
                validationOk = validateByContext(httpServerExchange);
            } else if (validationType == ValidationType.NO) {
                log.info(logPrefix + "skipping validation");
            }
        } catch (Exception e) {
            validationOk = false;
            failReason = "Internal error: " + e.getMessage();
            log.error(logPrefix + failReason, e);
        }

        if (validationOk) {
            log.info(logPrefix + "Validation OK.");
            next.handleRequest(httpServerExchange);
        } else {
            log.error(logPrefix + "Validation FAILED.");
            httpServerExchange.setStatusCode(VALIDATION_ERROR_CODE);
            Sender sender = httpServerExchange.getResponseSender();
            sender.send(createSoapFault(validationType, failReason));
        }

    }

    private boolean validateByMsg(HttpServerExchange httpServerExchange) {
        boolean validationOk = true;
        String reqContent = getRequestMessage(httpServerExchange);
        log.info(logPrefix + "message retrieved");
        log.info(logPrefix + "message: " + reqContent.substring(0, reqContent.length() > 1000 ? 1000 : reqContent.length()));
        //log.info(logPrefix + "resource: " + this.getClass().getResource("/xsds/xop.xsd").toExternalForm());
        if (reqContent.length() == 0) {
            failReason = "no msg found in lwi context";
            log.error(logPrefix + failReason);
            validationOk = false;
        } else {
            if (wsdlLocation == null) {
                failReason = "no wsdlLocation filter param defined";
                log.error(logPrefix + failReason);
                validationOk = false;
            } else {
                log.info(logPrefix + "parsing wsdl: " + wsdlLocation);
                Wsdl wsdl = Wsdl.parse(wsdlLocation); // "file:///d:/work/wsdl-validator/data/GetBillingProfileId_v1.wsdl"
                log.info(logPrefix + "wsdl parsed");
                if (wsdl.getBindings() == null || wsdl.getBindings().size() == 0) {
                    failReason = "no bindings found in wsdl";
                    log.error(logPrefix + failReason);
                    validationOk = false;
                } else {
                    String localPart = wsdl.getBindings().get(0).getLocalPart();
                    SoapBuilder builder = wsdl.binding().localPart(localPart).find();
                    SoapOperation op = builder.getOperations().get(0);
                    try {
                        builder.validateInputMessage(op, reqContent);
                        log.debug(logPrefix + "request is VALID");
                    } catch (SoapValidationException e) {
                        failReason = "request is NOT VALID. Found " + e.getErrors().size() + "errors.";
                        log.error(logPrefix + failReason);
                        validationOk = false;
                        int errCnt = 1;
                        for (AssertionError err : e.getErrors()) {
                            failReason += "\n#" + (errCnt++) + ": " + err.getMessage();
                        }
                    }
                }
            }
        }
        return validationOk;
    }

    private boolean validateByContext(HttpServerExchange httpServerExchange) {
        String reqContent = getRequestMessage(httpServerExchange);
        Document doc;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(new ByteArrayInputStream(reqContent.getBytes())); // TODO encoding?
        } catch (Exception e) {
            failReason = "Not a soap message";
            log.error(logPrefix + failReason);
            return false;
        }
        String requestId;
        String correlationId;
        String userId;

        // 1. soap attribute
        requestId = LwiLogAttributeUtil.getSoapAttribute(doc, LwiLogAttribute.RequestId);
        if (requestId != null) { // ha talalt requestId-t, akkor a tobbit is ott varja
            log.debug("Soap attribute requestId detected");
            correlationId = LwiLogAttributeUtil.getSoapAttribute(doc, LwiLogAttribute.CorrelationId);
            userId = LwiLogAttributeUtil.getSoapAttribute(doc, LwiLogAttribute.UserId);
            return isValidationOk(requestId, correlationId, userId);
        }

        // 2. tech osb
        requestId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.RequestId);
        if (requestId != null) {
            log.debug("TechOSB requestId detected");
            correlationId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.CorrelationId);
            userId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.UserId);
            return isValidationOk(requestId, correlationId, userId);
        }

        // 3. new osb
        requestId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.RequestId);
        if (requestId != null) {
            log.debug("NewOSB requestId detected");
            correlationId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.CorrelationId);
            userId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.UserId);
            return isValidationOk(requestId, correlationId, userId);
        }

        // 4. http header
        // TODO ha nincs msg, ezt akkor is lehet vizsgalni
        requestId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.RequestId);
        if (requestId != null) {
            log.debug("HTTP Header requestId detected");
            correlationId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.CorrelationId);
            userId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.UserId);
            return isValidationOk(requestId, correlationId, userId);
        } else {
            failReason = "No applicable context detected";
            log.error(logPrefix + failReason);
            return false;
        }
    }

    private boolean isValidationOk(String requestId, String correlationId, String userId) {
        boolean validationOk = true;
        if (correlationId == null || userId == null) {
            validationOk = false;
            failReason = "Attribute validation failed! requestId=" + requestId + ", correlationId=" + correlationId + ", userId=" + userId;
            log.error(logPrefix + failReason);
        } else {
            log.info(logPrefix + "Attribute validation OK requestId=" + requestId + ", correlationId=" + correlationId + ", userId=" + userId);
        }
        return validationOk;
    }

    private String getRequestMessage(HttpServerExchange exchange) {
        if (msg == null) {
            try {
                final StreamSourceChannel channel = exchange.getRequestChannel();
                int readBuffers = 0;
                final PooledByteBuffer[] bufferedData = new PooledByteBuffer[maxBuffers];
                PooledByteBuffer buffer = exchange.getConnection().getByteBufferPool().allocate();
                do {
                    int r;
                    ByteBuffer b = buffer.getBuffer();
                    r = channel.read(b);
                    if (r == -1) { //TODO: listener read
                        if (b.position() == 0) {
                            buffer.close();
                        } else {
                            b.flip();
                            bufferedData[readBuffers] = buffer;
                        }
                        break;
                    } else if (r == 0) {
                        final PooledByteBuffer finalBuffer = buffer;
                        final int finalReadBuffers = readBuffers;
                        channel.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {

                            PooledByteBuffer buffer = finalBuffer;
                            int readBuffers = finalReadBuffers;

                            public void handleEvent(StreamSourceChannel channel) {
                                try {
                                    do {
                                        int r;
                                        ByteBuffer b = buffer.getBuffer();
                                        r = channel.read(b);
                                        if (r == -1) { //TODO: listener read
                                            if (b.position() == 0) {
                                                buffer.close();
                                            } else {
                                                b.flip();
                                                bufferedData[readBuffers] = buffer;
                                            }
                                            Connectors.ungetRequestBytes(exchange, bufferedData);
                                            Connectors.resetRequestChannel(exchange);
                                            Connectors.executeRootHandler(next, exchange);
                                            channel.getReadSetter().set(null);
                                            return;
                                        } else if (r == 0) {
                                            return;
                                        } else if (!b.hasRemaining()) {
                                            b.flip();
                                            bufferedData[readBuffers++] = buffer;
                                            if (readBuffers == maxBuffers) {
                                                Connectors.ungetRequestBytes(exchange, bufferedData);
                                                Connectors.resetRequestChannel(exchange);
                                                Connectors.executeRootHandler(next, exchange);
                                                channel.getReadSetter().set(null);
                                                return;
                                            }
                                            buffer = exchange.getConnection().getByteBufferPool().allocate();
                                        }
                                    } while (true);
                                } catch (IOException e) {
                                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                                    for (int i = 0; i < bufferedData.length; ++i) {
                                        IoUtils.safeClose(bufferedData[i]);
                                    }
                                    exchange.endExchange();
                                }
                            }
                        });
                        channel.resumeReads();
                        break;
                    } else if (!b.hasRemaining()) {
                        b.flip();
                        bufferedData[readBuffers++] = buffer;
                        if (readBuffers == maxBuffers) {
                            break;
                        }
                        buffer = exchange.getConnection().getByteBufferPool().allocate();
                    }
                } while (true);

                StringBuilder sb = new StringBuilder();
                String charset = exchange.getRequestCharset() != null ? exchange.getRequestCharset() : "UTF-8";
                for (int i = 0; i < bufferedData.length; i++) {
                    if (bufferedData[i] == null) break;
                    byte[] byteBuffer = new byte[bufferedData[i].getBuffer().remaining()];
                    bufferedData[i].getBuffer().get(byteBuffer);

                    // IMPORTANT: this is the key - to flip back buffer to starting point
                    // 			  because ungetRequestBytes only puts back the same buffers into attachment again (in their current state)
                    bufferedData[i].getBuffer().flip();

                    sb.append(new String(byteBuffer, charset));
                }

                msg = sb.toString();

                Connectors.ungetRequestBytes(exchange, bufferedData);
                Connectors.resetRequestChannel(exchange);

            } catch (IOException e) {
                failReason = "Internal error. Unable to get post request message";
                log.error(logPrefix, failReason, e);
            }
        }
        return msg;
    }

    private static String createSoapFault(ValidationType validationType, String msg) {
        String template = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <SOAP-ENV:Body>\n" +
                "      <SOAP-ENV:Fault>\n" +
                "         <faultcode>SOAP-ENV:Client</faultcode>\n" +
                "         <faultstring>\n" +
                "          %s\n" +
                "          %s\n" +
                "         </faultstring>\n" +
                "      </SOAP-ENV:Fault>\n" +
                "   </SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
        return String.format(template, validationType.toString() + " validation FAILED", msg);
    }
}
