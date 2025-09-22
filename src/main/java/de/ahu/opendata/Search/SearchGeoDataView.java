package de.ahu.opendata.Search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import de.ahu.opendata.DataUtils.StationDTO;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Pegeldienst.PegelStationDTO;
import de.ahu.opendata.Pegeldienst.WasserstandService;
import de.ahu.opendata.ServiceUtils.CrawlerFileService;
import de.ahu.opendata.ServiceUtils.ParameterTranslatorService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterParameter;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import de.ahu.opendata.Wetterdienst.WetterdienstService;
import de.ahu.opendata.views.MainLayout;
import de.ahu.opendata.views.OpenLayersMap;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@Route(value = "suche", layout = MainLayout.class)
@Slf4j
public class SearchGeoDataView extends VerticalLayout {

	private WetterdienstService wetterdienstService;
	private CrawlerFileService crawlerFileService;
	private SearchGeoDataService searchOpenGeoDatenService;
	private LocationService locationService;
	private ServiceProviderService serviceProvider;
	private WasserstandService wasserstandService;
	private OpenLayersMap olMap;
	private Konfiguration konfiguration;
	private ComboBox<String> tfSearch = new ComboBox<>();

	private List<WetterStationDTO> historicalWetterStation = null;
	private List<WetterStationDTO> forecastWetterStation = new ArrayList<>();
	private List<WetterStationDTO> openGeoDataNrwStations = new ArrayList<>();
	private List<WetterStationDTO> mergeWetterStation = new ArrayList<>();
	private Set<FileDTO> relevantFiles = new HashSet<>();
	private List<FileDTO> extractedContentFiles = new ArrayList<>();
	private List<PegelStationDTO> pegelStations = new ArrayList<>();;
	private List<StationDTO> mergedStations = new ArrayList<>();

	private HorizontalLayout hlOpenGeoData = new HorizontalLayout();
	private HorizontalLayout btnLayout = new HorizontalLayout();

	private VerticalLayout gridAndFormVerticalLayout = new VerticalLayout();
	private VerticalLayout mapVerticalLayout = new VerticalLayout();

	private Grid<StationDTO> stationGrid = null;

	private String convertValue = "";
	private boolean isGrundWasser = false;

	private Button btnStoreStations = new Button("Stationen speichern");
	private Button btnFilterStations = new Button("Filtern");

	private Map<WetterParameter, List<WetterStationDTO>> stationsByForecastParameter;
	private Map<String, List<? extends StationDTO>> providerList = new HashMap<>();
	private ComboBox<String> ddProvider = new ComboBox<>();

	private H5 countResult = new H5();
	private FormLayout formLayout = new FormLayout();
	private TextField tfMinLatitude = new TextField("Minimaler Breitengrad");
	private TextField tfMaxLatitude = new TextField("Maximaler Breitengrad");
	private TextField tfMinLongitude = new TextField("Minimaler Längengrad");
	private TextField tfMaxLongitude = new TextField("Maximale Längengrad");

	private GridMesstelle gridStation = null;

