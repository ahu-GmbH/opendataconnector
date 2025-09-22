package de.ahu.opendata.Pegeldienst;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WasserstandService {

	@Value("${pegeldienst.url}")
	private String pegeldienstBaseUrl;

	public String historicalDataUrl = "";

	public String forecastDataUrl = "";

	public List<WasserstandDTO> fetchVorhersageData(String stationId) {
		StringBuilder forecastStationUrl = new StringBuilder(pegeldienstBaseUrl + "/" + stationId);
		forecastStationUrl.append("/WV/measurements.csv?contentType=text/plain");
		List<WasserstandDTO> result = new ArrayList<>();
		forecastDataUrl = forecastStationUrl.toString();
		if (WebUtils.isServiceOnline(forecastDataUrl)) {
			String csvData = WebUtils.fetchRemoteText(forecastDataUrl);

			if (csvData != null) {
				String[] lines = csvData.split("\n");
				for (int i = 1; i < lines.length; i++) {
					String line = lines[i].trim();
					if (line.isEmpty())
						continue;
					String[] fields = line.split(";");

					if (fields.length < 12)
						continue;

					try {
						String initialized = fields[0];
						String timestamp = fields[1];
						String valueStr = fields[2];

						valueStr = valueStr.replace(',', '.');
						double value = valueStr.isEmpty() ? 0 : Double.parseDouble(valueStr);

						WasserstandDTO wasserstandDTO = new WasserstandDTO();
						wasserstandDTO.setTimespanStart(initialized);
						wasserstandDTO.setTimespanEnd(timestamp);
						wasserstandDTO.setCurrentMeasuredValue(value);

						result.add(wasserstandDTO);
					} catch (NumberFormatException e) {
						log.info("Error parsing line " + i + ": " + e.getMessage());
						continue;
					}
				}

			}
		}
		return result;
	}

	public List<WasserstandDTO> fetchHistoricalData(String stationId) {
		historicalDataUrl = pegeldienstBaseUrl + "/" + stationId + "/W/measurements.json?start=P8D";
		List<WasserstandDTO> historicalList = new ArrayList<WasserstandDTO>();
		if (WebUtils.isServiceOnline(historicalDataUrl)) {
			String data = WebUtils.fetchRemoteText(historicalDataUrl);
			if (data == null) {
				return historicalList;
			}
			JSONArray jsonArray = Utils.convertStringToJson(data, JSONArray.class);

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject feature = jsonArray.getJSONObject(i);

				String timeStamp = feature.has("timestamp") ? feature.getString("timestamp") : null;
				Double value = feature.has("value") ? feature.getDouble("value") : null;

				WasserstandDTO wasserstandDto = new WasserstandDTO();
				wasserstandDto.setTimeStamp(timeStamp);
				wasserstandDto.setCurrentMeasuredValue(value);

				historicalList.add(wasserstandDto);
			}
		}

		return historicalList;
	}

	public List<PegelStationDTO> findAndPegelStation() {
		List<PegelStationDTO> allPegelStations = new ArrayList<>();
		allPegelStations = fetchPegelStationWithHistoricalData();
		allPegelStations.forEach(station -> {
			station.setProvider("https://www.pegelonline.wsv.de/gast/start");
			station.setIsHistorical(Boolean.TRUE);
		});

		List<PegelStationDTO> forecastStations = fetchPegelStationWithForecastData();
		Map<String, PegelStationDTO> stationMap = new HashMap<>();
		allPegelStations.forEach(station -> stationMap.put(station.getId(), station));

		for (PegelStationDTO forecastStation : forecastStations) {
			forecastStation.setProvider("https://www.pegelonline.wsv.de/gast/start");
			forecastStation.setIsForecast(Boolean.TRUE);

			if (stationMap.containsKey(forecastStation.getId())) {
				stationMap.get(forecastStation.getId()).setIsForecast(Boolean.TRUE);
			} else {
				stationMap.put(forecastStation.getId(), forecastStation);
			}
		}

		return new ArrayList<>(stationMap.values());
	}

	public List<PegelStationDTO> fetchPegelStationWithForecastData() {
		String url = pegeldienstBaseUrl
				+ ".json?includeTimeseries=true&hasTimeseries=WV&includeForecastTimeseries=true&includeCurrentMeasurement=true&includeCharacteristicValues=true";
		return fetchPegelStations(url, true);
	}

	public List<PegelStationDTO> fetchPegelStationWithHistoricalData() {
		String url = pegeldienstBaseUrl
				+ ".json?includeTimeseries=true&includeCurrentMeasurement=true&includeCharacteristicValues=true";
		return fetchPegelStations(url, false);
	}

	@Cacheable(value = "pegelstation", key = "#url + '-' + #includeForecast")
	private List<PegelStationDTO> fetchPegelStations(String url, boolean includeForecast) {
		List<PegelStationDTO> allPegelStations = new ArrayList<>();

		if (!WebUtils.isServiceOnline(url)) {
			return allPegelStations;
		}
		String fetchData = WebUtils.fetchRemoteText(url);
		JSONArray jsonArray = Utils.convertStringToJson(fetchData, JSONArray.class);

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject feature = jsonArray.getJSONObject(i);
			PegelStationDTO pegelStationDTO = extractStationBaseInfo(feature);
			if (pegelStationDTO == null)
				continue;
			allPegelStations.add(pegelStationDTO);

			if (feature.has("timeseries") && feature.get("timeseries") instanceof JSONArray) {
				JSONArray timeSeriesArray = feature.getJSONArray("timeseries");

				for (int j = 0; j < timeSeriesArray.length(); j++) {
					JSONObject jsonObject = timeSeriesArray.getJSONObject(j);

					WasserstandDTO wasserstandDTO = extractWasserstand(jsonObject);
					if (wasserstandDTO != null) {
						pegelStationDTO.setHasWasserstand(wasserstandDTO);
						processCharacteristicValues(jsonObject, wasserstandDTO, pegelStationDTO);
						processCommentsAndStatus(jsonObject, wasserstandDTO, pegelStationDTO);
					}

					if (includeForecast && jsonObject.optString("shortname").equals("WV")) {
						PegelstandDaten forecastDaten = new PegelstandDaten();
						forecastDaten.setShortname(jsonObject.optString("shortname"));
						forecastDaten.setLongname("WASSERSTANDVORHERSAGE");
						forecastDaten.setEquidistance(jsonObject.optDouble("equidistance"));
						forecastDaten.setUnit(jsonObject.optString("unit"));
						forecastDaten.setStart(jsonObject.optString("start"));
						forecastDaten.setEnd(jsonObject.optString("end"));

						pegelStationDTO.setPegelstandTimeSeries(forecastDaten);
					}
				}
			}

		}
		return allPegelStations;
	}

	private PegelStationDTO extractStationBaseInfo(JSONObject feature) {
		String uuId = feature.optString("uuid", "");
		String longName = feature.optString("longname", "");
		Double length = feature.optDouble("km", 0.0);
		String agency = feature.optString("agency", "");
		Double latitude = feature.optDouble("latitude", 0.0);
		Double longitude = feature.optDouble("longitude", 0.0);

		if (latitude == 0.0 && longitude == 0.0) {
			return null;
		}

		PegelStationDTO dto = new PegelStationDTO();
		dto.setId(uuId);
		dto.setName(longName);
		dto.setLength(length);
		dto.setAgency(agency);
		dto.setLatitude(latitude);
		dto.setLongitude(longitude);

		return dto;
	}

	private WasserstandDTO extractWasserstand(JSONObject jsonObject) {
		String shortname = jsonObject.optString("shortname");

		if (!shortname.equals("W") && !shortname.equals("DFH")) {
			return null;
		}

		WasserstandDTO dto = new WasserstandDTO();
		dto.setEquidistance(jsonObject.optDouble("equidistance"));

		JSONObject currentMeasurement = jsonObject.optJSONObject("currentMeasurement");
		if (currentMeasurement == null) {
			return dto;
		}

		dto.setTimeStamp(currentMeasurement.optString("timestamp", null));
		dto.setCurrentMeasuredValue(currentMeasurement.has("value") ? currentMeasurement.optDouble("value") : null);
		dto.setStateMnwMhw(currentMeasurement.optString("stateMnwMhw", null));
		dto.setStateNswHsw(currentMeasurement.optString("stateNswHsw", null));

		return dto;
	}

	private void processCharacteristicValues(JSONObject jsonObject, WasserstandDTO wasserstandDTO,
			PegelStationDTO pegelStationDTO) {
		JSONArray charValuesArray = jsonObject.optJSONArray("characteristicValues");
		if (charValuesArray == null)
			return;

		Map<String, Double> charValuesMap = new HashMap<>();
		for (int k = 0; k < charValuesArray.length(); k++) {
			JSONObject characteristicValue = charValuesArray.getJSONObject(k);
			String shortname = characteristicValue.optString("shortname");

			if (Arrays.asList("MHW", "MNW", "HHW", "HSW").contains(shortname)) {
				charValuesMap.put(shortname, characteristicValue.optDouble("value"));
			}
		}

		wasserstandDTO.setHasCharacteristicValues(charValuesMap);

		Double currentValue = wasserstandDTO.getCurrentMeasuredValue();
		Double mnw = charValuesMap.get("MNW");
		Double mhw = charValuesMap.get("MHW");
		Double hsw = charValuesMap.get("HSW");

		if (currentValue == null) {
			pegelStationDTO.setPegelStatus("unknown");
			return;
		}

		if (isLow(currentValue, mnw)) {
			pegelStationDTO.setPegelStatus("low");
		} else if (isNormal(currentValue, mnw, mhw, hsw, wasserstandDTO.getStateMnwMhw())) {
			pegelStationDTO.setPegelStatus("normal");
		} else if (isHigh(currentValue, mhw, hsw)) {
			pegelStationDTO.setPegelStatus("high");
		} else if (mnw == null && mhw == null && hsw == null) {
			pegelStationDTO.setPegelStatus("normal");
		}
	}

	private void processCommentsAndStatus(JSONObject jsonObject, WasserstandDTO wasserstandDTO,
			PegelStationDTO pegelStationDTO) {
		if (isCommentedWithComment(wasserstandDTO, jsonObject)) {
			JSONObject comment = jsonObject.getJSONObject("comment");
			pegelStationDTO.setDescription(comment.optString("longDescription", ""));
			pegelStationDTO.setPegelStatus("unknown");
		}

		if (shouldSetNormal(wasserstandDTO, jsonObject)
				&& !Objects.equals(wasserstandDTO.getStateNswHsw(), "commented")) {
			pegelStationDTO.setPegelStatus("normal");
		}
	}

	public Map<LocalDateTime, Number> getPegelTimeseriesData(List<WasserstandDTO> pegelDaten) {
		Map<LocalDateTime, Number> pegelTimeseries = pegelDaten.stream()
				.collect(Collectors.toMap(dto -> LocalDateTime.parse(Utils.omitMsFromString(dto.getTimeStamp())),
						dto -> dto.getCurrentMeasuredValue(), (existing, replacement) -> existing, TreeMap::new));
		return pegelTimeseries;
	}

	private boolean isCommentedWithComment(WasserstandDTO dto, JSONObject jsonObject) {
		return Objects.equals(dto.getStateMnwMhw(), "commented") && jsonObject.has("comment");
	}

	private boolean shouldSetNormal(WasserstandDTO dto, JSONObject jsonObject) {
		boolean isMnwMhwUnknown = Objects.equals(dto.getStateMnwMhw(), "unknown");
		boolean isNswHswUnknown = Objects.equals(dto.getStateNswHsw(), "unknown");
		boolean charValuesEmpty = jsonObject.has("characteristicValues")
				&& jsonObject.getJSONArray("characteristicValues").isEmpty();
		boolean charValuesNotEmpty = !charValuesEmpty;

		return (isMnwMhwUnknown && isNswHswUnknown && charValuesEmpty)
				|| (dto.getStateMnwMhw() == null && dto.getStateNswHsw() == null && charValuesEmpty)
				|| (isMnwMhwUnknown && isNswHswUnknown && charValuesNotEmpty)
				|| Objects.equals(dto.getStateMnwMhw(), "normal");
	}

	private boolean isLow(Double current, Double mnw) {
		return mnw != null && Double.compare(current, mnw) <= 0;
	}

	private boolean isNormal(Double current, Double mnw, Double mhw, Double hsw, String stateMnwMhw) {
		boolean inMnwMhwRange = mnw != null && mhw != null && Double.compare(current, mnw) >= 0
				&& Double.compare(current, mhw) <= 0 && !"commented".equalsIgnoreCase(stateMnwMhw);

		boolean inZeroHswRange = hsw != null && Double.compare(current, 0.0) >= 0 && Double.compare(current, hsw) <= 0;

		return inMnwMhwRange || inZeroHswRange;
	}

	private boolean isHigh(Double current, Double mhw, Double hsw) {
		boolean aboveMhw = mhw != null && Double.compare(current, mhw) >= 0;
		boolean aboveHsw = hsw != null && Double.compare(current, hsw) >= 0;

		return aboveMhw || aboveHsw;
	}

}
