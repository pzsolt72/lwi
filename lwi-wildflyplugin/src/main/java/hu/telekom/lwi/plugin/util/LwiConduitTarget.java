package hu.telekom.lwi.plugin.util;

public interface LwiConduitTarget {

	public void processRequest(String request, boolean lastPart);

	public void acceptRequest(String request, boolean lastPart);

	public void processResponse(String response, boolean lastPart);

	public void acceptResponse(String response, boolean lastPart);

	public void exchangeCompleted();
	
}