	public SearchGeoDataView() {
		wetterdienstService = SpringApplicationContext.getBean(WetterdienstService.class);
		crawlerFileService = SpringApplicationContext.getBean(CrawlerFileService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		searchOpenGeoDatenService = SpringApplicationContext.getBean(SearchGeoDataService.class);
		locationService = SpringApplicationContext.getBean(LocationService.class);
		serviceProvider = SpringApplicationContext.getBean(ServiceProviderService.class);
		wasserstandService = SpringApplicationContext.getBean(WasserstandService.class);
		crawlerFileService = new CrawlerFileService(konfiguration.getOwStartUrl(), 5);

		searchOpenGeoDatenService.scheduleCache();

		olMap = new OpenLayersMap();
		olMap.setSizeFull();

		initSearchField();
		initCoordinatesForm();

		btnFilterStations.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		btnFilterStations.getStyle().set("margin-top", "10px");

		ddProvider.setLabel("Servicename(n) auswählen");
		ddProvider.setItemLabelGenerator(String::toString);
		ddProvider.setWidth("20%");

		HorizontalLayout searchLayout = new HorizontalLayout(tfSearch);
		searchLayout.setWidthFull();
		searchLayout.setJustifyContentMode(JustifyContentMode.CENTER);

		formLayout.setWidth("65%");
		formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
		hlOpenGeoData.setSizeFull();
		gridAndFormVerticalLayout.setHeightFull();
		gridAndFormVerticalLayout.setWidth("50%");

		mapVerticalLayout.setHeightFull();
		mapVerticalLayout.setWidth("50%");
		add(searchLayout);
		hlOpenGeoData.add(gridAndFormVerticalLayout, mapVerticalLayout);

		handleChangeListeners();

		add(hlOpenGeoData);
		setSizeFull();
	}

	private void initSearchField() {
		tfSearch.setPlaceholder("🔍 Suche nach Phänomen");
		tfSearch.setClearButtonVisible(true);

		List<String> phaenomenList = List.of("Niederschlag", "Grundwasser", "Pegel", "Bodentemperatur",
				"Lufttemperatur", "Luftfeuchtigkeit", "Windgeschwindigkeit", "Windrichtung", "Windstärke", "Windböe",
				"Sonnenscheindauer", "Gesamtbewölkung");
		tfSearch.setItems(phaenomenList.stream().sorted().toList());

		tfSearch.setAllowCustomValue(true);
		tfSearch.setWidth("20%");
		tfSearch.getStyle().set("borderRadius", "12px").set("padding", "0.5rem 1rem")
				.set("boxShadow", "0 2px 6px rgba(0, 0, 0, 0.1)").set("backgroundColor", "#fff");
	}

	private void initCoordinatesForm() {
		tfMinLatitude.setPlaceholder("Minimaler Breitengrad eingeben (47.2701 bis 55.0581)");
		tfMaxLatitude.setPlaceholder("Maximaler Breitengrad eingeben (47.2701 bis 55.0581)");
		tfMinLongitude.setPlaceholder("Minimaler Längengrad eingeben  (5.8663 bis 15.0419)");
		tfMaxLongitude.setPlaceholder("Maximaler Längegrad eingeben (5.8663 bis 15.0419)");

		tfMinLatitude.setValue("50.323");
		tfMaxLatitude.setValue("52.531");
		tfMinLongitude.setValue("5.866");
		tfMaxLongitude.setValue("9.462");
	}

	private void handleChangeListeners() {
		tfSearch.addValueChangeListener(ev -> {
			addValueChangeSearchGeoData(ev.getValue());
		});

		tfSearch.addCustomValueSetListener(event -> {
			addValueChangeSearchGeoData(event.getDetail());
		});

		btnStoreStations.addClickListener(event -> {
			List<Location> locations = new ArrayList<>();
			mergeWetterStation.forEach(station -> {
				Location location = new Location();
				location.setStationId(station.getStationId());
				location.setLabel(station.getName());
				location.setLatitude(station.getLatitude());
				location.setLongitude(station.getLongitude());
				location.setProvider(serviceProvider.findServiceProvider(station));
				locations.add(location);
			});
			locationService.saveLocations(locations);
			Utils.showHinweisBox("Stationen erfolgreich initialisiert");
		});
	}

	private void addValueChangeSearchGeoData(String value) {
		if (stationGrid != null) {
			gridAndFormVerticalLayout.removeAll();
			ddProvider.removeFromParent();
			countResult.removeFromParent();
			mapVerticalLayout.removeAll();
		}
		isGrundWasser = false;
		historicalWetterStation = new ArrayList<>();
		mergedStations = new ArrayList<>();
		forecastWetterStation = new ArrayList<>();
		providerList = new HashMap<>();

		String searchTerm = value;
		if (searchTerm != null && !searchTerm.isEmpty()) {
			searchTerm = searchTerm.strip().substring(0, 1).toUpperCase()
					+ searchTerm.substring(1, searchTerm.length());
			String translateTerm = ParameterTranslatorService.translateToEnglish(searchTerm);
			if (translateTerm != null) {

				historicalWetterStation = wetterdienstService.searchAllStationsWithParameter(translateTerm, true);
				if (!historicalWetterStation.isEmpty()) {
					historicalWetterStation.forEach(station -> {
						station.setIsHistorical(Boolean.TRUE);
						station.setProvider("Wetterdienst-Observation");
					});
					providerList.put("Wetterdienst-Observation", historicalWetterStation);
					mergeWetterStation.addAll(historicalWetterStation);
				}
				wetterdienstService.searchAllStationsWithParameter(translateTerm, false);
				forecastWetterStation = getForecastWeatherData();
			}

			if (StringUtils.containsIgnoreCase(tfSearch.getValue(), "Pegel")) {
				pegelStations.clear();
				pegelStations = wasserstandService.findAndPegelStation();
				providerList.put("Pegelonline-Dienst", pegelStations);
			} else {
				pegelStations.clear();
			}

			if (StringUtils.containsIgnoreCase(tfSearch.getValue(), "Niederschlag")) {
				convertValue = "Niederschlag";
			} else {
				convertValue = tfSearch.getValue();
			}

			if (StringUtils.containsIgnoreCase(tfSearch.getValue(), "grundwasser")) {
				crawlerFileService = new CrawlerFileService(konfiguration.getGwStartUrl(), 5);
				isGrundWasser = true;
			} else {
				crawlerFileService = new CrawlerFileService(konfiguration.getOwStartUrl(), 5);
			}

			if (isGrundWasser) {
				crawlerFileService.startCrawling(konfiguration.getGwStartUrl());
				FileDTO messtelleFile = crawlerFileService.fetchFoundFiles().stream()
						.filter(file -> StringUtils.containsIgnoreCase(file.getUrl(), "gw-messstelle")).findFirst()
						.orElse(null);

				relevantFiles = crawlerFileService.fetchFoundFiles().stream()
						.filter(file -> StringUtils.containsIgnoreCase(file.getUrl(), "gw-wasserstand"))
						.collect(Collectors.toSet());
				relevantFiles = relevantFiles.stream()
						.filter(file -> StringUtils.containsIgnoreCase(file.getUrl(), "2010-2019")
								|| StringUtils.containsIgnoreCase(file.getUrl(), "2020-2029"))
						.collect(Collectors.toSet());
				relevantFiles.add(messtelleFile);

			} else {
				crawlerFileService.startCrawling(konfiguration.getOwStartUrl());
				relevantFiles = crawlerFileService.fetchFoundFiles().stream()
						.filter(file -> StringUtils.containsIgnoreCase(file.getUrl(), convertValue))
						.filter(file -> !StringUtils.containsIgnoreCase(file.getUrl(), "_Shape"))
						.collect(Collectors.toSet());
			}
			if (!relevantFiles.isEmpty()) {
				extractedContentFiles.clear();
				extractedContentFiles = searchOpenGeoDatenService.extractContentZipFiles(this.relevantFiles);
				openGeoDataNrwStations.clear();
				if (isGrundWasser) {
					openGeoDataNrwStations = searchOpenGeoDatenService
							.extractGrundWasserStationsFromFile(extractedContentFiles);
				} else {
					openGeoDataNrwStations = searchOpenGeoDatenService.extractStationsFromFile(extractedContentFiles);
				}
				mergeWetterStation.addAll(openGeoDataNrwStations);
				providerList.put("OpenGeoData-NRW", openGeoDataNrwStations);
			} else {
				openGeoDataNrwStations = new ArrayList<>();
			}

			if (!pegelStations.isEmpty()) {
				mergedStations = Stream.concat(pegelStations.stream(), openGeoDataNrwStations.stream())
						.sorted(Comparator.comparing(StationDTO::getName)).collect(Collectors.toList());
			} else {
				mergedStations = Stream
						.concat(historicalWetterStation.stream(),
								Stream.concat(openGeoDataNrwStations.stream(), forecastWetterStation.stream()))
						.sorted(Comparator.comparing(StationDTO::getName)).collect(Collectors.toList());
			}
			btnLayout.setAlignItems(Alignment.BASELINE);
			btnLayout.setWidth("50%");

			countResult.setText(mergedStations.size() + " Ergebnisse gefunden zu " + searchTerm);
			btnLayout.add(countResult);
			ddProvider.setItems(providerList.keySet());
			formLayout.add(tfMinLatitude, tfMinLongitude, tfMaxLatitude, tfMaxLongitude, ddProvider, btnFilterStations);
			gridAndFormVerticalLayout.add(formLayout, btnLayout);

			if (gridStation != null) {
				gridStation.messstelleGrid.removeFromParent();
				gridStation.olMap.removeFromParent();
			}
			gridStation = new GridMesstelle(mergedStations, extractedContentFiles, stationsByForecastParameter,
					providerList, searchTerm, isGrundWasser, gridAndFormVerticalLayout, mapVerticalLayout);
		}
	}

	private List<WetterStationDTO> getForecastWeatherData() {
		List<WetterStationDTO> firstForecastStationsList = new ArrayList<>();
		stationsByForecastParameter = wetterdienstService.getStationWithForecastParameter();
		if (!stationsByForecastParameter.isEmpty()) {
			WetterParameter firstKey = stationsByForecastParameter.keySet().iterator().next();
			firstForecastStationsList = stationsByForecastParameter.get(firstKey);
			mergeWetterStation.addAll(firstForecastStationsList);
			providerList.put("Wetterdienst-Vorhersage", firstForecastStationsList);
		}
		return firstForecastStationsList;
	}

}
