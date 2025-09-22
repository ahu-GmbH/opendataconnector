package de.ahu.opendata.Wetterdienst;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.Abonnement.Abonnement;
import de.ahu.opendata.Abonnement.AbonnementDialogView;
import de.ahu.opendata.Abonnement.AbonnementService;
import de.ahu.opendata.DataUtils.DataEvents;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.ServiceUtils.ParameterTranslatorService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.views.MainLayout;
import de.ahu.opendata.views.OpenLayersMap;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@Slf4j
@Route(value = "historisch", layout = MainLayout.class)
public class HistoricalWetterDatenView extends VerticalLayout {

	private final WetterdienstService wetterdienstService;
	private final AbonnementService abonnementService;
	private final Konfiguration konfiguration;
	private final Select<String> ddResolution = new Select<>();
	private final Select<String> ddMainParameter = new Select<>();
	private final Select<String> ddParameter = new Select<>();
	private final Select<String> ddStates = new Select<>();
	private final ComboBox<WetterStationDTO> ddStation = new ComboBox<>();
	private final ComboBox<WetterStationDTO> ddFilterStation = new ComboBox<>();
	private final DatePicker startDate = new DatePicker("Startdatum");
	private final DatePicker endDate = new DatePicker("Enddatum");

	private VerticalLayout vlParameters;
	private final VerticalLayout vlBaseData = new VerticalLayout();
	private final VerticalLayout vlMap = new VerticalLayout();
	private final VerticalLayout vlDownloadFile = new VerticalLayout();
	private HorizontalLayout hlStationSearch = new HorizontalLayout();
	private WetterDatenChart wetterChart = new WetterDatenChart();

	private ParameterNode rootNode = null;
	private List<WetterDatenDTO> wetterDaten = Collections.emptyList();
	private List<WetterStationDTO> allStationen = Collections.emptyList();
	private List<WetterStationDTO> stationInState = Collections.emptyList();
	private Button btnFilterStation = new Button(new Icon(VaadinIcon.SEARCH_PLUS));
	private Button csvDownload = new Button(new Icon(VaadinIcon.CLOUD_DOWNLOAD_O));

	private Button btnSubscribe = new Button();

	private String wetterdienstBaseUrl;

	private OpenLayersMap olMap;

	private HorizontalLayout dataHorizontalLayout = new HorizontalLayout();

	private Div wrapperChart = new Div();
	private AbonnementDialogView abonnementDialogView = null;

	public HistoricalWetterDatenView() {
		wetterdienstService = SpringApplicationContext.getBean(WetterdienstService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		abonnementService = SpringApplicationContext.getBean(AbonnementService.class);

		wetterdienstBaseUrl = konfiguration.getWetterdienstBaseUrl();

		rootNode = wetterdienstService.parse();
		if (rootNode != null) {
			configureSelects(rootNode);
		}

		startDate.setValue(LocalDate.now().minusYears(1));
		endDate.setValue(LocalDate.now().minusDays(1));

		ddStation.setLabel("Stationen");

		ddStates.setWidthFull();
		ddStates.setLabel("Bundesland");
		ddStates.setItems(List.of("Baden-Württemberg", "Bayern", "Berlin", "Brandenburg", "Bremen", "Hamburg", "Hessen",
				"Mecklenburg-Vorpommern", "Niedersachsen", "Nordrhein-Westfalen", "Rheinland-Pfalz", "Saarland",
				"Sachsen", "Sachsen-Anhalt", "Schleswig-Holstein", "Thüringen"));

		ddResolution.setValue("täglich");
		ddMainParameter.setValue("Niederschlag mehr");
		// ddStates.setValue("Nordrhein-Westfalen");
		Stream.of(ddResolution, ddMainParameter, ddParameter).forEach(select -> {
			select.setWidthFull();
		});
		ddStation.setWidthFull();

		vlBaseData.add(new H3("Basis Daten:"));
		startDate.setWidthFull();
		endDate.setWidthFull();
		FlexLayout flexDateLayout = new FlexLayout(startDate, endDate);
		flexDateLayout.setWidthFull();
		flexDateLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
		flexDateLayout.setJustifyContentMode(FlexLayout.JustifyContentMode.CENTER);
		flexDateLayout.setFlexGrow(1.0, startDate, endDate);
		flexDateLayout.addClassNames(LumoUtility.Gap.MEDIUM);

		vlBaseData.add(flexDateLayout);
		vlBaseData.setWidth("50%");
		vlBaseData.addClassNames(LumoUtility.Border.RIGHT, LumoUtility.Padding.Right.SMALL,
				LumoUtility.Padding.Left.SMALL);

		HorizontalLayout hlForms = new HorizontalLayout();
		hlForms.setPadding(true);
		vlDownloadFile.setAlignItems(Alignment.START);

		hlForms.add(vlParameters, vlBaseData);
		hlForms.setSizeFull();

		Anchor link = new Anchor("https://opendata.dwd.de/climate_environment/CDC/observations_germany/climate/",
				"Fileservers");
		link.setTarget("_blank");

		Paragraph pgOverView = new Paragraph();
		H3 h3Header = new H3("Übersicht-Oberservation");
		h3Header.addClassName(LumoUtility.Padding.SMALL);
		pgOverView.add(h3Header, new Span(
				"Der wertvolle Datenschatz des DWD liegt verborgen in der komplexen Struktur eines umfangreichen "),
				link, new Span(
						". Die hier verfügbaren Daten reichen bis ins 19. Jahrhundert zurück und spiegeln Messwerte von über 1000 Wetterstationen in ganz Deutschland wider. Dabei variiert die Anzahl der Stationen, die bestimmte Messgrößen erfassen, zum Teil erheblich – daher sollte man nicht erwarten, dass für jeden Parameter gleich viele Daten vorliegen."));
		pgOverView.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM, LumoUtility.Background.BASE);

		VerticalLayout dataVerticalLayout = new VerticalLayout();
		dataVerticalLayout.setHeightFull();
		dataVerticalLayout.add(pgOverView);
		dataVerticalLayout.add(hlForms);
		dataVerticalLayout.setWidth("75%");

		olMap = new OpenLayersMap();
		olMap.setSizeFull();

		dataHorizontalLayout.add(dataVerticalLayout, olMap);
		dataHorizontalLayout.setWidthFull();
		dataHorizontalLayout.setHeight("750px");

		wrapperChart.setSizeFull();
		dataHorizontalLayout.addClassName(LumoUtility.Margin.Bottom.LARGE);
		wrapperChart.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Padding.Top.LARGE);

