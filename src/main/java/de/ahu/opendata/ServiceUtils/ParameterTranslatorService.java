package de.ahu.opendata.ServiceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

@Service
public class ParameterTranslatorService {

	private static final Map<String, String> TRANSLATIONS = new HashMap<>();
	private static Map<String, String> SORT_TRANSLATIONS = null;

	static {
		Properties properties = new Properties();
		try (InputStream is = ParameterTranslatorService.class.getResourceAsStream("/translations.properties")) {
			properties.load(is);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load translations", e);
		}

		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
			TRANSLATIONS.put(key, value);
		}

		SORT_TRANSLATIONS = new TreeMap<>(TRANSLATIONS);
	}

	public static String translateToGerman(String englishName) {
		return SORT_TRANSLATIONS.getOrDefault(englishName, englishName);
	}

	public static String translateToEnglish(String germanName) {
		for (Map.Entry<String, String> entry : SORT_TRANSLATIONS.entrySet()) {
			if (entry.getValue().contains(germanName)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static List<String> getTranslateListToEnglisch(String germanName) {
		List<String> keys = new ArrayList<>();
		for (Map.Entry<String, String> entry : SORT_TRANSLATIONS.entrySet()) {
			if (entry.getValue().contains(germanName)) {
				keys.add(entry.getKey());
			}
		}
		return keys;
	}

}
