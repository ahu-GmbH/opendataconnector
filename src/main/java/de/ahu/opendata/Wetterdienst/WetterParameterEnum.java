package de.ahu.opendata.Wetterdienst;

import java.util.function.Function;

import com.vaadin.flow.component.charts.model.ChartType;

import de.ahu.opendata.Utils.Utils;
import lombok.Getter;

@Getter
public enum WetterParameterEnum {
	NIEDERSCHLAG(" [mm]", ChartType.COLUMN, "mm", data -> data.getValue(), true),
	TEMPERATUR(" [°C]", ChartType.LINE, "°C", data -> data.getValue(), true),
	WIND(" [km/h]", ChartType.LINE, "km/h", data -> data.getValue(), true),
	SONNENSCHEINDAUER(" [Stunden]", ChartType.LINE, " Stunden", data -> data.getValue() / 3600, false),
	HIMMELSTRAHLUNG(" [Stunden]", ChartType.LINE, " Stunden", transformValue(), false),
	FEUCHTIGKEIT(" [%]", ChartType.LINE, "%", data -> data.getValue() * 100, true),
	WOLKBEDECKUNG(" [%]", ChartType.LINE, "%", data -> data.getValue() * 100, true),

	SICHTWEITE(" [m]", ChartType.LINE, "m", data -> data.getValue(), true),
	LUFTDRUCK(" [hPa]", ChartType.LINE, "hPa", data -> data.getValue(), true),
	LETZTE_WETTER(" -", ChartType.LINE, " ", data -> data.getValue(), false),
	NEBEL(" [%]", ChartType.LINE, "%", data -> data.getValue(), false),
	GRUNDWASSER(" [ m ü. NHN]", ChartType.LINE, "m ü. NHN", data -> data.getValue(), false),
	PEGEL(" [cm]", ChartType.LINE, "cm", data -> data.getValue(), false);

	private final String title;
	private final ChartType chartType;
	private final String unit;
	private final Function<WetterDatenDTO, Number> valueTransformer;
	private final boolean useCrosshair;
	private static String resolution = "";

	WetterParameterEnum(String title, ChartType chartType, String unit,
			Function<WetterDatenDTO, Number> valueTransformer, boolean useCrosshair) {
		this.title = title;
		this.chartType = chartType;
		this.unit = unit;
		this.valueTransformer = valueTransformer;
		this.useCrosshair = useCrosshair;
	}

	static {
		resolution = "";
	}

	private static Function<WetterDatenDTO, Number> transformValue() {
		return data -> {
			double value = data.getValue();
			String resol = resolution;
			switch (resol) {
			case "daily":
				return Utils.roundUpNumber(value / 60.0);
			case "monthly":
				return Utils.roundUpNumber(value / 1440.0);
			case "yearly":
				return Utils.roundUpNumber(value / 525600.0);
			case "hourly":
				return value / 60.0;
			case "1 minute":
			case "5 minutes":
			case "10 minutes":
				return value;
			default:
				return value;
			}
		};
	}

	public static void setResolution(String newResolution) {
		resolution = newResolution;
	}
}
