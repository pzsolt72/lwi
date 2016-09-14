package hu.telekom.lwi.plugin.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LwiSecurityUtil {

	private static final String HEX_FORMAT = "%x";
	private static final String MD5_ALGORITHM = "MD5";
	
	public static boolean checkPassword(String plainPassword, String hashedPassword) {
		if (hashedPassword != null) {
			return hashedPassword.equals(toHex(toMD5(plainPassword)));
		}
		return false;
	}
	
	public static String toHex(byte[] value) {
		return String.format(HEX_FORMAT, new BigInteger(1, value));
	}
	
	public static byte[] toMD5(String value) {
		try {
			try {
				return MessageDigest.getInstance(MD5_ALGORITHM).digest(value.getBytes(LwiResourceBundleUtil.ENCODING));
			} catch (UnsupportedEncodingException e) {
				return MessageDigest.getInstance(MD5_ALGORITHM).digest(value.getBytes());
			}
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}
	
}
