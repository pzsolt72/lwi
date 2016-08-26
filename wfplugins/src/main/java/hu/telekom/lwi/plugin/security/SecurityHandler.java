package hu.telekom.lwi.plugin.security;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class SecurityHandler implements HttpHandler {
	
	private static final Logger log = Logger.getLogger(SecurityHandler.class);
	
	private static final String REALM = "LWI_REALM";
	
	private SecurityInitialHandler initialHandler;
	private AuthenticationConstraintHandler constraintHandler;
	private AuthenticationMechanismsHandler mechanismsHandler;
	private AuthenticationCallHandler authenticationCallHandler;
	
	public SecurityHandler(HttpHandler next) {
		List<AuthenticationMechanism> mechanisms = new ArrayList<AuthenticationMechanism>();
		
		mechanisms.add(new BasicAuthenticationMechanism(REALM));
		mechanisms.add(new DigestAuthenticationMechanism(REALM, REALM, "DIGEST"));
		mechanisms.add(new ClientCertAuthenticationMechanism());
		
		authenticationCallHandler = new AuthenticationCallHandler(next);
		mechanismsHandler = new AuthenticationMechanismsHandler(authenticationCallHandler, mechanisms);
		constraintHandler = new AuthenticationConstraintHandler(mechanismsHandler);
		initialHandler = new SecurityInitialHandler(AuthenticationMode.CONSTRAINT_DRIVEN, new LwiIdentityManager(), constraintHandler);
	}
	
	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		log.info(String.format("SecurityHandler.handleRequest invoked"));
		initialHandler.handleRequest(exchange);
	}
}
