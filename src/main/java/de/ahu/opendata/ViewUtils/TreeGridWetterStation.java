package de.ahu.opendata.ViewUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.Abonnement.AbonnementDialogView;
import de.ahu.opendata.DataUtils.DataEvents;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.ServiceUtils.ParameterTranslatorService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterDatenDTO;
import de.ahu.opendata.Wetterdienst.WetterParameter;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import de.ahu.opendata.Wetterdienst.WetterdienstService;

@SuppressWarnings("serial")
public class TreeGridWetterStation extends VerticalLayout {
	private WetterdienstService wetterdienstService;
	private Konfiguration konfiguration;

	private TreeGrid<WetterStationDTO> wetterstationTreeGrid = null;
	private TreeGrid<WetterStationDTO> gridForcastWetterstation = null;

	private Anchor linkDwdObserveration = new Anchor(
			"https://wetterdienst.readthedocs.io/en/latest/data/provider/dwd/observation/", "Observation-Modell");
	private Anchor linkDwdForcast = new Anchor(
			"https://wetterdienst.readthedocs.io/en/latest/data/provider/dwd/mosmix/", "Mosmix-Modell");

	private String highestCountResolution = "";
	private AbonnementDialogView abonnementDialogView;

	private VerticalLayout observationLayout = new VerticalLayout();
	private VerticalLayout vlWetterdienst = new VerticalLayout();
	private HorizontalLayout hlWetterStation = new HorizontalLayout();
	private VerticalLayout forcastLayout = new VerticalLayout();

	private List<WetterStationDTO> mergeWetterStation = new ArrayList<>();
	private List<WetterStationDTO> historicalWetterStation = new ArrayList<>();

	private Select<String> ddRollenTyp = new Select<>();

	public TreeGridWetterStation() {
		wetterdienstService = SpringApplicationContext.getBean(WetterdienstService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);

		ddRollenTyp.setLabel("Rollen-Typ");
		ddRollenTyp.setItems(List.of("MeteorologischeMessstelle", "Grundwassermessstelle", "Gewaesser"));
		ddRollenTyp.setWidth("25%");

		initGridHistoricalWetterStations();
		initGridForcastWetterStation();
	}

	public void initGridHistoricalWetterStations() {
		if (wetterstationTreeGrid != null) {
			observationLayout.removeAll();
			vlWetterdienst.removeAll();
			hlWetterStation.remove(vlWetterdienst);

		}
		wetterstationTreeGrid = new TreeGrid<>();
		wetterstationTreeGrid.setAriaLabel("Wetterstationen mit historischen Daten");
		wetterstationTreeGrid.setSizeFull();
		wetterstationTreeGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
		wetterstationTreeGrid.addClassNames(LumoUtility.Margin.Right.SMALL);

		TreeData<WetterStationDTO> treeData = new TreeData<>();

		Map<String, WetterStationDTO> resolutionNodes = new HashMap<>();

		Map<String, List<WetterStationDTO>> stationsByResolution = new HashMap<>();

		Set<WetterStationDTO> processedStations = new HashSet<>();

		for (WetterStationDTO station : historicalWetterStation) {
			if (processedStations.add(station)) {
				String resolution = ParameterTranslatorService.translateToGerman(station.getResolution());
				if (resolution != null) {
					stationsByResolution.computeIfAbsent(resolution, k -> new ArrayList<>()).add(station);
				}
			}
		}

		List<String> sortedResolutions = new ArrayList<>(stationsByResolution.keySet());
		sortedResolutions.sort(String::compareTo);

		Map<String, Integer> resolutionCount = new HashMap<>();
		Map<String, Set<WetterStationDTO>> stationsByResolutionCount = new HashMap<>();

		for (String resolution : sortedResolutions) {
			WetterStationDTO resolutionNode = new WetterStationDTO();
			treeData.addRootItems(resolutionNode);
			resolutionNodes.put(resolution, resolutionNode);

			List<WetterStationDTO> stations = stationsByResolution.get(resolution);
			Set<WetterStationDTO> sortedStations = stations.stream().collect(
					Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(WetterStationDTO::getName))));

			resolutionNode.setName(resolution + "-" + sortedStations.size());

