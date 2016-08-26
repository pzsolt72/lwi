package hu.telekom.lwi.plugin.security;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;

public class LwiIdentityManager implements IdentityManager {

	private static final Logger log = Logger.getLogger(LwiIdentityManager.class);
	
	@Override
	public Account verify(Account account) {
		log.info("LwiIdentityManager.verify invoked - "+account);
		return account;
	}

	@Override
	public Account verify(final String id, final Credential credential) {
		log.info("LwiIdentityManager.verify invoked - "+id+" - "+credential);
		
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
		log.info("LwiIdentityManager.verify invoked - "+credential);
		return verify("LWI_ADMIN", credential);
	}

}
