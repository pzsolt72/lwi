package hu.telekom.lwi.plugin.security;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class SecurityHandler implements HttpHandler, IdentityManager {
	
	private static final Logger log = Logger.getLogger(SecurityHandler.class);
	
	private static final String REALM = "LWI_REALM";
	
	private SecurityInitialHandler initialHandler;
	private AuthenticationConstraintHandler constraintHandler;
	private AuthenticationMechanismsHandler mechanismsHandler;
	private AuthenticationCallHandler authenticationCallHandler;
	
	public SecurityHandler(HttpHandler next) {
		List<AuthenticationMechanism> mechanisms = new ArrayList<AuthenticationMechanism>();
		
		mechanisms.add(new BasicAuthenticationMechanism(REALM));
//		mechanisms.add(new DigestAuthenticationMechanism(REALM, REALM, "DIGEST"));
		mechanisms.add(new ClientCertAuthenticationMechanism());

		authenticationCallHandler = new AuthenticationCallHandler(next);
		mechanismsHandler = new AuthenticationMechanismsHandler(authenticationCallHandler, mechanisms);
		constraintHandler = new AuthenticationConstraintHandler(mechanismsHandler);
		initialHandler = new SecurityInitialHandler(AuthenticationMode.CONSTRAINT_DRIVEN, this, constraintHandler);
	}
	
	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		log.info(String.format("SecurityHandler.handleRequest invoked"));
		initialHandler.handleRequest(exchange);
	}

	@Override
	public Account verify(Account account) {
		log.info("SecurityHandler.verify invoked - "+account);
		return account;
	}

	@Override
	public Account verify(final String id, final Credential credential) {
		log.info("SecurityHandler.verify invoked - "+id+" - "+credential);
		
		return new Account() {
			@Override
			public Set<String> getRoles() {
				Set<String> roles = new HashSet<String>();
				roles.add("LWI_ROLE");
				return roles;
			}
			
			@Override
			public Principal getPrincipal() {
				return new Principal() {
					@Override
					public String getName() {
						return id;
					}
				};
			}
		};
	}

	@Override
	public Account verify(Credential credential) {
		log.info("SecurityHandler.verify invoked - "+credential);
		return verify("LWI_ADMIN", credential);
	}

}
