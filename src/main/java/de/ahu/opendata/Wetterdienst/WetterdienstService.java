package de.ahu.opendata.Wetterdienst;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.vaadin.flow.server.StreamResource;

import de.ahu.opendata.DataUtils.RestDTO;
import de.ahu.opendata.ServiceUtils.GeolocationService;
import de.ahu.opendata.ServiceUtils.ParameterPathFinderService;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WetterdienstService {

	@Value("${wetterdienst.url}")
	private String wetterdienstBaseUrl;

	@Autowired
	private GeolocationService geolocationService;

	private List<WetterStationDTO> allWetterstationen;

	private List<WetterDatenDTO> allWetterdaten;

	private WetterStationDTO stationWithValues;

	private Map<WetterParameter, List<WetterStationDTO>> stationMap = new HashMap<>();

	public List<WetterDatenDTO> fetchWetterDaten(String url) {
		if (WebUtils.isServiceOnline(url)) {
			String fetchData = WebUtils.fetchContentSimpleWebClient(url);
			if (fetchData == null) {
				log.warn("Der Abruf lieferte keine Wetterstationen zurück.");
				return List.of();
			}
			JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);

			JSONArray stationsArray = jsonObject.getJSONArray("stations");
			JSONObject stationJson = stationsArray.getJSONObject(0);
			stationWithValues = extractStation(stationJson);
			JSONArray jsonFeatures = jsonObject.has("values") ? jsonObject.getJSONArray("values") : null;
			allWetterdaten = new ArrayList<WetterDatenDTO>();

			for (int i = 0; i < jsonFeatures.length(); i++) {
				JSONObject feature = jsonFeatures.getJSONObject(i);
				String date = feature.has("date") ? feature.getString("date") : null;
				Double value = feature.has("value") ? feature.getDouble("value") : null;
				// Double quality = feature.has("quality") ? feature.getDouble("quality") : 0.0;

				WetterDatenDTO wetterDatenDTO = new WetterDatenDTO();

				wetterDatenDTO.setDate(date);
				wetterDatenDTO.setValue(value);
				// wetterDatenDTO.setQuality(quality);
				wetterDatenDTO.setStationId(feature.getString("station_id"));
				// wetterDatenDTO.setResolution(feature.getString("resolution"));
				wetterDatenDTO.setDatasest(feature.getString("dataset"));
				wetterDatenDTO.setParameter(feature.getString("parameter"));

				allWetterdaten.add(wetterDatenDTO);
			}
		}
		return allWetterdaten;
	}

	public List<WetterStationDTO> hasStationValues(String url, List<WetterStationDTO> allStationen) {
		List<WetterStationDTO> foundStationWithData = new ArrayList<>();
		String fetchData = WebUtils.fetchContentSimpleWebClient(url);
		if (fetchData == null) {
			log.warn("Keine Daten aus dem Link : {}", url);
			return foundStationWithData;
		}

		JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
		JSONArray values = jsonObject.optJSONArray("values");
		if (values != null && values.length() > 0) {
			Set<String> stationsWithData = new HashSet<>();
			for (int i = 0; i < values.length(); i++) {
				JSONObject value = values.getJSONObject(i);
				stationsWithData.add(value.getString("station_id"));
			}
			foundStationWithData = allStationen.stream()
					.filter(station -> stationsWithData.contains(station.getStationId())).collect(Collectors.toList());
		}
		return foundStationWithData;
	}

	public List<WetterStationDTO> searchAllStationsWithParameter(String keyParameter, boolean isObservation) {
		List<WetterStationDTO> allStations = Collections.synchronizedList(new ArrayList<>());
		Map<String, WetterStationDTO> stationMapString = new HashMap<>();

		if (isObservation && keyParameter != null) {
			List<String> paths = new ParameterPathFinderService().findAllParameterPaths(keyParameter);
			paths = paths.stream().filter(p -> !p.contains("minute")).collect(Collectors.toList());

			paths.stream().forEach(path -> {
				List<WetterStationDTO> stations = fetchWetterstationen(path, isObservation);
				if (!stations.isEmpty()) {
					stations.stream().forEach(station -> {
						String stationId = station.getStationId();
						WetterParameter parameter = new WetterParameter(path);

						stationMapString.computeIfAbsent(stationId, k -> {
							WetterStationDTO newStation = new WetterStationDTO();
							newStation.setStationId(station.getStationId());
							newStation.setName(station.getName());
							newStation.setState(station.getState());
							newStation.setHeight(station.getHeight());
							newStation.setLatitude(station.getLatitude());
							newStation.setLongitude(station.getLongitude());
							newStation.setResolution(station.getResolution());
							newStation.setDataset(station.getDataset());
							newStation.setStartDate(station.getStartDate());
							newStation.setEndDate(station.getEndDate());
							return newStation;
						}).addPathParameter(parameter);
					});
				}
			});
		} else if (!isObservation && keyParameter != null) {
			List<WetterParameter> wetterParameterVorhersage = loadMosmixParameterList();
			List<WetterParameter> filteredParameters = wetterParameterVorhersage.stream()
					.filter(wetterParameter -> wetterParameter.getName().contains(keyParameter))
					.collect(Collectors.toList());

			if (filteredParameters.isEmpty()) {
				stationMap.clear();
				return Collections.emptyList();
			}
			geolocationService.initializeGermanGeometry();
			List<WetterStationDTO> allStationInGermany = fetchAndFilterGermanStations(
					geolocationService.germanyGeometry());
			Set<String> germanStationSet = allStationInGermany.stream().map(WetterStationDTO::getStationId)
					.collect(Collectors.toSet());
			stationMap.clear();
			if (!filteredParameters.isEmpty()) {
				filteredParameters.stream().forEach(wetterParameter -> {
					List<WetterStationDTO> stations = fetchWetterstationen(wetterParameter.getOriginalName(), false);

					List<WetterStationDTO> filteredStations = stations.stream()
							.filter(station -> germanStationSet.contains(station.getStationId()))
							.collect(Collectors.toList());

					if (!filteredStations.isEmpty()) {
						filteredStations.get(0).setUnit(wetterParameter.getUnit());
						filteredStations.stream().forEach(station -> {
							station.addPathParameter(new WetterParameter(wetterParameter.getOriginalName()));
							station.setProvider("Wetterdienst-Vorhersage");
							station.setIsForecast(Boolean.TRUE);
						});
						stationMap.put(wetterParameter, filteredStations);
					}
				});

			}
		}

		String currentYear = String.valueOf(OffsetDateTime.now().getYear());

		allStations = stationMapString.values().stream()
				.filter(station -> station.getEndDate() != null && station.getEndDate().contains(currentYear))
				.distinct().collect(Collectors.toList());

		return allStations;
	}

	public Map<WetterParameter, List<WetterStationDTO>> getStationWithForecastParameter() {
		return stationMap;
	}

	@Cacheable(value = "wetterstationen", key = "#parameters + '-' + #isObservation")
	public List<WetterStationDTO> fetchWetterstationen(String parameters, boolean isObservation) {
		String getAllStationsUrl = "";
		if (isObservation) {
			getAllStationsUrl = wetterdienstBaseUrl + "stations?provider=dwd&network=observation&parameters="
					+ parameters + "&periods=historical&all=true";
		} else {
			getAllStationsUrl = wetterdienstBaseUrl + "stations?provider=dwd&network=mosmix&parameters=hourly/large/"
					+ parameters + "&all=true";
		}
		allWetterstationen = new ArrayList<WetterStationDTO>();
		try {
			if (!WebUtils.isServiceOnline(getAllStationsUrl)) {
				return Collections.emptyList();
			}

			String fetchData = WebUtils.fetchContentSimpleWebClient(getAllStationsUrl);
			if (fetchData == null) {
				log.warn("Der Abruf lieferte keine Wetterstationen zurück.");
				return Collections.emptyList();
			}

			JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
			JSONArray jsonFeatures = jsonObject.has("stations") ? jsonObject.getJSONArray("stations") : null;

			if (jsonFeatures != null) {
				for (int i = 0; i < jsonFeatures.length(); i++) {
					JSONObject feature = jsonFeatures.getJSONObject(i);
					allWetterstationen.add(extractStation(feature));
				}
			}
		} catch (Exception e) {
			WebUtils.handleWebClientException(getAllStationsUrl, e);
			return Collections.emptyList();
		}
		return allWetterstationen;
	}

	public List<WetterStationDTO> fetchAndFilterGermanStations(Geometry germanGeometry) {
		List<WetterStationDTO> allStations = fetchWetterstationen("", false);
		if (germanGeometry == null) {
			throw new IllegalStateException("German geometry not initialized");
		}
		GeometryFactory factory = new GeometryFactory();
		return allStations.stream().filter(station -> {
			Double lat = station.getLatitude();
			Double lon = station.getLongitude();
			if (lat == null || lon == null)
				return false;
			Point point = factory.createPoint(new Coordinate(lon, lat));
			return germanGeometry.contains(point);
		}).collect(Collectors.toList());
	}

	private WetterStationDTO extractStation(JSONObject feature) {
		String stationId;
		WetterStationDTO wetterStationDTO = new WetterStationDTO();
		try {
			stationId = feature.has("station_id") ? feature.getString("station_id") : null;
			String stationName = feature.has("name") ? feature.getString("name") : null;
			String stationState = (feature.has("state") && !feature.isNull("state")) ? feature.getString("state")
					: null;
			Double latitude = feature.has("latitude") ? feature.getDouble("latitude") : null;
			Double longitude = feature.has("longitude") ? feature.getDouble("longitude") : null;
			Double height = feature.has("height") ? feature.getDouble("height") : null;

			String resolution = feature.has("resolution") ? feature.getString("resolution") : null;
			String dataset = feature.has("dataset") ? feature.getString("dataset") : null;

			if (feature.has("start_date")) {
				var startDate = feature.get("start_date") != null ? feature.get("start_date") : "";
				wetterStationDTO.setStartDate(startDate.toString());
			}
			if (feature.has("end_date")) {
				var endDate = feature.get("end_date") != null ? feature.get("end_date") : "";
				wetterStationDTO.setEndDate(endDate.toString());
			}

			wetterStationDTO.setStationId(stationId);
			wetterStationDTO.setName(stationName);
			wetterStationDTO.setState(stationState);
			wetterStationDTO.setLatitude(latitude);
			wetterStationDTO.setLongitude(longitude);
			wetterStationDTO.setHeight(height);
			wetterStationDTO.setResolution(resolution);
			wetterStationDTO.setDataset(dataset);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return wetterStationDTO;
	}

	public StreamResource createCsvStreamResource(List<WetterDatenDTO> wetterDaten) {
		try {
			StringWriter writer = new StringWriter();

			writer.write("station_id,station_name,latitude,longitude,date,value,parameter\n");

			for (WetterDatenDTO data : wetterDaten) {
				String value = data.getParameter().contains("temperature") ? String.valueOf(data.convertToCelsius())
						: String.valueOf(data.getValue());
				String line = String.join(",", stationWithValues.getStationId(), stationWithValues.getName(),
						String.valueOf(stationWithValues.getLatitude()),
						String.valueOf(stationWithValues.getLongitude()), data.getDate(), value, data.getParameter())
						+ "\n";
				writer.write(line);
			}
			writer.close();

			return new StreamResource("wetterdaten.csv", () -> {
				return new ByteArrayInputStream(writer.toString().getBytes());
			});
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public ParameterNode parse() {
		try (InputStream inputStream = this.getClass().getResourceAsStream("/parameter_list.txt")) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				List<String> lines = reader.lines().collect(Collectors.toList());
				return parse(lines);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private ParameterNode parse(List<String> lines) {
		ParameterNode root = new ParameterNode("root", -1);
		Deque<ParameterNode> stack = new ArrayDeque<>();
		stack.push(root);

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty())
				continue;

			int currentIndent = line.indexOf(trimmedLine);
			while (stack.size() > 1 && stack.peek().getIndent() >= currentIndent) {
				stack.pop();
			}
			ParameterNode parent = stack.peek();
			ParameterNode currentNode = new ParameterNode(trimmedLine, currentIndent);
			parent.getChildren().add(currentNode);
			stack.push(currentNode);
		}

		return root;
	}

	public List<WetterParameter> loadMosmixParameterList() {
		List<WetterParameter> dataList = new ArrayList<>();
		try (InputStream inputStream = this.getClass().getResourceAsStream("/mosmix_parameter_german_list.txt");
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
				CSVReader csvReader = new CSVReader(reader)) {

			String[] line;
			csvReader.readNext();
			while ((line = csvReader.readNext()) != null) {
				dataList.add(new WetterParameter(line[0], line[1], line[2], line[3], line[4],
						line.length > 5 ? line[5] : ""));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dataList;
	}

	public <T extends RestDTO> String formatWetterDaten(List<T> wetterDaten, String dateiFormat) {
		switch (dateiFormat) {
		case ".json":
			return toJson(wetterDaten);
		case ".csv":
			return toCsv(wetterDaten);
		case ".txt":
			return toTxt(wetterDaten);
		default:
			throw new IllegalArgumentException("Unsupported file format: " + dateiFormat);
		}
	}

	private <T extends RestDTO> String toJson(List<T> wetterDaten) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.writeValueAsString(wetterDaten);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to convert to JSON", e);
		}
	}

	private <T extends RestDTO> String toCsv(List<T> wetterDaten) {
		if (wetterDaten.isEmpty())
			return "";

		StringBuilder csv = new StringBuilder();
		List<String> headers = getNonNullFieldNames(wetterDaten);
		csv.append(String.join(",", headers)).append("\n");

		for (T dto : wetterDaten) {
			List<String> values = new ArrayList<>();
			for (String field : headers) {
				try {
					Field f = getField(dto.getClass(), field);
					f.setAccessible(true);
					Object value = f.get(dto);
					values.add(value != null ? value.toString() : "");
				} catch (Exception e) {
					values.add("");
				}
			}
			csv.append(String.join(",", values)).append("\n");
		}
		return csv.toString();
	}

	private <T extends RestDTO> String toTxt(List<T> wetterDaten) {
		if (wetterDaten.isEmpty())
			return "";
		List<String> headers = getNonNullFieldNames(wetterDaten);
		StringBuilder txt = new StringBuilder();

		for (T dto : wetterDaten) {
			List<String> parts = new ArrayList<>();
			for (String field : headers) {
				try {
					Field f = getField(dto.getClass(), field);
					f.setAccessible(true);
					Object value = f.get(dto);
					parts.add(capitalize(field) + ": " + (value != null ? value.toString() : ""));
				} catch (Exception e) {
				}
			}
			txt.append(String.join(", ", parts)).append("\n");
		}
		return txt.toString();
	}

	private <T extends RestDTO> List<String> getNonNullFieldNames(List<T> wetterDaten) {
		if (wetterDaten.isEmpty())
			return Collections.emptyList();
		Map<String, Boolean> fieldHasValue = new LinkedHashMap<>();
		Class<?> clazz = wetterDaten.get(0).getClass();
		while (clazz != null) {
			for (Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				String fieldName = field.getName();
				fieldHasValue.put(fieldName, false);
				for (T dto : wetterDaten) {
					try {
						Object value = field.get(dto);
						if (value != null) {
							fieldHasValue.put(fieldName, true);
							break;
						}
					} catch (IllegalAccessException ignored) {
					}
				}
			}
			clazz = clazz.getSuperclass();
		}
		return fieldHasValue.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	private Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		throw new NoSuchFieldException(fieldName);
	}

	private String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
