package hu.telekom.lwi.plugin;

public class LwiContext {
	
	private String requestContentString;	
	private String requestContentFile;
	private String responseContentString;
	private String responseContentFile;
	public String getRequestContentString() {
		return requestContentString;
	}
	public void setRequestContentString(String requestContentString) {
		this.requestContentString = requestContentString;
	}
	public String getRequestContentFile() {
		return requestContentFile;
	}
	public void setRequestContentFile(String requestContentFile) {
		this.requestContentFile = requestContentFile;
	}
	public String getResponseContentString() {
		return responseContentString;
	}
	public void setResponseContentString(String responseContentString) {
		this.responseContentString = responseContentString;
	}
	public String getResponseContentFile() {
		return responseContentFile;
	}
	public void setResponseContentFile(String responseContentFile) {
		this.responseContentFile = responseContentFile;
	}

	
	
	
}
