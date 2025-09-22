package de.ahu.opendata.Pegeldienst;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PegelstandDaten {
	private String shortname;
	private String longname;
	private String unit;
	private double equidistance;
	private String start;
	private String end;
}
