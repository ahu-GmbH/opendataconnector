package de.ahu.opendata.Pegeldienst;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.AxisTitle;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotOptionsAreaspline;
import com.vaadin.flow.component.charts.model.PointPlacement;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.views.MainLayout;
import de.ahu.opendata.views.OpenLayersMap;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@Slf4j
@Route(value = "pegelstand", layout = MainLayout.class)
public class PegelstandView extends VerticalLayout {

	private final WasserstandService wasserstandService;

	private OpenLayersMap openLayersMap = new OpenLayersMap();

	private HorizontalLayout mainHorizontalLayout = new HorizontalLayout();
	private VerticalLayout dataVerticalLayout = new VerticalLayout();
	private VerticalLayout mapVerticalLayout = new VerticalLayout();
	private ComboBox<PegelStationDTO> ddPegelStation = new ComboBox<PegelStationDTO>();
	private ComboBox<String> filterStationStatus = new ComboBox<String>();
	private H5 headerLayout = new H5();

	private ComboBox<String> ddAgency = new ComboBox<String>();

	private HorizontalLayout mapFooterHorizontalLayout = new HorizontalLayout();
	private Paragraph overViewParagraph = new Paragraph();

	private Button btnToggleFilter = new Button(new Icon(VaadinIcon.REFRESH));

	private Div wrapperChartVorhersage = new Div();
	private Div wrapperChartHistorical = new Div();

	private List<PegelStationDTO> stations;
	private Set<String> agencies;
	private List<PegelStationDTO> stationWithForecast;
	private boolean isFiltered = false;

	public PegelstandView() {
		wasserstandService = SpringApplicationContext.getBean(WasserstandService.class);

		stations = wasserstandService.fetchPegelStationWithHistoricalData();

		openLayersMap.showStationsOnMap(stations);
		openLayersMap.setSizeFull();

		Paragraph pegelstandParagraph = new Paragraph();
		pegelstandParagraph.setText(
				"Wenn Sie mit der Maus über die einzelnen Punkte fahren, erscheinen die aktuellen Pegelstände. Bei einem Klick auf einen Punkt werden weitere Informationen angezeigt.");
		pegelstandParagraph.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);
		VerticalLayout legendLayout1 = new VerticalLayout();
		legendLayout1.setPadding(false);
		legendLayout1.setSpacing(false);
		legendLayout1.add(createLegendItem("mittlerer Wasserstand", "green"));
		legendLayout1.add(createLegendItem("niedriger Wasserstand (kleiner/gleich MHW)", "orange"));
		legendLayout1.add(createLegendItem("hoher Wasserstand (größer/gleich MHW)", "blue"));
		legendLayout1.add(createLegendItem("kein aktueller Wasserstand", "gray"));
		pegelstandParagraph.add(legendLayout1);

		headerLayout.setWidthFull();
		headerLayout.setText("Übersichtskarte über Pegelstationen");
		headerLayout.addClassNames(LumoUtility.Border.BOTTOM, LumoUtility.Padding.SMALL);

		VerticalLayout legendLayout2 = new VerticalLayout();
		H5 heading = new H5("Aktuelle Lage");
		heading.addClassNames(LumoUtility.TextAlignment.CENTER, LumoUtility.Padding.MEDIUM);
		overViewParagraph.add(heading);
		legendLayout2.setPadding(false);
		legendLayout2.setSpacing(false);
		legendLayout2.setWidth("100%");
		legendLayout2.setJustifyContentMode(JustifyContentMode.START);
		legendLayout2.setAlignItems(Alignment.START);

