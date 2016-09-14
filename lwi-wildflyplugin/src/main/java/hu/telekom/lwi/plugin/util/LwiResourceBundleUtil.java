package hu.telekom.lwi.plugin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class LwiResourceBundleUtil {
	
	public static final String ENCODING = "UTF-8";
	private static final String JBOSS_CONFIG_PATH = "../standalone/configuration/";
	
	public static ResourceBundle getJbossConfig(String name) {
		try (FileInputStream fis = new FileInputStream(new File(JBOSS_CONFIG_PATH+name))) {
			return new PropertyResourceBundle(new InputStreamReader(fis, ENCODING));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
