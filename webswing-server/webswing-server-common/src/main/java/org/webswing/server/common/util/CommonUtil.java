package org.webswing.server.common.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.lang3.ClassUtils.Interfaces;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webswing.Constants;

import main.Main;

public class CommonUtil {
	public static final int bufferSize = 4 * 1024;
	private static final String DEFAULT = "default";
	private static final Logger log = LoggerFactory.getLogger(CommonUtil.class);
	private static final Map<String, byte[]> iconMap = new HashMap<String, byte[]>();

	public static byte[] loadImage(File iconFile) {
		String icon;
		if (iconFile == null || !iconFile.isFile()) {
			icon = null;
		} else {
			icon = iconFile.getAbsolutePath();
		}
		try {
			if (icon == null) {
				if (iconMap.containsKey(DEFAULT)) {
					return iconMap.get(DEFAULT);
				} else {
					BufferedImage defaultIcon = ImageIO.read(CommonUtil.class.getClassLoader().getResourceAsStream("images/java.png"));
					byte[] b64icon = getPngImage(defaultIcon);
					iconMap.put(DEFAULT, b64icon);
					return b64icon;
				}
			} else {
				if (iconMap.containsKey(icon)) {
					return iconMap.get(icon);
				} else {
					BufferedImage defaultIcon = ImageIO.read(new File(icon));
					byte[] b64icon = getPngImage(defaultIcon);
					iconMap.put(icon, b64icon);
					return b64icon;
				}
			}
		} catch (IOException e) {
			log.error("Failed to load image " + icon, e);
			return null;
		}
	}

	public static File resolveFile(String name, String homeDir, StrSubstitutor external) {
		StrSubstitutor subs = external == null ? getConfigSubstitutor() : external;
		if (name == null) {
			return null;
		}
		name = subs.replace(name);
		homeDir = subs.replace(homeDir);
		File relativeToHomeInRoot = new File(Main.getRootDir(), homeDir + File.separator + name);
		if (relativeToHomeInRoot.exists()) {
			return relativeToHomeInRoot;
		}
		File relativeToHome = new File(homeDir + File.separator + name);
		if (relativeToHome.exists()) {
			return relativeToHome;
		}
		File absolute = new File(name);
		if (absolute.exists()) {
			return absolute;
		}
		return null;
	}

	private static byte[] getPngImage(BufferedImage imageContent) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
			ImageIO.write(imageContent, "png", ios);
			byte[] result = baos.toByteArray();
			baos.close();
			return result;
		} catch (IOException e) {
			log.error("Writing image interupted:" + e.getMessage(), e);
		}
		return null;
	}

	public static String getWarFileLocation() {
		String warFile = System.getProperty(Constants.WAR_FILE_LOCATION);
		if (warFile == null) {
			ProtectionDomain domain = Main.class.getProtectionDomain();
			URL location = domain.getCodeSource().getLocation();
			String locationString = location.toExternalForm();
			if (locationString.endsWith("/WEB-INF/classes/")) {
				locationString = locationString.substring(0, locationString.length() - "/WEB-INF/classes/".length());
			}
			System.setProperty(Constants.WAR_FILE_LOCATION, locationString);
			return locationString;
		}
		return warFile;
	}

	public static Map<String, String> getConfigSubstitutorMap(String user, String sessionId, String clientIp, String locale, String customArgs) {

		Map<String, String> result = new HashMap<String, String>();
		result.putAll(System.getenv());
		for (final String name : System.getProperties().stringPropertyNames()) {
			result.put(name, System.getProperties().getProperty(name));
		}
		if (user != null) {
			result.put(Constants.USER_NAME_SUBSTITUTE, user);
		}
		if (sessionId != null) {
			result.put(Constants.SESSION_ID_SUBSTITUTE, sessionId);
		}
		if (clientIp != null) {
			result.put(Constants.SESSION_IP_SUBSTITUTE, clientIp);
		}
		if (locale != null) {
			result.put(Constants.SESSION_LOCALE_SUBSTITUTE, locale);
		}
		if (customArgs != null) {
			result.put(Constants.SESSION_CUSTOMARGS_SUBSTITUTE, customArgs);
		}
		return result;
	}

	public static StrSubstitutor getConfigSubstitutor() {
		return getConfigSubstitutor(null, null, null, null, null);
	}

	public static StrSubstitutor getConfigSubstitutor(String user, String sessionId, String clientIp, String locale, String customArgs) {
		return new StrSubstitutor(getConfigSubstitutorMap(user, sessionId, clientIp, locale, customArgs));
	}

	public static void transferStreams(InputStream is, OutputStream os) throws IOException {
		try {
			byte[] buf = new byte[bufferSize];
			int bytesRead;
			while ((bytesRead = is.read(buf)) != -1)
				os.write(buf, 0, bytesRead);
		} finally {
			is.close();
			os.close();
		}
	}

	public static File getConfigFile() {
		String configFile = System.getProperty(Constants.CONFIG_FILE_PATH);
		if (configFile == null) {
			String war = CommonUtil.getWarFileLocation();
			configFile = war.substring(0, war.lastIndexOf("/") + 1) + Constants.DEFAULT_CONFIG_FILE_NAME;
			System.setProperty(configFile, Constants.CONFIG_FILE_PATH);
		}
		File config = new File(URI.create(configFile));
		return config;
	}

	public static String getClassPath(List<String> classpath) {
		StrSubstitutor subs = CommonUtil.getConfigSubstitutor();
		String cp = CommonUtil.generateClassPathString(classpath);
		return subs.replace(cp);
	}

	public static String generateClassPathString(Collection<String> classPathEntries) {
		String result = "";
		if (classPathEntries != null) {
			for (String cpe : classPathEntries) {
				result += cpe + ";";
			}
			result = result.length() > 0 ? result.substring(0, result.length() - 1) : result;
		}
		return result;
	}

	public static boolean isSubPath(String subpath, String path) {
		return path.equals(subpath) || path.startsWith(subpath + "/");
	}

	public static String toPath(String path) {
		String mapping = path == null ? "/" : path;
		mapping = mapping.startsWith("/") ? mapping : ("/" + mapping);
		mapping = mapping.endsWith("/") ? mapping.substring(0, mapping.length() - 1) : mapping;
		return mapping;
	}

	public static <T extends Annotation> T findAnnotation(Method readMethod, Class<T> ann) {
		T annotation = readMethod.getAnnotation(ann);
		if (annotation != null) {
			return annotation;
		} else {
			Set<Method> overrideHierarchy = MethodUtils.getOverrideHierarchy(readMethod, Interfaces.INCLUDE);
			for (Method m : overrideHierarchy) {
				annotation = m.getAnnotation(ann);
				if (annotation != null) {
					return annotation;
				}
			}
		}
		return null;
	}

}