			resolutionCount.put(resolution, sortedStations.size());
			stationsByResolutionCount.put(resolution, sortedStations);
			for (WetterStationDTO station : sortedStations) {
				treeData.addItem(resolutionNode, station);
			}
		}
		resolutionCount = Utils.sortedDescendingMapByValue(resolutionCount);

		highestCountResolution = (String) resolutionCount.keySet().toArray()[0];
		Set<WetterStationDTO> stationsWithHighestCount = stationsByResolutionCount.get(highestCountResolution);
		stationsWithHighestCount.forEach(station -> station.setIsForecast(Boolean.TRUE));
		mergeWetterStation.addAll(stationsWithHighestCount);

		TreeDataProvider<WetterStationDTO> dataProvider = new TreeDataProvider<>(treeData);
		wetterstationTreeGrid.setDataProvider(dataProvider);
		wetterstationTreeGrid.setAllRowsVisible(false);

		wetterstationTreeGrid.addHierarchyColumn(station -> station.getName()).setHeader("Auflösung").setFlexGrow(2)
				.setSortable(true);

		wetterstationTreeGrid.addColumn(station -> station.getLatitude()).setHeader("Breitengrad");

		wetterstationTreeGrid.addColumn(station -> station.getLongitude()).setHeader("Längengrad");

		wetterstationTreeGrid.addColumn(station -> station.getStartDate()).setHeader("Startdatum").setFlexGrow(2);

		wetterstationTreeGrid.addColumn(station -> station.getEndDate()).setHeader("Enddatum").setFlexGrow(2);

		wetterstationTreeGrid.addComponentColumn(station -> {
			if (station.getLatitude() == null) {
				return null;
			}
			Button btnSubscribe = new Button("Abonnieren");
			btnSubscribe.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
			btnSubscribe.addClickListener(click -> {
//				String url = String
//						.format(konfiguration.getWetterdienstBaseUrlObservation() + station.getPathParameter()
//								+ "&station=%s&date=" + Utils.formatDateOnly(station.getStartDate()) + "/"
//								+ Utils.formatDateOnly(station.getEndDate()), station.getStationId());
//				List<WetterDatenDTO> wetterDaten = wetterdienstService.fetchWetterDaten(url);
//				if (wetterDaten.isEmpty()) {
//					Utils.showHinweisBox("Keine Daten vorhanden");
//					return;
//				}
//				wetterDaten.get(0).setParameter(station.getPathParameter());
//				wetterDaten.get(0).setId(station.getStationId());
//				wetterDaten.get(0).setStationId(station.getName());
//
//				if (abonnementDialogView != null) {
//					abonnementDialogView.removeFromParent();
//				}
//				abonnementDialogView = new AbonnementDialogView("historisch", "observation");
//				add(abonnementDialogView);
//				ComponentUtil.fireEvent(UI.getCurrent(), new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(),
//						true, List.of(wetterDaten.get(0), wetterDaten.get(wetterDaten.size() - 1)), ""));

			});

			return btnSubscribe;
		});

		linkDwdObserveration.setTarget("_blank");

		H4 title = new H4("Wetterstationen mit historischen Daten");
		HorizontalLayout titleLayout = new HorizontalLayout(title, linkDwdObserveration);
		titleLayout.setAlignItems(Alignment.BASELINE);
		titleLayout.setJustifyContentMode(JustifyContentMode.CENTER);
		observationLayout.setSizeFull();
		observationLayout.add(titleLayout, wetterstationTreeGrid);

		hlWetterStation.add(observationLayout);
	}

	public void initGridForcastWetterStation() {
		if (gridForcastWetterstation != null) {
			forcastLayout.removeAll();
		}
		Map<WetterParameter, List<WetterStationDTO>> stationsByParameter = wetterdienstService
				.getStationWithForecastParameter();
		if (stationsByParameter.isEmpty()) {
			hlWetterStation.remove(forcastLayout);
			return;
		}
		gridForcastWetterstation = new TreeGrid<>();
		gridForcastWetterstation.setSizeFull();
		gridForcastWetterstation.setSelectionMode(Grid.SelectionMode.SINGLE);
		gridForcastWetterstation.addClassNames(LumoUtility.Margin.Right.SMALL);

		TreeData<WetterStationDTO> treeData = new TreeData<>();

		for (Map.Entry<WetterParameter, List<WetterStationDTO>> entry : stationsByParameter.entrySet()) {
			String parameter = entry.getKey().getOriginalName();
			List<WetterStationDTO> stations = entry.getValue();
			stations.sort(Comparator.comparing(WetterStationDTO::getName));
			WetterStationDTO parameterNode = new WetterStationDTO();
			parameterNode.setName(parameter);

			treeData.addItem(null, parameterNode);

			for (WetterStationDTO station : stations) {
				treeData.addItem(parameterNode, station);
			}
		}
		TreeDataProvider<WetterStationDTO> dataProvider = new TreeDataProvider<>(treeData);
		gridForcastWetterstation.setDataProvider(dataProvider);
		gridForcastWetterstation.setAllRowsVisible(false);

		linkDwdForcast.setTarget("_blank");
		H4 title = new H4("Wetterstationen mit Vorhersagedaten");
		forcastLayout.setSizeFull();
		HorizontalLayout titleLayout = new HorizontalLayout(title, linkDwdForcast);
		titleLayout.setAlignItems(Alignment.BASELINE);
		titleLayout.setJustifyContentMode(JustifyContentMode.CENTER);

		gridForcastWetterstation.addHierarchyColumn(station -> station.getName()).setHeader("Parameter")
				.setAutoWidth(true).setSortable(true).setResizable(true);
		gridForcastWetterstation.addColumn(station -> station.getStationId()).setHeader("Station-Id")
				.setAutoWidth(true);
		gridForcastWetterstation.addColumn(station -> station.getLatitude()).setHeader("Breitengrad")
				.setAutoWidth(true);
		gridForcastWetterstation.addColumn(station -> station.getLongitude()).setHeader("Längengrad")
				.setAutoWidth(true);
		gridForcastWetterstation.addComponentColumn(station -> {
			if (station.getLatitude() == null) {
				return null;
			}
			Button btnSubscribe = new Button("Abonnieren");
			btnSubscribe.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

			btnSubscribe.addClickListener(click -> {
				String url = String.format(
						konfiguration.getWetterdienstBaseUrlMosmix() + "hourly/large/"
								+ station.getPathParameters().getFirst().getOriginalName() + "&station=%s",
						station.getStationId());
				List<WetterDatenDTO> wetterDaten = wetterdienstService.fetchWetterDaten(url);
				if (wetterDaten.isEmpty()) {
					Utils.showHinweisBox("Keine Daten vorhanden");
					return;
				}
				wetterDaten.get(0).setParameter(station.getPathParameters().getFirst().getOriginalName());
				wetterDaten.get(0).setId(station.getStationId());
				wetterDaten.get(0).setStationId(station.getName());

				if (abonnementDialogView != null) {
					abonnementDialogView.removeFromParent();
				}
				abonnementDialogView = new AbonnementDialogView("vorhersage", "mosmix");
				add(abonnementDialogView);
				ComponentUtil.fireEvent(UI.getCurrent(), new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(),
						true, List.of(wetterDaten.get(0), wetterDaten.get(wetterDaten.size() - 1)), ""));

			});
			return btnSubscribe;
		});

		forcastLayout.add(titleLayout, gridForcastWetterstation);

		hlWetterStation.add(forcastLayout);
	}
