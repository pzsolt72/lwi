package hu.telekom.lwi.plugin.security;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.jboss.logging.Logger;

import hu.telekom.lwi.plugin.util.LwiResourceBundleUtil;
import hu.telekom.lwi.plugin.util.LwiSecurityUtil;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class LwiSecurityHandler implements HttpHandler, IdentityManager {
	
	private static final Logger log = Logger.getLogger(LwiSecurityHandler.class);
	
	private static final String REALM = "ApplicationRealm";
	
	private SecurityInitialHandler initialHandler;
	private AuthenticationConstraintHandler constraintHandler;
	private AuthenticationMechanismsHandler mechanismsHandler;
	private AuthenticationCallHandler authenticationCallHandler;
	
	private static ResourceBundle applicationUsers = LwiResourceBundleUtil.getJbossConfig("application-users.properties");
	private static ResourceBundle applicationRoles = LwiResourceBundleUtil.getJbossConfig("application-roles.properties");
	
	public LwiSecurityHandler(HttpHandler next) {
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
	public Account verify(final String userId, final Credential credential) {
		log.info("SecurityHandler.verify invoked - "+userId+" - "+credential);
		
		if (credential != null) {
			try {
				boolean authenticated = false;
				if (credential instanceof PasswordCredential) {
					String password = new String(((PasswordCredential) credential).getPassword());
					authenticated = LwiSecurityUtil.checkPassword(userId+":"+REALM+":"+password, applicationUsers.getString(userId));
				}
				
				if (authenticated) {
					return new Account() {
						@Override
						public Set<String> getRoles() {
							Set<String> roles = new HashSet<String>();
							try {
								for (String role : applicationRoles.getString(userId).split(",")) {
									roles.add(role);
								}
							} catch (MissingResourceException e) {}
							return roles;
						}
						
						@Override
						public Principal getPrincipal() {
							return new Principal() {
								@Override
								public String getName() {
									return userId;
								}
							};
						}
					};
				} else {
					log.warn(String.format("LwiSecurityHandler - authentication failed for user (%s)...", userId));
				}
			} catch (MissingResourceException e) {
				// couldn't find user in properties
				log.warn(String.format("LwiSecurityHandler - not found user sent by client (%s)...", userId));
			}
		}
		return null;
	}

	@Override
	public Account verify(Credential credential) {
		log.info("SecurityHandler.verify invoked - "+credential);
		return verify("LWI_ADMIN", credential);
	}

}
