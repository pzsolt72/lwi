package hu.telekom.lwi.plugin.log;

public enum LwiLogAttribute {

	RequestId("MessageContext/RequestId", "eiMessageContext/requestId"),
	CorrelationId("MessageContext/CorrelationId", "eiMessageContext/correlationId"),
	UserId("MessageContext/UserId", "eiMessageContext/sender");
	
	public static final String HTTP_HEADER = "X-MT-"; 
	
	public static final String EMPTY = "N/A";
	
	private String newOsbField;
	private String techOsbField;
	private LwiLogAttribute(String newOsbField, String techOsbField) {
		this.newOsbField = newOsbField;
		this.techOsbField = techOsbField;
	}

	public String getHttpHeaderAttribute() {
		return HTTP_HEADER + name();
	}
	
	public String getSoapAttribute() {
		return "//*[@RequestId]/@RequestId";
		//return "//" + name() + "/text()";
	}

	public String getNewOSBAttribute() {
		return "//" + newOsbField + "/text()";
	}

	public String getTechOSBAttribute() {
		return "//" + techOsbField + "/text()";
	}
}
