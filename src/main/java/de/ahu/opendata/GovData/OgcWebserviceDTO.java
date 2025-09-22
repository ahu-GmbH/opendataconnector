package de.ahu.opendata.GovData;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OgcWebserviceDTO {
	private String label;
	private String description;
	private String serviceFees;
	private String accessConstraints;
	private String providerName;
	private String resourceUrl;
	private String keywords;
	private String serviceVersion;
	private String format;
	private String packageId;
	private String capabilityList;
	private List<OgcLayerDTO> ogcLayers;

}