//	private void getHistoricalStationByResolutionCount() {
//	Map<String, List<WetterStationDTO>> stationsByResolution = new HashMap<>();
//
//	Set<WetterStationDTO> processedStations = new HashSet<>();
//
//	for (WetterStationDTO station : historicalWetterStation) {
//		if (processedStations.add(station)) {
//			String resolution = ParameterTranslatorService.translateToGerman(station.getResolution());
//			if (resolution != null) {
//				stationsByResolution.computeIfAbsent(resolution, k -> new ArrayList<>()).add(station);
//			}
//		}
//	}
//
//	List<String> sortedResolutions = new ArrayList<>(stationsByResolution.keySet());
//	sortedResolutions.sort(String::compareTo);
//
//	Map<String, Integer> resolutionCount = new HashMap<>();
//	Map<String, Set<WetterStationDTO>> stationsByResolutionCount = new HashMap<>();
//
//	for (String resolution : sortedResolutions) {
//		WetterStationDTO resolutionNode = new WetterStationDTO();
//
//		List<WetterStationDTO> stations = stationsByResolution.get(resolution);
//		Set<WetterStationDTO> sortedStations = stations.stream().collect(
//				Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(WetterStationDTO::getName))));
//
//		resolutionNode.setName(resolution + "-" + sortedStations.size());
//
//		resolutionCount.put(resolution, sortedStations.size());
//		stationsByResolutionCount.put(resolution, sortedStations);
//	}
//	resolutionCount = Utils.sortedDescendingMapByValue(resolutionCount);
//
//	highestCountResolution = (String) resolutionCount.keySet().toArray()[0];
//	Set<WetterStationDTO> stationsWithHighestCount = stationsByResolutionCount.get(highestCountResolution);
//	stationsWithHighestCount.forEach(station -> {
//		station.setIsHistorical(Boolean.TRUE);
//		station.setProvider("Wetterdienst-Observation");
//	});
//	mergeWetterStation.addAll(stationsWithHighestCount);
//}
}