		legendLayout2.add(createLegendItem(
				"Normaler Wasserstand: " + fetchCurrentPegelStations().get("normal") + " Pegel", "green"));
		legendLayout2.add(createLegendItem(
				"Niedriger Wasserstand: " + fetchCurrentPegelStations().get("low") + " Pegel", "orange"));
		legendLayout2.add(
				createLegendItem("Hoher Wasserstand: " + fetchCurrentPegelStations().get("high") + " Pegel", "blue"));
		legendLayout2.add(createLegendItem(
				"Kein aktueller Wasserstand: " + fetchCurrentPegelStations().get("unknown") + " Pegel", "grey"));
		overViewParagraph.add(legendLayout2);
		overViewParagraph.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);

		overViewParagraph.setWidth("45%");
		mapFooterHorizontalLayout.add(overViewParagraph, pegelstandParagraph);
		mapVerticalLayout.add(headerLayout, openLayersMap, mapFooterHorizontalLayout);
		mapVerticalLayout.setWidth("65%");

		stations = stations.stream().sorted((a, b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList());
		ddPegelStation.setItems(stations);
		ddPegelStation.setItemLabelGenerator(
				station -> station.getName().substring(0, 1) + station.getName().substring(1).toLowerCase());
		ddPegelStation.setLabel("Pegelstation");
		ddPegelStation.setHelperText(stations.size() + " Pegel");

		agencies = stations.stream().map(st -> st.getAgency()).collect(Collectors.toCollection(TreeSet::new));

		ddAgency.setItems(agencies);
		ddAgency.setLabel("Agenture");
		ddAgency.setHelperText(agencies.size() + " Behörde");

		Paragraph inforParagraph = new Paragraph();
		inforParagraph.setText(
				"Vorhersagen des Wasserstands werden vom Wasserstraßen- und Schifffahrtsamt Elbe, der Bundesanstalt für Gewässerkunde, der Hochwasservorhersagezentrale Rheinland-Pfalz und dem Landesbetrieb für Hochwasserschutz und Wasserwirtschaft Sachsen-Anhalt erstellt. Die Vorhersagen sind nur bei einigen Stationen abrufbar.");
		inforParagraph.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);
		inforParagraph.setWidth("70%");

		ddPegelStation.setWidth("40%");
		btnToggleFilter.setWidth("30%");
		HorizontalLayout stationHl = new HorizontalLayout(ddPegelStation, btnToggleFilter);
		stationHl.setAlignItems(Alignment.BASELINE);
		stationHl.addClassNames(LumoUtility.Gap.MEDIUM, LumoUtility.Display.FLEX, LumoUtility.FlexDirection.ROW,
				LumoUtility.FlexWrap.WRAP);
		ddAgency.setWidth("72%");
		stationHl.setWidthFull();

		filterStationStatus.setItems(List.of("mittel", "niedrig", "hoch", "kein aktueller"));
		filterStationStatus.setLabel("Stationen nach Wasserstand filtern ");
		filterStationStatus.setWidth("72%");

		dataVerticalLayout.add(inforParagraph, ddAgency, stationHl, filterStationStatus);

		filterStationStatus.addValueChangeListener(change -> {
			if (change.getValue() == null) {
				openLayersMap.showStationsOnMap(stations);
				filterStationStatus.setHelperText("");
				return;
			}
			String translateChangeValue = Utils.mapStatusValue(change.getValue());
			List<PegelStationDTO> filteredStations = ddPegelStation.getListDataView().getItems()
					.filter(st -> st.getPegelStatus() != null && st.getPegelStatus().equals(translateChangeValue))
					.collect(Collectors.toList());
			if (translateChangeValue.equals("unknown")) {
				List<PegelStationDTO> stationsWithoutValue = ddPegelStation.getListDataView().getItems()
						.filter(st -> st.getHasWasserstand() == null).collect(Collectors.toList());
				filteredStations.addAll(stationsWithoutValue);
			}
			openLayersMap.showStationsOnMap(filteredStations);
			filterStationStatus.setHelperText(filteredStations.size() + " Pegel gefunden");
		});

		btnToggleFilter.setTooltipText("Übersichrskarte wechseln");
		btnToggleFilter.addClickListener(click -> {

			if (!isFiltered) {
				stationWithForecast = wasserstandService.fetchPegelStationWithForecastData();
				Set<String> filterAgencies = stationWithForecast.stream().map(st -> st.getAgency())
						.collect(Collectors.toCollection(TreeSet::new));
				ddPegelStation.setItems(stationWithForecast);
				ddAgency.setItems(filterAgencies);

				ddAgency.setHelperText(filterAgencies.size() + " Behörde");
				ddPegelStation.setHelperText(stationWithForecast.size() + " Pegelstationen");
				openLayersMap.showStationsOnMap(stationWithForecast);
				headerLayout.setText("Übersichtskarte über Pegelstationen mit Vorhersage");
			} else {
				ddAgency.setItems(agencies);
				ddAgency.setHelperText(agencies.size() + " Behörde");
				ddPegelStation.setItems(stations);
				ddPegelStation.setHelperText(stations.size() + " Pegel");
				openLayersMap.showStationsOnMap(stations);
				headerLayout.setText("Übersichtskarte über Pegelstationen");
			}
			isFiltered = !isFiltered;
		});

		ddAgency.addValueChangeListener(change -> {
			List<PegelStationDTO> filterStations = null;
			if (ddAgency.getValue() != null) {

				if (isFiltered) {
					filterStations = stationWithForecast.stream()
							.filter(st -> st.getLatitude() != 0.0 && st.getAgency().equals(ddAgency.getValue()))
							.collect(Collectors.toList());
					ddPegelStation.setItems(filterStations);
					ddPegelStation.setHelperText(filterStations.size() + " Pegelstationen für " + ddAgency.getValue());
					openLayersMap.showStationsOnMap(filterStations);
				} else {
					filterStations = stations.stream()
							.filter(st -> st.getLatitude() != 0.0 && st.getAgency().equals(ddAgency.getValue()))
							.collect(Collectors.toList());
					ddPegelStation.setItems(filterStations);
					ddPegelStation.setHelperText(filterStations.size() + " Pegelstationen für " + ddAgency.getValue());
					openLayersMap.showStationsOnMap(filterStations);
				}
			} else {

			}
		});

		ddPegelStation.addValueChangeListener(change -> {
			if (change.getValue() != null) {

				openLayersMap.showStationsOnMap(List.of(ddPegelStation.getValue()));

				if (ddPegelStation.getValue().getPegelstandTimeSeries() != null) {
					List<WasserstandDTO> forecastData = wasserstandService
							.fetchVorhersageData(ddPegelStation.getValue().getId());

					wrapperChartVorhersage.removeAll();

					if (!forecastData.isEmpty()) {
						wrapperChartVorhersage.setSizeFull();

						wrapperChartVorhersage.add(
								new H4("Vorhersagen des Wasserstands vom "
										+ Utils.formateToGermanDate(forecastData.get(0).getTimespanStart()) + " bis "
										+ Utils.formateToGermanDate(
												forecastData.get(forecastData.size() - 1).getTimespanEnd())),
								createVorhersageChart(forecastData));
						dataVerticalLayout.addAndExpand(wrapperChartVorhersage);
					}
				} else {
					Utils.showHinweisBox(
							"Die Pegelstation " + ddPegelStation.getValue().getName() + " hat keine Vorhersagendaten.");
				}

				List<WasserstandDTO> historicalData = wasserstandService
						.fetchHistoricalData(ddPegelStation.getValue().getId());
				if (!historicalData.isEmpty()) {
					wrapperChartHistorical.removeAll();
					wrapperChartHistorical.setSizeFull();
					wrapperChartHistorical.add(
							new H4("Historischer Pegelstand vom "
									+ Utils.formatDateTime(historicalData.get(0).getTimeStamp()) + " bis "
									+ Utils.formatDateTime(
											historicalData.get(historicalData.size() - 1).getTimeStamp())),
							createHistoricalWasserStandChart(historicalData));
					dataVerticalLayout.addAndExpand(wrapperChartHistorical);
				}
			} else {
				if (wrapperChartHistorical != null) {
					wrapperChartHistorical.removeAll();
				}
				if (wrapperChartVorhersage != null) {
					wrapperChartVorhersage.removeAll();
				}
				openLayersMap.showStationsOnMap(
						ddPegelStation.getDataProvider().fetch(new Query<>()).collect(Collectors.toList()));
			}
		});

		dataVerticalLayout.setWidth("50%");
		dataVerticalLayout.addClassNames(LumoUtility.Border.RIGHT, LumoUtility.Padding.Right.SMALL);
		dataVerticalLayout.setHeightFull();
		mainHorizontalLayout.add(dataVerticalLayout, mapVerticalLayout);
		mainHorizontalLayout.setSizeFull();

		add(mainHorizontalLayout);
		setSizeFull();
	}

	private Map<String, Integer> fetchCurrentPegelStations() {
		Map<String, Integer> currentPegel = new HashMap<String, Integer>();
		List<String> statusList = List.of("low", "high", "normal", "unknown");
		for (String status : statusList) {
			List<PegelStationDTO> pegel = stations.stream()
					.filter(st -> st.getPegelStatus() != null && st.getPegelStatus().equals(status))
					.collect(Collectors.toList());

			int countPegel = pegel.size();

			if (status.equals("unknown")) {
				List<PegelStationDTO> stationWithNA = stations.stream().filter(st -> st.getHasWasserstand() == null)
						.collect(Collectors.toList());
				countPegel += stationWithNA.size();
			}

			currentPegel.put(status, countPegel);
		}
		return currentPegel;
	}

	private HorizontalLayout createLegendItem(String text, String color) {
		Span circle = new Span();
		circle.getStyle().set("width", "12px").set("height", "12px").set("background-color", color)
				.set("border-radius", "50%").set("display", "inline-block").set("margin-right", "8px");

		Span label = new Span(text);

		HorizontalLayout itemLayout = new HorizontalLayout(circle, label);
		itemLayout.setAlignItems(Alignment.CENTER);
		itemLayout.setAlignItems(Alignment.BASELINE);
		itemLayout.setSpacing(false);
		itemLayout.setPadding(false);

		return itemLayout;
	}

	public Chart createHistoricalWasserStandChart(List<WasserstandDTO> historicalData) {
		Chart chartPegel = new Chart(ChartType.LINE);
		chartPegel.setMinHeight("400px");
		Configuration conf = chartPegel.getConfiguration();
		conf.getChart().setStyledMode(true);

		XAxis xAxis = conf.getxAxis();
		xAxis.setType(AxisType.DATETIME);
		xAxis.setTickInterval(6 * 3600 * 1000);

		conf.addxAxis(xAxis);
		conf.getyAxis().setTitle("[cm]");

		Tooltip tooltip = new Tooltip();
		conf.setTooltip(tooltip);

		PlotOptionsAreaspline plotOptions = new PlotOptionsAreaspline();
		plotOptions.setPointPlacement(PointPlacement.ON);
		plotOptions.setMarker(new Marker(false));
		conf.addPlotOptions(plotOptions);

		DataSeries series = new DataSeries("Wassserstand");

		Map<LocalDateTime, Number> pegelTimeseries = wasserstandService.getPegelTimeseriesData(historicalData);

		if (pegelTimeseries != null) {
			pegelTimeseries.forEach((pegelDateTime, pegelMesswert) -> {
				long millis = pegelDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
				series.add(new DataSeriesItem(millis, pegelMesswert));
			});
		}
		conf.setSeries(series);

		return chartPegel;
	}

	private Chart createVorhersageChart(List<WasserstandDTO> data) {
		Chart chartPegel = new Chart(ChartType.LINE);
		chartPegel.setMinHeight("400px");
		Configuration conf = chartPegel.getConfiguration();
		conf.getChart().setStyledMode(true);

		XAxis xAxis = conf.getxAxis();
		xAxis.setType(AxisType.DATETIME);
		xAxis.setTickInterval(2 * 3600 * 1000);
		xAxis.setTitle(new AxisTitle("Zeitreihe"));

		YAxis yAxis = conf.getyAxis();
		yAxis.setTitle(new AxisTitle("Wassserstand in (cm)"));

		Tooltip tooltip = new Tooltip();
		tooltip.setShared(true);
		tooltip.setValueDecimals(1);
		conf.setTooltip(tooltip);

		DataSeries series = new DataSeries("Wassserstand");
		DateTimeFormatter forecastFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

		for (WasserstandDTO dto : data) {
			try {
				LocalDateTime dateTime = LocalDateTime.parse(dto.getTimespanEnd(), forecastFormatter);
				long timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

				DataSeriesItem point = new DataSeriesItem();
				point.setX(timestamp);
				point.setY(dto.getCurrentMeasuredValue());
				series.add(point);

			} catch (Exception e) {
				log.error("Error parsing date: " + e.getMessage());
			}
		}
		conf.addSeries(series);

		conf.getLegend().setEnabled(true);

		PlotOptionsAreaspline plotOptions = new PlotOptionsAreaspline();
		plotOptions.setPointPlacement(PointPlacement.ON);
		plotOptions.setMarker(new Marker(false));
		conf.addPlotOptions(plotOptions);

		return chartPegel;
	}

}
