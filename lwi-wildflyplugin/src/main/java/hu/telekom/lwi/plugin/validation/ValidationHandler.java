package hu.telekom.lwi.plugin.validation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;

import org.jboss.logging.Logger;
import org.reficio.ws.SoapValidationException;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import hu.telekom.lwi.plugin.log.LwiRequestData;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.Sender;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class ValidationHandler implements HttpHandler {

    private static final int VALIDATION_ERROR_CODE = 500;
    private static final String VALIDATION_ERROR_MSG = "Message validation failed";
    private final Logger log = Logger.getLogger(ValidationHandler.class);
    private final String logPrefix = "ValidationHandler > ";
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
        log.info(logPrefix + "resource: " + Wsdl.class.getResource("/xsds/xop.xsd"));
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
                Wsdl wsdl = Wsdl.parse(wsdlLocation);
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

        LwiRequestData lwiRequestData = new LwiRequestData(httpServerExchange);
        try {
            Stack<String> qNames = new Stack<>();
        	qNames.push("ROOT");
        	
        	LwiLogAttributeUtil.getMessageAttributes(qNames, lwiRequestData, reqContent);
        	
        	
        	if ( lwiRequestData.getRequestId() != null ) {
        		httpServerExchange.getRequestHeaders().add(new HttpString(LwiLogAttribute.HTTP_HEADER + LwiLogAttribute.RequestId.name()), lwiRequestData.getRequestId());
        		log.debug(logPrefix + "Adding RequestId to the header");
        	}
        	
        	if ( lwiRequestData.getCorrelationId() != null ) {
        		httpServerExchange.getRequestHeaders().add(new HttpString(LwiLogAttribute.HTTP_HEADER + LwiLogAttribute.CorrelationId.name()), lwiRequestData.getCorrelationId());
        		log.debug(logPrefix + "Adding CorrelationId to the header");
        	}
        	
        	if ( lwiRequestData.getUserId() != null ) {
        		httpServerExchange.getRequestHeaders().add(new HttpString(LwiLogAttribute.HTTP_HEADER + LwiLogAttribute.UserId.name()), lwiRequestData.getUserId());
        		log.debug(logPrefix + "Adding UserId to the header");
        	}
        	
        	
        } catch (Exception e) {
            failReason = "Not a soap message";
            log.error(logPrefix + failReason + " reqMsg=" + "(length=" + reqContent.length() + ") " + reqContent.substring(0,(reqContent.length() < 1001 ? reqContent.length() : 1000)));
            return false;
        }
        return isValidationOk(lwiRequestData);
    }

    private boolean isValidationOk(LwiRequestData lwiRequestData) {
        boolean validationOk = true;
        if (lwiRequestData.getCorrelationId() == null || lwiRequestData.getUserId() == null) {
            validationOk = false;
            failReason = "Attribute validation failed! requestId=" + lwiRequestData.getRequestId() + ", correlationId=" + lwiRequestData.getCorrelationId() + ", userId=" + lwiRequestData.getUserId();
            log.error(logPrefix + failReason);
        } else {
            log.info(logPrefix + "Attribute validation OK requestId=" + lwiRequestData.getRequestId() + ", correlationId=" + lwiRequestData.getCorrelationId() + ", userId=" + lwiRequestData.getUserId());
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
                                            if (exchange.isComplete()) {
                                                log.warn(logPrefix + "reading buffer while exchange is complete already. readBuffers = " + readBuffers);
                                                return;
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
                                                if (exchange.isComplete()) {
                                                    log.warn(logPrefix + "reading buffer while exchange is complete already. readBuffers = " + readBuffers);
                                                    return;
                                                }
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
