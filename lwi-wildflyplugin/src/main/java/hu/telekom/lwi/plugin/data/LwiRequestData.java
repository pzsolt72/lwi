package hu.telekom.lwi.plugin.data;

import hu.telekom.lwi.plugin.log.LwiLogAttribute;
import hu.telekom.lwi.plugin.util.LwiLogAttributeUtil;
import io.undertow.server.HttpServerExchange;

public class LwiRequestData {
	
	protected static final String LOG_FORMAT = "[RequestId: %s CorrelationId: %s UserId: %s]";

	private String requestId;
	private String correlationId;
	private String userId;

	public LwiRequestData(HttpServerExchange exchange) {
		requestId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.RequestId);
		correlationId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.CorrelationId);
		userId = LwiLogAttributeUtil.getHttpHeaderAttribute(exchange, LwiLogAttribute.UserId);
	}

	public boolean isNullRequestId() {
		return requestId == null;
	}

	public boolean isNullCorrelationId() {
		return correlationId == null;
	}

	public boolean isNullUserId() {
		return userId == null;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public boolean parseRequestRequired() {
		return isNullRequestId() || isNullCorrelationId() || isNullUserId();
	}
	
	public String toLog() {
		return String.format(LOG_FORMAT, requestId!=null?requestId:LwiLogAttribute.EMPTY, correlationId!=null?correlationId:LwiLogAttribute.EMPTY, userId!=null?userId:LwiLogAttribute.EMPTY);
	}

	public String getRequestId() {
		return requestId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getUserId() {
		return userId;
	}

}
