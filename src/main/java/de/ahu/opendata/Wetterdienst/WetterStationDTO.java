package de.ahu.opendata.Wetterdienst;

import java.util.ArrayList;
import java.util.List;

import de.ahu.opendata.DataUtils.StationDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WetterStationDTO extends StationDTO {
	private String stationId;
	private String state;
	private Double height;
	private String resolution;
	private String dataset;
	private List<WetterParameter> pathParameters = new ArrayList<>();

	public void addPathParameter(WetterParameter parameter) {
		this.pathParameters.add(parameter);
	}

}
