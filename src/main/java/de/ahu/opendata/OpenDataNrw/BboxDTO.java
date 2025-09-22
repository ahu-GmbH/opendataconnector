package de.ahu.opendata.OpenDataNrw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BboxDTO {
	private double westBoundLongitude;
	private double eastBoundLongitude;
	private double southBoundLatitude;
	private double northBoundLatitude;
}