		ddStation.addValueChangeListener(e -> {
			if (ddStation.getValue() != null) {
				clickListenerParameter();
				vlMap.removeAll();
				if (olMap == null) {
					olMap = new OpenLayersMap();
				}
				olMap.showStationsOnMap(List.of(ddStation.getValue()));
			}
		});

		ddParameter.addValueChangeListener(e -> {
			if (e.getValue() == null) {
				return;
			}
			String resolution = findEnglishName(rootNode.getChildren(), ddResolution.getValue());
			String mainParam = findMainParam(resolution);
			allStationen = wetterdienstService.fetchWetterstationen(resolution + "/" + mainParam, true);
			ddStation.setItems(allStationen.stream().sorted((a, b) -> a.getName().compareTo(b.getName()))
					.collect(Collectors.toList()));
			ddStation.setHelperText("Anzahl Stationen  : " + allStationen.size());
			ddStation.setItemLabelGenerator(station -> station.getName() + " (" + station.getState() + ")");

			if (ddStates.getValue() != null) {
				ddStates.setValue(null);
			}

			ddFilterStation.removeFromParent();
			wetterChart.removeFromParent();
			vlDownloadFile.removeFromParent();

			hlStationSearch.setAlignItems(Alignment.BASELINE);
			hlStationSearch.add(ddStation, btnFilterStation);
			hlStationSearch.setWidthFull();
			vlBaseData.add(ddStates, hlStationSearch);

			if (olMap == null) {
				olMap = new OpenLayersMap();
			}
			olMap.showStationsOnMap(allStationen);
		});

