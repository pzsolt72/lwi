package hu.telekom.lwi.plugin;

import io.undertow.server.HttpHandler;

public abstract class LwiAbstractHandler implements HttpHandler  {
	
	protected LwiContext lwiContext;
	
	protected HttpHandler next;
	
	public LwiAbstractHandler(LwiContext lwiContext, HttpHandler next ) {
		
		this.next = next;
		this.lwiContext = lwiContext;
		
	}
	
	
	
	
	
	
	
	

}
