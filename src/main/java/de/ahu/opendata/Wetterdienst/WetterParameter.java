package de.ahu.opendata.Wetterdienst;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WetterParameter {
	private String name;
	private String originalName;
	private String description;
	private String unitType;
	private String unit;
	private String constraints;

	public WetterParameter(String name, String originalName, String description, String unitType, String unit,
			String constraints) {
		this.name = name;
		this.originalName = originalName;
		this.description = description;
		this.unitType = unitType;
		this.unit = unit;
		this.constraints = constraints;
	}

	public WetterParameter(String originalName) {
		this.originalName = originalName;
	}
}
