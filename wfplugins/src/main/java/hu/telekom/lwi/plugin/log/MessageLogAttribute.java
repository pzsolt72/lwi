package hu.telekom.lwi.plugin.log;

public enum MessageLogAttribute {

	RequestId,
	CorrelationId,
	UserId;
	
	public static final String HTTP_HEADER = "X-MT-"; 
	
	public static final String EMPTY = "N/A";

	public String getHttpHeaderAttribute() {
		return HTTP_HEADER + name();
	}
	
	public String getSoapAttribute() {
		return name();
	}
}
