package de.ahu.opendata.OpenDataNrw;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetadataDTO {
	private String referenceSystemCode;
	private String label;
	private String abstractText;
	private List<String> keywords;
	private BboxDTO geographicBoundingBox;
}
