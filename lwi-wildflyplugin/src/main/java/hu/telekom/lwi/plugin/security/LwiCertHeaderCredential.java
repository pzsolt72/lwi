package hu.telekom.lwi.plugin.security;

import io.undertow.security.idm.Credential;

/**
 * Used to identify the SSL based authentication forwarded from the SSL offload component.s
 * 
 * @author paroczi1zso420-dvr
 *
 */
public class LwiCertHeaderCredential implements Credential {
	
	private String cn;

	public String getCn() {
		return cn;
	}

	public void setCn(String cn) {
		this.cn = cn;
	}
	
	

}
