package de.ahu.opendata.Pegeldienst;

import java.util.Map;

import de.ahu.opendata.DataUtils.RestDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WasserstandDTO extends RestDTO {
	private String timeStamp;
	private Double currentMeasuredValue;
	private String stateMnwMhw;
	private String stateNswHsw;
	private Double equidistance;
	private Map<String, Double> hasCharacteristicValues;

	private String shortName;
	private String longName;
	private String value;
	private String unit;
	private String occurrences;
	private String timespanStart;
	private String timespanEnd;
	private String validFrom;

}
