package de.ahu.opendata.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import de.ahu.opendata.OpenDataNrw.FileDTO;
import elemental.json.JsonException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {

	private static Notification hinweisBox;

	public static void showErrorBox(String message) {
		showHinweisBox(message, 5000, NotificationVariant.LUMO_ERROR);
	}

	public static void showErrorBox(String message, int duration) {
		showHinweisBox(message, duration, NotificationVariant.LUMO_ERROR);
	}

	public static void showWarnBox(String message) {
		showHinweisBox(message, 5000, NotificationVariant.LUMO_WARNING);
	}

	public static void showSuccessBox(String message) {
		showHinweisBox(message, 5000, NotificationVariant.LUMO_SUCCESS);
	}

	public static void showNeutralBox(String message) {
		showHinweisBox(message, 5000, NotificationVariant.LUMO_CONTRAST);
	}

	public static void showHinweisBox(String message) {
		showHinweisBox(message, 5000, NotificationVariant.LUMO_PRIMARY);
	}

	public static void showHinweisBox(String message, int duration) {
		showHinweisBox(message, duration, NotificationVariant.LUMO_PRIMARY);
	}

	public static void showHinweisBox(String message, NotificationVariant variant) {
		showHinweisBox(message, 5000, variant);
	}

	public static void showHinweisBox(String message, int duration, NotificationVariant variant) {
		showHinweisBox(message, duration, variant, false);
	}

	public static void showHinweisBox(String message, int duration, NotificationVariant variant, boolean useHtml) {
		if (hinweisBox != null) {
			hinweisBox.close();
		}
		if (useHtml) {
			Div content = new Div();
			content.getElement().setProperty("innerHTML", message);
			hinweisBox = new Notification(content);
			hinweisBox.setDuration(duration);
		} else {
			hinweisBox = new Notification((StringUtils.isNotBlank(message)) ? message : "", duration);
		}
		hinweisBox.addThemeVariants(variant);
		hinweisBox.open();
		hinweisBox.addDetachListener(event -> hinweisBox = null);
	}

	public static String getStringFromAnyType(JSONObject jsonObject, String targetParameter) {
		if (jsonObject == null || targetParameter == null) {
			return null;
		}
		String result = "";
		Object object = jsonObject.get(targetParameter);
		if (object instanceof String) {
			result = jsonObject.getString(targetParameter);
		} else if (object instanceof Integer) {
			result = String.valueOf(jsonObject.getInt(targetParameter));
		} else if (object instanceof Long) {
			result = String.valueOf(jsonObject.getLong(targetParameter));
		} else if (object instanceof Double) {
			result = String.valueOf(jsonObject.getDouble(targetParameter));
		} else if (object instanceof BigInteger) {
			result = String.valueOf(jsonObject.getBigInteger(targetParameter));
		} else if (object instanceof BigDecimal) {
			result = String.valueOf(jsonObject.getBigDecimal(targetParameter));
		} else if (object instanceof Number) {
			result = String.valueOf(jsonObject.getNumber(targetParameter));
		} else if (object instanceof Boolean) {
			result = String.valueOf(jsonObject.getBoolean(targetParameter));
		}
		return result;
	}

	public static byte[] readEntryData(ZipInputStream zis, long entrySize) throws IOException {
		ByteArrayOutputStream baos = entrySize > 0 && entrySize <= Integer.MAX_VALUE
				? new ByteArrayOutputStream((int) entrySize)
				: new ByteArrayOutputStream(8192);

		byte[] buffer = new byte[8192];
		int bytesRead;
		long totalBytes = 0;
		long maxSize = 512 * 1024 * 1024;
		while ((bytesRead = zis.read(buffer)) != -1) {
			totalBytes += bytesRead;
			if (totalBytes > maxSize) {
				throw new IOException("Entry size exceeds maximum allowed size of 512MB");
			}
			baos.write(buffer, 0, bytesRead);
		}

		return baos.toByteArray();
	}

	public static ByteArrayOutputStream readByteArrayOutputStream(ZipInputStream zis, long entrySize) {
		ByteArrayOutputStream baos = entrySize > 0 && entrySize <= Integer.MAX_VALUE
				? new ByteArrayOutputStream((int) entrySize)
				: new ByteArrayOutputStream(8192);

		byte[] buffer = new byte[8192];
		int bytesRead;
		long totalBytes = 0;
		long maxSize = 512 * 1024 * 1024;

		try {
			while ((bytesRead = zis.read(buffer)) != -1) {
				totalBytes += bytesRead;
				if (totalBytes > maxSize) {
					throw new IOException("Entry size exceeds maximum allowed size of 512MB");
				}
				baos.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return baos;
	}

	public static String encodeUTF8(String input) {
		byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public static String[] fastSplit(String str, char separator) {
		List<String> result = new ArrayList<>();
		int len = str.length();
		int start = 0;
		for (int i = 0; i < len; i++) {
			if (str.charAt(i) == separator) {
				result.add(str.substring(start, i));
				start = i + 1;
			}
		}
		result.add(str.substring(start));
		return result.toArray(new String[0]);
	}

	public static File downloadZipToFile(String urlStr) {
		URL url = null;
		try {
			url = URI.create(urlStr).toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		File tempFile = null;
		try {
			tempFile = File.createTempFile("downloadedZip", ".zip");
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (InputStream in = url.openStream(); OutputStream out = new FileOutputStream(tempFile)) {
			byte[] buffer = new byte[65536];
			int bytesRead;
			try {
				while ((bytesRead = in.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return tempFile;
	}

	public static Map<String, Integer> mapHeaderIndexes(String headerLine) {
		Map<String, Integer> map = new HashMap<>();
		String[] headers = fastSplit(headerLine, ';');
		for (int i = 0; i < headers.length; i++) {
			map.put(headers[i].trim().toLowerCase(), i);
		}
		return map;
	}

	public static String extractTimeWithoutOffset(String timestamp) {
		try {
			String[] parts = timestamp.split("T");
			if (parts.length != 2) {
				throw new Exception("Invalid timestamp format");
			}
			String timeString = parts[1];
			return timeString.substring(0, 5);
		} catch (Exception e) {
			System.out.println("Error parsing timestamp: " + e.getMessage());
			return "";
		}
	}

	public static List<String> extractQueryParams(String url) {
		String query;
		try {
			query = new URI(url).getQuery();
			Map<String, String> queryParams = Arrays.stream(query.split("&")).map(param -> param.split("=", 2))
					.collect(Collectors.toMap(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
							parts -> parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : ""));

			String fieldsParam = queryParams.get("fields");

			return fieldsParam != null ? Arrays.asList(fieldsParam.split(",")) : List.of();

		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return List.of();
	}

	public static String extractBaseUrl(String urlString) {
		int lastSlashIndex = urlString.lastIndexOf('/');
		if (lastSlashIndex > 0) {
			return urlString.substring(0, lastSlashIndex + 1);
		}
		return urlString;
	}

	public static String extractAndFormatFileName(String fileName) {
		String filenameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
		String[] words = filenameWithoutExtension.split("_");
		StringBuilder result = new StringBuilder();

		for (String word : words) {
			if (word.length() > 0) {
				result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
			}
		}
		return result.toString().trim();
	}

	public static String getFileName(String url) {
		return url.substring(url.lastIndexOf('/') + 1);
	}

	public static boolean isFileOutdated(File file) {
		Instant lastModified = Instant.ofEpochMilli(file.lastModified());
		Instant now = Instant.now();
		return Duration.between(lastModified, now).toHours() >= 24;
	}

	public static String extractFilenameWithoutExtension(String urlString) {
		int lastSlashIndex = urlString.lastIndexOf('/');
		String filenameWithExtension = "";

		if (lastSlashIndex >= 0 && lastSlashIndex < urlString.length() - 1) {
			filenameWithExtension = urlString.substring(lastSlashIndex + 1);
		} else {
			filenameWithExtension = urlString;
		}

		int lastDotIndex = filenameWithExtension.lastIndexOf('.');
		if (lastDotIndex > 0) {
			return filenameWithExtension.substring(0, lastDotIndex);
		}
		return filenameWithExtension;
	}

	public static String omitMsFromString(String dateString) {
		String[] parts = dateString.split("\\+");
		if (parts.length == 2) {
			return parts[0];
		}
		return null;
	}

	public static String convertUppCaseToLowerCase(String input) {
		if (input == null) {
			return null;
		}
		return input.substring(0, 1) + input.substring(1).toLowerCase();
	}

	public static double roundUpNumber(double value) {
		DecimalFormat decimalFormat = new DecimalFormat("#.00");

		String formattedDistance = decimalFormat.format(value);

		try {
			return DecimalFormat.getNumberInstance().parse(formattedDistance).doubleValue();
		} catch (ParseException e) {
			e.printStackTrace();
			return value;
		}
	}

	public static String mapStatusValue(String germanStatus) {
		switch (germanStatus) {
		case "mittel":
			return "normal";
		case "niedrig":
			return "low";
		case "hoch":
			return "high";
		default:
			return "unknown";
		}
	}

	public static String extractFileName(FileDTO file) {
		String url = file.getUrl();
		return url != null ? url.substring(url.lastIndexOf('/') + 1) : file.getPath();
	}

	public static String xmlToJson(String inputXml, Integer indentFactor) {
		int indent = (indentFactor == null) ? 4 : indentFactor;
		String jsonPrettyPrintString = null;
		try {
			JSONObject xmlJSONObj = XML.toJSONObject(inputXml);
			jsonPrettyPrintString = xmlJSONObj.toString(indent);
		} catch (JsonException je) {
			log.error(je.toString());
			return jsonPrettyPrintString;
		}
		return jsonPrettyPrintString;
	}

	public static Map<String, Integer> sortedDescendingMapByValue(Map<String, Integer> map) {
		return map.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	public static <T> T convertStringToJson(String data, Class<T> clazz) {
		try {
			if (clazz == JSONArray.class) {
				return clazz.cast(new JSONArray(data));
			} else if (clazz == JSONObject.class) {
				return clazz.cast(new JSONObject(data));
			} else {
				throw new IllegalArgumentException("Unsupported JSON type: " + clazz.getSimpleName());
			}
		} catch (JSONException e) {
			log.warn("Fehler bei der Verarbeitung der Wetterdaten (Mapping): " + e.getMessage());
			return null;
		}
	}

	public static String formatCustomIsoDate(String dataString) {
		if (dataString == null) {
			return null;
		}

		try {
			LocalDate date = LocalDate.parse(dataString, DateTimeFormatter.ISO_DATE_TIME);

			DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("MM.dd.yyyy");

			return date.format(customFormatter);
		} catch (DateTimeParseException e) {
			log.error("Error parsing date string: " + e.getMessage());
			return "";
		}
	}

	public static String formatToGermanDate(String dateString) {
		if (dateString == null) {
			return null;
		}
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
			Date date = formatter.parse(dateString);

			SimpleDateFormat germanFormatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
			return germanFormatter.format(date);
		} catch (ParseException e) {
			log.error("Error parsing date string: " + e.getMessage());
			return "";
		}
	}

	public static String formatDateOnly(String isoTimestamp) {
		if (isoTimestamp == null || isoTimestamp.isEmpty()) {
			return "";
		}
		int tIndex = isoTimestamp.indexOf('T');
		if (tIndex > 0) {
			return isoTimestamp.substring(0, tIndex);
		}

		return isoTimestamp;
	}

	public static String formatDateTime(String dateTimeString) {

		OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateTimeString);
		LocalDateTime localDateTime = offsetDateTime.toLocalDateTime();
		DateTimeFormatter germanDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN);
		String formattedDateTime = localDateTime.format(germanDateTimeFormatter);

		return formattedDateTime;
	}

	public static String formateToGermanDate(String date) {
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		DateTimeFormatter germanFormatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
		LocalDateTime localDateTime = LocalDateTime.parse(date, inputFormatter);
		return localDateTime.format(germanFormatter);

	}

	public static LocalDate parseGermanDate(String date) {
		DateTimeFormatter formatter = null;
		if (date.length() > 10) {
			formatter = DateTimeFormatter.ISO_DATE_TIME;
		} else {
			formatter = DateTimeFormatter.ISO_DATE;
		}
		try {
			return LocalDate.from(formatter.parse(date));
		} catch (DateTimeParseException e) {
			log.error("Error parsing date string: " + e.getMessage());
			return null;
		}
	}

	public static MediaType getMediaType(String fileFormat) {
		switch (fileFormat) {
		case ".json":
			return MediaType.APPLICATION_JSON;
		case ".csv":
			return MediaType.TEXT_PLAIN;
		case ".txt":
			return MediaType.TEXT_PLAIN;
		default:
			return MediaType.TEXT_PLAIN;
		}
	}

	public static String serializeMapToJSON(Map<String, List<String>> selectedColumns) {
		ObjectMapper objectMapper = new ObjectMapper();
		String columnsJson;
		try {
			columnsJson = objectMapper.writeValueAsString(selectedColumns);
		} catch (JsonProcessingException e) {
			columnsJson = "";
			e.printStackTrace();
		}
		return URLEncoder.encode(columnsJson, StandardCharsets.UTF_8);
	}

	public static String removeSquareBrackets(String input) {
		if (input == null) {
			return null;
		}
		return input.replace("[", "").replace("]", "");
	}

	public static List<String> parseCsvContent(String csvContent) {
		List<String> lines = new ArrayList<>();
		String[] splitLines = csvContent.split("\n");
		for (String line : splitLines) {
			if (!line.trim().isEmpty()) {
				lines.add(line.trim());
			}
		}
		return lines;
	}

	public static ByteArrayOutputStream writeToByteArray(List<String> lines) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
			for (String line : lines) {
				writer.println(line);
			}
		}
		return baos;
	}

	public static Map<String, List<String>> parseStringToMap(String input) {
		if (input == null || input.isEmpty()) {
			return new HashMap<>();
		}

		Map<String, List<String>> result = new HashMap<>();

		String content = input.trim();
		if (content.startsWith("{")) {
			content = content.substring(1);
		}
		if (content.endsWith("}")) {
			content = content.substring(0, content.length() - 1);
		}

		// Split by comma that isn't inside square brackets
		String[] entries = content.split("(?<=\\]), ");

		for (String entry : entries) {
			// Find the position of the equals sign
			int equalsPos = entry.indexOf('=');
			if (equalsPos > 0) {
				// Extract the key (filename)
				String key = entry.substring(0, equalsPos).trim();

				// Extract the value (list of column names)
				String listStr = entry.substring(equalsPos + 1).trim();

				if (listStr.startsWith("[")) {
					listStr = listStr.substring(1);
				}
				if (listStr.endsWith("]")) {
					listStr = listStr.substring(0, listStr.length() - 1);
				}

				// Split the list by commas and convert to List<String>
				List<String> values = Arrays.asList(listStr.split(", "));

				result.put(key, values);
			}
		}

		return result;
	}
}
