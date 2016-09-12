package hu.telekom.lwi.plugin.validation;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.UndertowLogger;
import io.undertow.connector.PooledByteBuffer;
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

    private final Logger log = Logger.getLogger(ValidationHandler.class);
    private final String logPrefix = "LogRequestHandler > ";
    private final int maxBuffers = 100000;
    private ValidationType validationType = ValidationType.MSG;
    private String wsdlLocation = null;
    private HttpHandler next = null;
    String msg = null;


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
        if (validationType == ValidationType.MSG) {
            log.info(logPrefix + "validating by message...");
            String reqContent = getRequestMessage(httpServerExchange);
            log.info(logPrefix + "message retrieved");
            log.info(logPrefix + "message: " + reqContent.substring(0,reqContent.length() > 1000 ? 1000 : reqContent.length()));
            //log.info(logPrefix + "resource: " + this.getClass().getResource("/xsds/xop.xsd").toExternalForm());
            if (reqContent.length() == 0) {
                log.error(logPrefix + "no msg found in lwi context");
            } else {
                if (wsdlLocation == null) {
                    log.error(logPrefix + "no wsdlLocation filter param defined");
                } else {
                    log.info(logPrefix + "parsing wsdl: " + wsdlLocation);
                    Wsdl wsdl = Wsdl.parse(wsdlLocation); // "file:///d:/work/wsdl-validator/data/GetBillingProfileId_v1.wsdl"
                    log.info(logPrefix + "wsdl parsed");
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
                            for (AssertionError err : e.getErrors()) {
                                log.debug(logPrefix + "request ERROR #" + (errCnt++) + ": " + err.getMessage());
                            }
                        }

                        // TODO 500-as hiba, ha nem valid
                    }

                }
            }
        } else if (validationType == ValidationType.CTX) {
            log.debug(logPrefix + "validating by context...");
            String reqContent = getRequestMessage(httpServerExchange);
            Document doc = null;
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                doc = dBuilder.parse(new ByteArrayInputStream(reqContent.getBytes())); // TODO encoding?
            } catch (Exception e) {
                // not a soap message
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
                validationOk = isValidationOk(requestId, correlationId, userId);
            } else {

                // 2. tech osb
                requestId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.RequestId);
                if (requestId != null) {
                    log.debug("TechOSB requestId detected");
                    correlationId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.CorrelationId);
                    userId = LwiLogAttributeUtil.getTechOSBAttribute(doc, LwiLogAttribute.UserId);
                    validationOk = isValidationOk(requestId, correlationId, userId);
                } else {

                    // 3. new osb
                    requestId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.RequestId);
                    if (requestId != null) {
                        log.debug("NewOSB requestId detected");
                        correlationId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.CorrelationId);
                        userId = LwiLogAttributeUtil.getNewOSBAttribute(doc, LwiLogAttribute.UserId);
                        validationOk = isValidationOk(requestId, correlationId, userId);
                    } else {

                        // 4. http header
                        requestId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.RequestId);
                        if (requestId != null) {
                            log.debug("HTTP Header requestId detected");
                            correlationId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.CorrelationId);
                            userId = LwiLogAttributeUtil.getHttpHeaderAttribute(httpServerExchange, LwiLogAttribute.UserId);
                            validationOk = isValidationOk(requestId, correlationId, userId);
                        } else {
                            validationOk = false;
                            log.error("No applicable context detected");
                        }
                    }
                }
            }
        }

        if (validationOk) {
            log.info(logPrefix + "Validation OK.");
        } else {
            log.error(logPrefix + "Validation FAILED.");
            // TODO 500
        }

        log.debug(logPrefix + "handle complete");

        next.handleRequest(httpServerExchange);

    }

    private boolean isValidationOk(String requestId, String correlationId, String userId) {
        boolean validationOk = true;
        if (correlationId == null || userId == null) {
            validationOk = false;
            log.error("Attribute validation failed! requestId=" + requestId + ", correlationId=" + correlationId + ", userId=" + userId);
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
                log.error(logPrefix, "Unable to get request message", e);
            }
        }
        return msg;
    }
}
