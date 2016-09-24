package hu.telekom.lwi.plugin.security;

import static io.undertow.UndertowMessages.MESSAGES;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;

public class LwiCertHeaderAuthMechanism implements AuthenticationMechanism {

	private static final String LWI_SSL_CN_HEADER = "LWI_SSL_CN_HEADER";
	public static final String SSL_USERNAME_HEADER = "X-SSL-Client-CN";

	@Override
	public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {

		HeaderValues headers = exchange.getRequestHeaders().get(SSL_USERNAME_HEADER);
		
		String sslCN = null;
		
		if ( headers != null ) sslCN = headers.getFirst();
					
		if (sslCN != null && !"".equals(sslCN)) {

			IdentityManager idm = securityContext.getIdentityManager();

			final AuthenticationMechanismOutcome result;
			
			Account account = idm.verify(sslCN, new LwiCertHeaderCredential());
			if (account != null) {
				securityContext.authenticationComplete(account, LWI_SSL_CN_HEADER, false);
				result = AuthenticationMechanismOutcome.AUTHENTICATED;
			} else {
				securityContext.authenticationFailed(MESSAGES.authenticationFailed(sslCN), LWI_SSL_CN_HEADER);
				result = AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
			}
			return result;

		}

		return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;

	}

	@Override
	public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        exchange.getResponseHeaders().add(WWW_AUTHENTICATE, LWI_SSL_CN_HEADER);
        UndertowLogger.SECURITY_LOGGER.debugf("Sending lwi auth challenge %s for %s", LWI_SSL_CN_HEADER, exchange);
        return new ChallengeResult(true, UNAUTHORIZED);	}

	
	
}
