package de.ahu.opendata.GovData;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataSetDTO {
	private String id;
	private String label;
	private String beschreibung;
	private int numberResources;
	private String lastModified;
	private String spatialValue;
	private String publisherName;
	private String tags;
	private String groups;
	private List<OgcWebserviceDTO> ogcWebserviceDTO;
}
