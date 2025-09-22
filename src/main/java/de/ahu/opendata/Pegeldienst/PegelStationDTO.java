package de.ahu.opendata.Pegeldienst;

import de.ahu.opendata.DataUtils.StationDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PegelStationDTO extends StationDTO {
	private String description;
	private Double length;
	private String agency;
	private PegelstandDaten pegelstandTimeSeries;
	private WasserstandDTO hasWasserstand;
	private String pegelStatus;
}
