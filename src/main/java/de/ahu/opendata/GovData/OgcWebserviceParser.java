package de.ahu.opendata.GovData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OgcWebserviceParser {

	private int INDENT_FACTOR = 4;
	private Set<OgcLayerDTO> ogcLayers = null;

	public OgcWebserviceDTO fetchResource(String capabilitiesUrl) {
		String capabilitiesXml = WebUtils.fetchRemoteText(capabilitiesUrl);
		if (StringUtils.isAllBlank(capabilitiesXml)) {
			Utils.showErrorBox("Die Abfrage lieferte kein Ergebnis", 10000);
			return null;
		}
		OgcWebserviceDTO parseResult = null;
		String jsonPrettyPrintString = Utils.xmlToJson(capabilitiesXml, INDENT_FACTOR);
		if (jsonPrettyPrintString != null) {
			JSONObject json = new JSONObject(jsonPrettyPrintString);
			String firstKey = json.keySet().iterator().next();
			JSONObject capabilities = json.getJSONObject(firstKey);

			if (StringUtils.containsIgnoreCase(firstKey, "WMS")) {
				return parseWms(capabilities);
			} else if (StringUtils.containsIgnoreCase(firstKey, "WFS")) {
				return parseWfs(capabilities);
			} else {
				return parseResult;
			}
		}
		return null;
	}

	private OgcWebserviceDTO parseWms(JSONObject capabilities) {
		JSONObject capability = capabilities.getJSONObject("Capability");
		JSONObject request = capability.getJSONObject("Request");
		JSONObject service = capabilities.getJSONObject("Service");
		String version = capabilities.getString("version");

		String title = service.getString("Title");
		String description = "";

		if (service.get("Abstract") instanceof JSONArray) {
			JSONArray abstractArray = service.getJSONArray("Abstract");
			for (int i = 0; i < abstractArray.length(); i++) {
				if (abstractArray.get(i) instanceof JSONArray) {
					JSONArray item = abstractArray.getJSONArray(i);
					for (int j = 0; j < item.length(); j++) {
						description += item.get(j).toString();
					}
				}
			}
		} else {
			description = service.getString("Abstract");
		}

		String capabilityList = String.join("; ", request.keySet());

		String keywords = "";
		if (service.has("KeywordList")) {
			JSONObject keywordWrapper = service.getJSONObject("KeywordList");
			Object keywordObject = keywordWrapper.get("Keyword");
			if (keywordObject instanceof JSONArray) {
				JSONArray keywordArray = keywordWrapper.getJSONArray("Keyword");
				keywords = keywordArray.join("; ");
				keywords = keywords.replaceAll("\"", "");
			} else if (keywordObject instanceof JSONObject) {
				JSONObject keywordJsonObject = keywordWrapper.getJSONObject("Keyword");
				if (keywordJsonObject.has("content")) {
					keywords = keywordJsonObject.getString("content");
				}
			} else {
				keywords = keywordWrapper.getString("Keyword");
			}
		}
		String serviceProviderName = "";
		if (service.has("ContactInformation")) {
			JSONObject contactInfo = service.getJSONObject("ContactInformation");
			if (contactInfo.has("ContactPersonPrimary")) {
				JSONObject contactPerson = contactInfo.getJSONObject("ContactPersonPrimary");
				if (contactPerson.has("ContactOrganization")) {
					serviceProviderName = contactPerson.getString("ContactOrganization");
				}
			}
		}

		String fees = "";
		if (service.has("Fees")) {
			fees = service.getString("Fees");
		}

		String accessConstraints = "";
		if (service.has("AccessConstraints")) {
			accessConstraints = service.getString("AccessConstraints");
		}

		ogcLayers = new HashSet<OgcLayerDTO>();
		if (capability.has("Layer")) {
			JSONObject layerMain = capability.getJSONObject("Layer");
			Set<OgcLayerDTO> ogcLayerMain = parseWmsLayer(layerMain);
			ogcLayers.addAll(ogcLayerMain);
		}

		OgcWebserviceDTO ogcWebservice = new OgcWebserviceDTO();
		ogcWebservice.setLabel(title);
		ogcWebservice.setDescription(description);
		ogcWebservice.setServiceVersion(version);
		ogcWebservice.setKeywords(keywords);
		ogcWebservice.setAccessConstraints(accessConstraints);
		ogcWebservice.setProviderName(serviceProviderName);
		ogcWebservice.setCapabilityList(capabilityList);
		ogcWebservice.setServiceFees(fees);
		ogcWebservice.setOgcLayers(ogcLayers.stream().toList());

		return ogcWebservice;
	}

	private OgcWebserviceDTO parseWfs(JSONObject capabilities) {
		String version = capabilities.getString("version");

		JSONObject serviceIdentification = capabilities.getJSONObject("ows:ServiceIdentification");
		String title = (serviceIdentification.has("ows:Title")) ? serviceIdentification.getString("ows:Title") : "";
		String description = (serviceIdentification.has("ows:Abstract"))
				? serviceIdentification.getString("ows:Abstract")
				: "";
		String fees = (serviceIdentification.has("ows:Fees")) ? serviceIdentification.getString("ows:Fees") : "";
		String accessConstraints = (serviceIdentification.has("ows:AccessConstraints"))
				? serviceIdentification.getString("ows:AccessConstraints")
				: "";

		String keywords = "";
		if (serviceIdentification.has("ows:Keywords")) {
			if (serviceIdentification.get("ows:Keywords") instanceof JSONArray) {
				JSONArray keywordsArray = serviceIdentification.getJSONArray("ows:Keywords");
				for (int i = 0; i < keywordsArray.length(); i++) {
					JSONObject item = (JSONObject) keywordsArray.get(i);
					keywords += parseKeyWordsKey(item, keywords);
				}
			} else if (serviceIdentification.get("ows:Keywords") instanceof JSONObject) {
				JSONObject keywordsObject = serviceIdentification.getJSONObject("ows:Keywords");
				keywords += parseKeyWordsKey(keywordsObject, keywords);
			} else {
				keywords = serviceIdentification.get("ows:Keywords").toString();
			}

			if (keywords.endsWith("; ")) {
				keywords = keywords.substring(0, keywords.length() - 2);
			}

			String[] keywordArray = keywords.split(";");
			if (keywordArray.length > 40) {
				int limit = Math.min(keywordArray.length, 40);
				String[] limitedKeywords = new String[limit];
				for (int i = 0; i < limit; i++) {
					limitedKeywords[i] = keywordArray[i];
				}
				keywords = String.join(";", limitedKeywords);
			}
		}
		String serviceProviderName = "";
		if (capabilities.has("ows:ServiceProvider")) {
			JSONObject serviceProvider = capabilities.getJSONObject("ows:ServiceProvider");
			serviceProviderName = serviceProvider.getString("ows:ProviderName");
		}
		if (capabilities.has("wfs:FeatureTypeList")) {
			JSONObject featureTypeList = capabilities.getJSONObject("wfs:FeatureTypeList");
			ogcLayers = new HashSet<OgcLayerDTO>();
			if (featureTypeList.has("wfs:FeatureType")) {
				Object featureObject = featureTypeList.get("wfs:FeatureType");
				if (featureObject instanceof JSONArray) {
					JSONArray featureTypes = featureTypeList.getJSONArray("wfs:FeatureType");
					for (int i = 0; i < featureTypes.length(); i++) {
						JSONObject feature = featureTypes.getJSONObject(i);
						OgcLayerDTO ogcLayer = parseWfsLayer(feature);

						ogcLayers.add(ogcLayer);
					}
				} else if (featureObject instanceof JSONObject) {
					JSONObject feature = featureTypeList.getJSONObject("wfs:FeatureType");
					OgcLayerDTO ogcLayer = parseWfsLayer(feature);
					ogcLayers.add(ogcLayer);
				}
			}
		}

		JSONObject operations = capabilities.getJSONObject("ows:OperationsMetadata");
		String url = "";
		String capabilityList = "";
		if (operations.has("ows:Operation")) {
			Object operationsObject = operations.get("ows:Operation");
			JSONObject firstOp = null;
			if (operationsObject instanceof JSONArray) {
				List<String> capabilityContainer = new ArrayList<String>();
				JSONArray operationArray = operations.getJSONArray("ows:Operation");
				firstOp = operationArray.getJSONObject(0);
				for (int i = 0; i < operationArray.length(); i++) {
					JSONObject operation = operationArray.getJSONObject(i);
					if (operation.has("name")) {
						capabilityContainer.add(operation.getString("name"));
					}
				}
				capabilityList = String.join("; ", capabilityContainer);
			} else {
				firstOp = operations.getJSONObject("ows:Operation");
			}
			if (firstOp != null) {
				JSONObject firstOpDcp = firstOp.getJSONObject("ows:DCP");
				JSONObject firstOpDcpHttp = firstOpDcp.getJSONObject("ows:HTTP");
				JSONObject firstOpDcpHttpGet = firstOpDcpHttp.getJSONObject("ows:Get");
				url = firstOpDcpHttpGet.optString("xlink:href");
			}
		}
		OgcWebserviceDTO ogcWebservice = new OgcWebserviceDTO();
		ogcWebservice.setLabel(title);
		ogcWebservice.setDescription(description);
		ogcWebservice.setServiceVersion(version);
		ogcWebservice.setKeywords(keywords);
		ogcWebservice.setAccessConstraints(accessConstraints);
		ogcWebservice.setProviderName(serviceProviderName);
		ogcWebservice.setCapabilityList(capabilityList);
		ogcWebservice.setServiceFees(fees);
		if (ogcLayers != null) {
			ogcWebservice.setOgcLayers(ogcLayers.stream().toList());
		}
		return ogcWebservice;
	}

	private Set<OgcLayerDTO> parseWmsLayer(JSONObject feature) {
		String featureGroup = feature.optString("Title");
		String featureCrsAvailable = "";
		if (feature.has("CRS")) {
			Object crsObject = feature.get("CRS");
			if (crsObject instanceof JSONArray) {
				JSONArray crsArray = feature.getJSONArray("CRS");
				featureCrsAvailable = crsArray.join("; ");
				featureCrsAvailable = featureCrsAvailable.replaceAll("\"", "");
			} else if (crsObject instanceof JSONObject) {
				featureCrsAvailable = feature.getString("CRS");
			}
		}
		JSONObject featureBbox = feature.getJSONObject("EX_GeographicBoundingBox");
		String westBoundLon = String.valueOf(featureBbox.getBigDecimal("westBoundLongitude"));
		String eastBoundLon = String.valueOf(featureBbox.getBigDecimal("eastBoundLongitude"));
		String southBoundLat = String.valueOf(featureBbox.getBigDecimal("southBoundLatitude"));
		String northtBoundLat = String.valueOf(featureBbox.getBigDecimal("northBoundLatitude"));

		Set<OgcLayerDTO> resultLayers = new HashSet<OgcLayerDTO>();

		if (feature.has("Layer")) {
			Object layerObject = feature.get("Layer");
			if (layerObject instanceof JSONArray) {
				JSONArray layerArray = feature.getJSONArray("Layer");
				for (int i = 0; i < layerArray.length(); i++) {
					if (layerObject instanceof JSONArray) {
						String layerName = "";
						String layerTitle = "";
						String legendUrl = "";
						JSONArray layerFeatureArray = feature.getJSONArray("Layer");
						for (int j = 0; j < layerFeatureArray.length(); j++) {
							JSONObject layerFeature = layerFeatureArray.getJSONObject(i);

							layerTitle = (layerFeature.has("Title")) ? layerFeature.getString("Title") : "";
							if (layerFeature.has("Name")) {
								layerName = Utils.getStringFromAnyType(layerFeature, "Name");
							}

							if (layerFeature.has("Style")) {

								if (layerFeature.get("Style") instanceof JSONObject) {
									JSONObject layerStyle = layerFeature.getJSONObject("Style");
									if (layerStyle.has("LegendURL")) {
										JSONObject layerLegend = layerStyle.getJSONObject("LegendURL");
										if (layerLegend.has("OnlineResource")) {
											JSONObject onlineResource = layerLegend.getJSONObject("OnlineResource");
											legendUrl = onlineResource.optString("xlink:href");
										}
									}
								} else if (layerFeature.get("Style") instanceof JSONArray) {
									JSONArray layerStyles = layerFeature.getJSONArray("Style");
									for (int a = 0; a < layerStyles.length(); a++) {
										JSONObject layerStyle = layerStyles.getJSONObject(a);
										if (layerStyle.has("OnlineResource")) {
											JSONObject onlineResource = layerStyle.getJSONObject("OnlineResource");
											legendUrl = onlineResource.optString("xlink:href");
											break;
										}
									}
								}
							}
						}
						OgcLayerDTO ogcLayer = new OgcLayerDTO();
						ogcLayer.setLabel(layerName);
						ogcLayer.setDescription(layerTitle);
						ogcLayer.setCrs(featureCrsAvailable);
						ogcLayer.setBboxWestLongitude(westBoundLon);
						ogcLayer.setBboxEastLongitude(eastBoundLon);
						ogcLayer.setBboxNorthLatitude(northtBoundLat);
						ogcLayer.setBboxSouthLatitude(southBoundLat);

						resultLayers.add(ogcLayer);
					}

				}
			} else if (layerObject instanceof JSONObject) {
				if (feature.get("CRS") instanceof String) {
					featureCrsAvailable = feature.getString("CRS");
				} else if (feature.get("CRS") instanceof JSONArray) {
					JSONArray crsArray = feature.getJSONArray("CRS");
					for (int j = 0; j < crsArray.length(); j++) {
						String crsObject = crsArray.getString(j);
						featureCrsAvailable += crsObject + (j < crsArray.length() - 1 ? ", " : "");
					}
				}

				JSONObject layer = feature.getJSONObject("Layer");

				String layerTitle = layer.getString("Title");

				String layerName = "";
				if (layer.get("Name") instanceof Integer) {
					layerName = Integer.toString(layer.getInt("Name"));
				} else if (layer.get("Name") instanceof String) {
					layerName = layer.getString("Name");
				}

				String legendUrl = "";
				if (layer.has("Style")) {
					if (layer.get("Style") instanceof JSONObject) {
						JSONObject layerStyle = layer.getJSONObject("Style");
						if (layerStyle.has("LegendURL")) {
							JSONObject layerLegend = layerStyle.getJSONObject("LegendURL");
							if (layerLegend.has("OnlineResource")) {
								JSONObject onlineResource = layerLegend.getJSONObject("OnlineResource");
								legendUrl = onlineResource.optString("xlink:href");
							}
						}
					}

				}

				OgcLayerDTO ogcLayer = new OgcLayerDTO();
				ogcLayer.setLabel(layerName);
				ogcLayer.setDescription(layerTitle);
				ogcLayer.setCrs(featureCrsAvailable);
				ogcLayer.setBboxWestLongitude(westBoundLon);
				ogcLayer.setBboxEastLongitude(eastBoundLon);
				ogcLayer.setBboxNorthLatitude(northtBoundLat);
				ogcLayer.setBboxSouthLatitude(southBoundLat);

				resultLayers.add(ogcLayer);
			}
		}
		return resultLayers;
	}

	private OgcLayerDTO parseWfsLayer(JSONObject feature) {
		String featureName = feature.optString("wfs:Name");
		String featureTitle = feature.optString("wfs:Title");
		String featureCrs = feature.optString("wfs:DefaultCRS");

		String westBoundLon = "";
		String eastBoundLon = "";
		String southBoundLat = "";
		String northtBoundLat = "";
		if (feature.has("ows:WGS84BoundingBox")) {
			JSONObject featureBbox = feature.getJSONObject("ows:WGS84BoundingBox");
			String featureBoxLowerLeft = featureBbox.optString("ows:LowerCorner");
			String featureBoxUpperRight = featureBbox.optString("ows:UpperCorner");
			String[] featureBoxLowerLeftElements = featureBoxLowerLeft.split(" ");
			String[] featureBoxUpperRightElements = featureBoxUpperRight.split(" ");
			westBoundLon = featureBoxLowerLeftElements[0];
			eastBoundLon = featureBoxUpperRightElements[0];
			southBoundLat = featureBoxLowerLeftElements[1];
			northtBoundLat = featureBoxUpperRightElements[1];
		}

		OgcLayerDTO ogcLayer = new OgcLayerDTO();
		ogcLayer.setLabel(featureName);
		ogcLayer.setDescription(featureTitle);
		ogcLayer.setCrs(featureCrs);
		ogcLayer.setBboxWestLongitude(westBoundLon);
		ogcLayer.setBboxEastLongitude(eastBoundLon);
		ogcLayer.setBboxNorthLatitude(northtBoundLat);
		ogcLayer.setBboxSouthLatitude(southBoundLat);
		return ogcLayer;
	}

	private String parseKeyWordsKey(JSONObject item, String keywords) {
		if (item.has("ows:Keyword")) {
			if (item.get("ows:Keyword") instanceof JSONArray) {
				JSONArray keywordArray = item.getJSONArray("ows:Keyword");
				for (int j = 0; j < keywordArray.length(); j++) {
					keywords += keywordArray.get(j).toString() + "; ";
				}
			} else if (item.get("ows:Keyword") instanceof String) {
				keywords += item.get("ows:Keyword").toString() + "; ";
			}
		}
		return keywords;
	}

}
