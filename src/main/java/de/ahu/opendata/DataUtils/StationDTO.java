package de.ahu.opendata.DataUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StationDTO extends RestDTO {
	private Double latitude;
	private Double longitude;
	private String name;
	private String stationNo;
	private String startDate;
	private String endDate;
	private String provider;
	private String unit;

	private Boolean isHistorical;
	private Boolean isForecast;

	@Getter
	@Setter
	public static class Measurement extends RestDTO {
		private final String stationNo;
		private final String time;
		private final Double value;

		public Measurement(String stationNo, String time, Double value) {
			this.stationNo = stationNo;
			this.time = time;
			this.value = value;
		}
	}

}