		ddStates.addValueChangeListener(click -> {
			if (ddStates.getValue() == null) {
				return;
			}
			stationInState = allStationen.stream().filter(station -> station.getState().equals(ddStates.getValue()))
					.sorted((a, b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList());

			ddStation.setItems(stationInState);
			ddStation.setHelperText("Anzahl Stationen  : " + stationInState.size());

			if (ddStation.getValue() != null || ddStates.getValue() != null) {
				olMap.showStationsOnMap(stationInState);
			}

			ddFilterStation.removeFromParent();
			wetterChart.removeFromParent();

			if (ddParameter.getValue() != null) {
				clickListenerParameter();
			}
		});

		ddFilterStation.setLabel("Stationen mit Werten");
		btnFilterStation.setTooltipText("Stationen mit Werten");
		ddFilterStation.setWidthFull();

		ddFilterStation.addValueChangeListener(e -> {

			ddStation.setValue(e.getValue());
			clickListenerParameter();
			if (e.getValue() != null) {
				olMap.showStationsOnMap(List.of(e.getValue()));
				readToCSV();
			}
		});

		btnFilterStation.addClickListener(click -> {
			String resolution = findEnglishName(rootNode.getChildren(), ddResolution.getValue());
			String mainParam = findMainParam(resolution);
			String subParam = findEnglishName(rootNode.getChildren().stream()
					.filter(node -> node.getName().equals(resolution)).findFirst().get().getChildren().stream()
					.filter(node -> node.getName().equals(mainParam)).findFirst().get().getChildren(),
					ddParameter.getValue() != null ? ddParameter.getValue() : null);

			List<WetterStationDTO> stationHasValues = new ArrayList<>();
			int chunkSize = 50;

			List<List<WetterStationDTO>> stationChunks = new ArrayList<>();
			for (int i = 0; i < stationInState.size(); i += chunkSize) {
				stationChunks.add(stationInState.subList(i, Math.min(i + chunkSize, stationInState.size())));
			}

			for (List<WetterStationDTO> chunk : stationChunks) {
				String stationIds = chunk.stream().map(WetterStationDTO::getStationId).collect(Collectors.joining(","));

				String url = String.format(
						wetterdienstBaseUrl
								+ "values?provider=dwd&network=observation&parameters=%s/%s/%s&station=%s&date=%s/%s",
						resolution, mainParam, subParam, stationIds, startDate.getValue().toString(),
						endDate.getValue().toString());
				stationHasValues.addAll(wetterdienstService.hasStationValues(url, chunk));
			}

			ddFilterStation.setItems(stationHasValues.isEmpty() ? Collections.emptyList()
					: stationHasValues.stream().sorted(Comparator.comparing(WetterStationDTO::getName))
							.collect(Collectors.toList()));
			ddFilterStation.setHelperText("Anzahl Stationen  : " + stationHasValues.size());
			ddFilterStation.setItemLabelGenerator(station -> station.getName() + " (" + station.getState() + ")");

			if (!stationHasValues.isEmpty()) {
				vlBaseData.add(ddFilterStation);
				vlMap.removeAll();
				olMap.showStationsOnMap(stationHasValues);

			} else {
				Utils.showHinweisBox("Keine Stationen mit Werten gefunden.", 3000);
			}

		});

		add(dataHorizontalLayout, wrapperChart);

	}

