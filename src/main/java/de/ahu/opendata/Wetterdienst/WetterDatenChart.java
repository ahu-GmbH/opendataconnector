package de.ahu.opendata.Wetterdienst;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.Crosshair;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.html.Div;

import de.ahu.opendata.Utils.Utils;

public class WetterDatenChart extends Div {

	private static final long serialVersionUID = 1L;
	private final Div grafikWrapperHistoricalChart = new Div();

	public WetterDatenChart() {

	}

	public void displayWetterDataChart(List<WetterDatenDTO> wetterdaten, String parameterValue,
			WetterStationDTO ddStation, String resolution) {

		if (wetterdaten.isEmpty()) {
			grafikWrapperHistoricalChart.removeAll();
			Utils.showHinweisBox("Keine " + parameterValue + " -Daten für " + ddStation.getName() + " gefunden.", 3000);
			return;
		}

		WetterParameterEnum param = getWeatherParameter(parameterValue, resolution);
		if (param == null) {
			grafikWrapperHistoricalChart.removeAll();
			Utils.showHinweisBox("Unbekannter Parameter: " + parameterValue + " für " + ddStation.getName(), 3000);
			return;
		}

		grafikWrapperHistoricalChart.removeAll();
		Chart chart = createChart(param, ddStation.getName(), parameterValue);
		configureChartData(chart, param, wetterdaten, ddStation.getName());

		grafikWrapperHistoricalChart.setSizeFull();
		grafikWrapperHistoricalChart.add(chart);
		add(grafikWrapperHistoricalChart);

	}

	private WetterParameterEnum getWeatherParameter(String parameterValue, String resolution) {
		String parameterValueLower = parameterValue.toLowerCase();
		WetterParameterEnum.setResolution(resolution);

		if (parameterValueLower.contains("nied") || parameterValueLower.contains("precipitation")
				|| parameterValueLower.contains("water") || parameterValueLower.contains("schnee")) {
			return WetterParameterEnum.NIEDERSCHLAG;
		} else if (parameterValueLower.contains("temp")) {
			return WetterParameterEnum.TEMPERATUR;
		} else if (parameterValueLower.contains("wind")) {
			return WetterParameterEnum.WIND;
		} else if (parameterValueLower.contains("dauer")) {
			return WetterParameterEnum.SONNENSCHEINDAUER;
		} else if (parameterValueLower.contains("feucht")) {
			return WetterParameterEnum.FEUCHTIGKEIT;
		} else if (parameterValueLower.contains("bewölk") || parameterValueLower.contains("cloud")) {
			return WetterParameterEnum.WOLKBEDECKUNG;
		} else if (parameterValueLower.contains("strahl") || parameterValueLower.contains("radiation")) {
			return WetterParameterEnum.HIMMELSTRAHLUNG;
		} else if (parameterValueLower.contains("sicht") || parameterValueLower.contains("visibility")) {
			return WetterParameterEnum.SICHTWEITE;
		} else if (parameterValueLower.contains("druck") || parameterValueLower.contains("pressure")) {
			return WetterParameterEnum.LUFTDRUCK;
		} else if (parameterValueLower.contains("weather")) {
			return WetterParameterEnum.LETZTE_WETTER;
		} else if (parameterValueLower.contains("fog") || parameterValueLower.contains("nebel")) {
			return WetterParameterEnum.NEBEL;
		}
		return null;
	}

	private Chart createChart(WetterParameterEnum param, String stationName, String parameterValue) {
		Chart chart = new Chart(param.getChartType());
		Configuration conf = chart.getConfiguration();
		conf.setTitle(parameterValue + param.getTitle() + " in " + stationName.substring(0, 1)
				+ stationName.substring(1).toLowerCase());
		conf.getChart().setStyledMode(true);

		configureXAxis(conf, param.isUseCrosshair());
		configureTooltip(conf, param.getUnit());

		if (param == WetterParameterEnum.NIEDERSCHLAG) {
			YAxis y = new YAxis();
			y.setMin(0);
			y.setTitle(parameterValue + param.getTitle());
			conf.addyAxis(y);
		}

		chart.setTimeline(true);
		return chart;
	}

	private void configureXAxis(Configuration conf, boolean useCrosshair) {
		XAxis xAxis = new XAxis();
		xAxis.setType(AxisType.DATETIME);
		if (useCrosshair) {
			xAxis.setCrosshair(new Crosshair());
		}
		conf.addxAxis(xAxis);
	}

	private void configureTooltip(Configuration conf, String unit) {
		Tooltip tooltip = new Tooltip();
		tooltip.setShared(true);
		tooltip.setValueSuffix(unit);
		if (unit.equals("%")) {
			tooltip.setValueDecimals(1);
		}
		conf.setTooltip(tooltip);
	}

	private void configureChartData(Chart chart, WetterParameterEnum param, List<WetterDatenDTO> wetterdaten,
			String stationName) {
		DataSeries series = new DataSeries(param.getTitle());
		for (WetterDatenDTO data : wetterdaten) {
			String cleanDate = omitMsFromString(data.getDate());
			LocalDateTime dateTime = LocalDateTime.parse(cleanDate);
			long millis = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
			Number value = param.getValueTransformer().apply(data);
			series.add(new DataSeriesItem(millis, value));
		}
		chart.getConfiguration().addSeries(series);
	}

	private String omitMsFromString(String dateString) {
		String[] parts = dateString.split("\\+");
		return parts.length == 2 ? parts[0] : null;
	}
}
