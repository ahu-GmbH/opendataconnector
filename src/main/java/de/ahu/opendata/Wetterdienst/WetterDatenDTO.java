package de.ahu.opendata.Wetterdienst;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.ahu.opendata.DataUtils.RestDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonFilter("wetterDatenFilter")
public class WetterDatenDTO extends RestDTO {

	@JsonProperty("station")
	private String stationId;
	private String resolution;
	private String datasest;
	private String parameter;
	private String date;
	private Double value;
	private Double quality;

	public Double convertToCelsius() {
		BigDecimal bd = new BigDecimal(this.value - 273.15).setScale(2, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

}