	private void clickListenerParameter() {

		String resolution = findEnglishName(rootNode.getChildren(), ddResolution.getValue());
		String mainParam = findMainParam(resolution);

		String subParam = findEnglishName(rootNode.getChildren().stream()
				.filter(node -> node.getName().equals(resolution)).findFirst().get().getChildren().stream()
				.filter(node -> node.getName().equals(mainParam)).findFirst().get().getChildren(),
				ddParameter.getValue() != null ? ddParameter.getValue() : null);

		String stationId = ddStation.getValue() != null ? ddStation.getValue().getStationId() : null;

		if (stationId != null) {
			String url = String.format(
					wetterdienstBaseUrl + "values?provider=dwd&network=observation&parameters=%s/%s/%s&station=%s&date="
							+ startDate.getValue().toString() + "/" + endDate.getValue().toString(),
					resolution, mainParam, subParam, stationId);
			wetterDaten = wetterdienstService.fetchWetterDaten(url);
			wetterChart.displayWetterDataChart(wetterDaten, ddParameter.getValue(), ddStation.getValue(),
					ddResolution.getValue());
			wetterChart.setWidthFull();
			wrapperChart.add(wetterChart);

			if (wetterDaten.isEmpty()) {
				return;
			}

			String parameterUrl = resolution + "/" + mainParam + "/" + subParam;
			wetterDaten.get(0).setParameter(parameterUrl);
			wetterDaten.get(0).setId(stationId);
			wetterDaten.get(0).setStationId(ddStation.getValue().getName());

			Abonnement alreadySubscribed = abonnementService.checkAlreadySubscribed(wetterDaten);

			if (alreadySubscribed != null) {
				btnSubscribe.setText("Abonniert");
				btnSubscribe.setIcon(new Icon(VaadinIcon.BELL));
			} else {
				btnSubscribe.setText("Abonnieren");
				btnSubscribe.setIcon(new Icon(VaadinIcon.BELL_O));
			}

			btnSubscribe.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
			btnSubscribe.addClickListener(click -> {
				if (!wetterDaten.isEmpty()) {
					String route = RouteConfiguration.forSessionScope().getUrl(HistoricalWetterDatenView.class);

					if (abonnementDialogView != null) {
						abonnementDialogView.removeFromParent();
					}
					abonnementDialogView = new AbonnementDialogView(route, "observation");
					add(abonnementDialogView);
					ComponentUtil.fireEvent(UI.getCurrent(),
							new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(), true,
									List.of(wetterDaten.get(0), wetterDaten.get(wetterDaten.size() - 1)), ""));
				}
			});

			readToCSV();
		}
	}

	private void readToCSV() {
		if (!wetterDaten.isEmpty()) {
			vlDownloadFile.removeAll();
			StreamResource resource = wetterdienstService.createCsvStreamResource(wetterDaten);
			csvDownload.setText(ddParameter.getValue() + "_" + ddResolution.getValue() + ".csv");
			Anchor downloadLink = new Anchor(resource, "");
			downloadLink.getElement().setAttribute("download", true);
			downloadLink.add(csvDownload);

			vlDownloadFile.addClassNames(LumoUtility.Border.TOP, LumoUtility.Padding.Top.MEDIUM);
			vlDownloadFile.setWidthFull();
			// vlDownloadFile.add(new H5(ddParameter.getValue() + "-Werte als CSV-Datei "));
			// vlDownloadFile.add(btnSubscribe);
			vlDownloadFile.setJustifyContentMode(JustifyContentMode.START);
			vlDownloadFile.setAlignItems(Alignment.START);
			vlParameters.add(vlDownloadFile);

		} else {
			if (wrapperChart != null) {
				wrapperChart.removeAll();
				btnSubscribe.removeFromParent();
			}
		}
	}

	private String findMainParam(String resolution) {
		return findEnglishName(rootNode.getChildren().stream().filter(node -> node.getName().equals(resolution))
				.findFirst().get().getChildren(),
				ddMainParameter.getValue() != null ? ddMainParameter.getValue() : null);
	}

	private void configureSelects(ParameterNode root) {
		List<String> mainCategories = root.getChildren().stream()
				.map(node -> ParameterTranslatorService.translateToGerman(node.getName())).collect(Collectors.toList());
		ddResolution.setLabel("Auflösung");
		ddResolution.setItems(mainCategories);
		ddResolution.addValueChangeListener(e -> {

			ddStates.removeFromParent();
			hlStationSearch.removeFromParent();
			ddFilterStation.removeFromParent();
			wetterChart.removeAll();
			vlDownloadFile.removeFromParent();
			vlMap.removeAll();

			updateMainParamSelect(root, e.getValue());
		});

		ddMainParameter.setLabel("Hauptparameter");
		ddMainParameter.addValueChangeListener(e -> {
			if (e.getValue() != null) {
				updateSubParamSelect(root, e.getValue());
			}
		});

		ddParameter.setLabel("Unterparameter");

		vlParameters = new VerticalLayout();

		vlParameters.add(new H3("Parametern auswählen:"), ddResolution);
		vlParameters.setWidth("50%");
		vlParameters.addClassNames(LumoUtility.Border.RIGHT, LumoUtility.Padding.Right.SMALL);

	}

	private void updateMainParamSelect(ParameterNode root, String mainCategory) {
		String englishMainCategory = findEnglishName(root.getChildren(), mainCategory);

		if (englishMainCategory == null)
			return;

		List<ParameterNode> mainNode = root.getChildren().stream().filter(n -> n.getName().equals(englishMainCategory))
				.findFirst().get().getChildren();

		if (!mainNode.isEmpty()) {
			List<String> subCategories = mainNode.stream()
					.map(node -> ParameterTranslatorService.translateToGerman(node.getName()))
					.collect(Collectors.toList());
			ddMainParameter.setItems(subCategories);
			ddMainParameter.setEnabled(true);
		} else {
			ddMainParameter.setItems(Collections.emptyList());
			ddMainParameter.setEnabled(false);
		}
		vlParameters.add(ddMainParameter, ddParameter);
		ddParameter.clear();
		ddParameter.setEnabled(false);
	}

	private void updateSubParamSelect(ParameterNode root, String subCategory) {
		String englishMainCategory = findEnglishName(root.getChildren(), ddResolution.getValue());
		if (englishMainCategory == null)
			return;

		List<ParameterNode> subNotes = root.getChildren().stream()
				.filter(node -> node.getName().equals(englishMainCategory)).findFirst().get().getChildren();

		String englishSubCategory = findEnglishName(subNotes, subCategory);

		if (englishSubCategory == null)
			return;

		if (englishSubCategory != null) {
			List<String> params = subNotes.stream().filter(n -> n.getName().equals(englishSubCategory)).findFirst()
					.get().getChildren().stream()
					.map(node -> ParameterTranslatorService.translateToGerman(node.getName()))
					.collect(Collectors.toList());
			if (params.isEmpty()) {
				ddParameter.setItems(Collections.emptyList());
				ddParameter.setEnabled(false);
			} else {
				ddParameter.setItems(params);
				ddParameter.setEnabled(true);
			}
		}
	}

	private String findEnglishName(List<ParameterNode> nodes, String germanName) {
		for (ParameterNode node : nodes) {
			if (ParameterTranslatorService.translateToGerman(node.getName()).equals(germanName)) {
				return node.getName();
			}
		}
		return null;
	}
}
