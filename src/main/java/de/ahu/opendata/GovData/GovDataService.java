package de.ahu.opendata.GovData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GovDataService {

	private Set<String> groupTags = new HashSet<>();
	private Set<String> organizationTags = new HashSet<>();
	private Set<String> formatTags = new HashSet<>();
	private Integer totalCount;

	public Mono<List<DataSetDTO>> fetchGovDataService(String url) {
		return Mono.just(WebUtils.fetchRemoteText(url)).map(this::parseResponse);
	}

	public void clearDropDownValues() {
		groupTags.clear();
		organizationTags.clear();
		formatTags.clear();
	}

	public List<DataSetDTO> parseResponse(String fetchData) {
		List<DataSetDTO> dataSetList = new ArrayList<>();
		if (fetchData == null) {
			log.warn("Problem bei Parsen der Response");
			return dataSetList;
		}
		JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
		if (jsonObject.has("result")) {
			JSONObject result = jsonObject.getJSONObject("result");
			if (result.has("count")) {
				totalCount = result.getInt("count");
			}
			if (result.has("results")) {
				JSONArray results = result.getJSONArray("results");
				for (int i = 0; i < results.length(); i++) {
					JSONObject obj = results.getJSONObject(i);
					DataSetDTO dataSet = new DataSetDTO();
					dataSet.setBeschreibung(obj.optString("notes", ""));
					dataSet.setLabel(obj.optString("title", ""));
					dataSet.setNumberResources(obj.getInt("num_resources"));

					if (obj.has("organization")) {
						JSONObject organization = obj.getJSONObject("organization");
						String title = organization.optString("title", "");
						organizationTags.add(title);
					}

					if (obj.has("extras") && obj.getJSONArray("extras") instanceof JSONArray) {
						JSONArray extras = obj.getJSONArray("extras");
						for (int j = 0; j < extras.length(); j++) {
							JSONObject extra = extras.getJSONObject(j);
							if (extra.get("key").equals("modified")) {
								dataSet.setLastModified(extra.optString("value", ""));
							}
							if (extra.get("key").equals("publisher_name")) {
								dataSet.setPublisherName(extra.optString("value", ""));
							}
							if (extra.get("key").equals("spatial")) {
								dataSet.setSpatialValue(extra.optString("value", ""));
							}
						}
					}

					if (obj.has("groups") && obj.getJSONArray("groups") instanceof JSONArray) {

						JSONArray groups = obj.getJSONArray("groups");

						for (int j = 0; j < groups.length(); j++) {
							JSONObject group = groups.getJSONObject(j);
							String display_name = group.optString("display_name", "");
							groupTags.add(display_name);
						}
					}
					if (obj.has("resources") && obj.getJSONArray("resources") instanceof JSONArray) {

						List<OgcWebserviceDTO> ogcWebserviceDTOs = new ArrayList<OgcWebserviceDTO>();

						JSONArray resources = obj.getJSONArray("resources");

						for (int j = 0; j < resources.length(); j++) {
							JSONObject resource = resources.getJSONObject(j);
							OgcWebserviceDTO ogcWebserviceDTO = new OgcWebserviceDTO();

							ogcWebserviceDTO.setLabel(resource.optString("name", ""));
							String accessServices = resource.optString("access_services", "");

							if (resource.has("format")) {
								String format = resource.getString("format");
								if (!format.isBlank()) {
									if (format.startsWith("http")) {
										format = format.substring(format.lastIndexOf("/") + 1);
									}
									formatTags.add(format);
								}
							}
							List<String> acessServicesList = Arrays.asList(accessServices.split(", "));
							for (String accessService : acessServicesList) {
								if (accessService.contains("\"description\"")) {
									ogcWebserviceDTO.setDescription(
											accessService.split(":")[1].replace("\"", "").replace("/", "").trim());
								}
							}

							ogcWebserviceDTO.setResourceUrl(resource.optString("url", ""));
							ogcWebserviceDTO.setFormat(resource.optString("format", ""));

							ogcWebserviceDTOs.add(ogcWebserviceDTO);
						}
						dataSet.setOgcWebserviceDTO(ogcWebserviceDTOs);
					}

					dataSetList.add(dataSet);
				}
			}
		}

		return dataSetList;
	}

	public List<String> fetchTags(String url) {
		List<String> tags = new ArrayList<>();
		String fetchData = WebUtils.fetchRemoteText(url);
		if (fetchData == null) {
			log.error("Unexpected error while fetching URL {}: {}", url);
			return tags;
		}
		JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
		JSONArray jsonFeatures = jsonObject.has("result") ? jsonObject.getJSONArray("result") : null;
		for (int i = 0; i < jsonFeatures.length(); i++) {
			tags.add(jsonFeatures.getString(i));
		}
		return tags;
	}

	public Map<String, String> fetchOrganizationAndGroupGovData(String url) {
		Map<String, String> categoriesMap = new HashMap<String, String>();
		String fetchData = WebUtils.fetchRemoteText(url);
		if (fetchData == null) {
			log.error("Unexpected error while fetching URL {}: {}", url);
			return categoriesMap;
		}
		JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
		JSONArray jsonFeatures = jsonObject.has("result") ? jsonObject.getJSONArray("result") : null;

		for (int i = 0; i < jsonFeatures.length(); i++) {
			JSONObject feature = jsonFeatures.getJSONObject(i);

			String display_name = feature.has("display_name") ? feature.getString("display_name") : null;
			String name = feature.has("name") ? feature.getString("name") : null;

			categoriesMap.put(display_name, name);

		}
		return categoriesMap;
	}

	public Integer getLastResultCount() {
		return totalCount;
	}

	public List<String> updateGroupTags() {
		return groupTags.stream().collect(Collectors.toList());
	}

	public List<String> updateOrganizationTags() {
		return organizationTags.stream().collect(Collectors.toList());
	}

	public List<String> updateFormatTags() {
		return formatTags.stream().collect(Collectors.toList());
	}
}
