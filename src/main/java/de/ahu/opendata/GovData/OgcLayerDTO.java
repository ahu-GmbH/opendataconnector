package de.ahu.opendata.GovData;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OgcLayerDTO {
	private String label;
	private String description;
	private String crs;
	private String bboxWestLongitude;
	private String bboxEastLongitude;
	private String bboxSouthLatitude;
	private String bboxNorthLatitude;
	private String geometryType;
	private String url;
}
