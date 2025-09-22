package de.ahu.opendata.Wetterdienst;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import de.ahu.opendata.Abonnement.Abonnement;
import de.ahu.opendata.Abonnement.AbonnementDialogView;
import de.ahu.opendata.Abonnement.AbonnementService;
import de.ahu.opendata.DataUtils.DataEvents;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.ServiceUtils.GeolocationService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.views.MainLayout;
import de.ahu.opendata.views.OpenLayersMap;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@Slf4j
@Route(value = "vorhersage", layout = MainLayout.class)
public class WettervorhersageView extends VerticalLayout {

	private final WetterdienstService wetterdienstService;

	private final GeolocationService geolocationService;
	private final AbonnementService abonnementService;

	private final Konfiguration konfiguration;

	private Grid<WetterParameter> grid = new Grid<>(WetterParameter.class, false);

	private ComboBox<WetterStationDTO> ddWetterstation = new ComboBox<WetterStationDTO>();

	private HorizontalLayout horizontalLayout = new HorizontalLayout();
	private VerticalLayout verticalLayout = new VerticalLayout();

	private WetterDatenChart wetterDatenChart = new WetterDatenChart();
	private Button btncheckAvaible = new Button(new Icon(VaadinIcon.SEARCH_PLUS));

	private List<WetterDatenDTO> weatherData;

	private List<WetterParameter> dataList;

	private Button btnShowMap = new Button(new Icon(VaadinIcon.MAP_MARKER));

	private AbonnementDialogView abonnementDialogView = null;

	private Abonnement alreadySubscribed;

	private List<WetterStationDTO> stationWithValues = new ArrayList<>();
	private String route = "";

	public WettervorhersageView() {
		wetterdienstService = SpringApplicationContext.getBean(WetterdienstService.class);
		geolocationService = SpringApplicationContext.getBean(GeolocationService.class);
		abonnementService = SpringApplicationContext.getBean(AbonnementService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		route = RouteConfiguration.forSessionScope().getUrl(WettervorhersageView.class);

		prepareGrid();
		dataList = wetterdienstService.loadMosmixParameterList();
		grid.setItems(dataList);

		geolocationService.initializeGermanGeometry();
		List<WetterStationDTO> allStationDTOs = wetterdienstService
				.fetchAndFilterGermanStations(geolocationService.germanyGeometry());

		ddWetterstation.setWidthFull();
		ddWetterstation.setItems(allStationDTOs.stream().sorted((a, b) -> a.getName().compareTo(b.getName())).toList());
		ddWetterstation.setLabel("Wetterstation");
		ddWetterstation.setItemLabelGenerator(station -> Utils.convertUppCaseToLowerCase(station.getName()));
		ddWetterstation.setHelperText(allStationDTOs.size() + " Wetterstationen in Deutschland gefunden");
		clickListenerStation();

		horizontalLayout.setWidth("70%");
		horizontalLayout.setHeightFull();

		Paragraph pgInformation = new Paragraph(
				"Mosmix ist ein Prognoseprodukt des DWD, das auf globalen Wettermodellen basiert und statistisches Downscaling für landgestützte Klimastationen auf Grundlage ihrer historischen Beobachtungen verwendet, um präzisere, lokale Prognosen zu liefern. Mosmix ist für über 5000 Stationen weltweit verfügbar und in zwei Versionen erhältlich, Mosmix-S und Mosmix-L. Mosmix-S verfügt über einen Satz von 40 Parametern und wird stündlich veröffentlicht, während MOSMIX-L einen Satz von etwa 115 Parametern hat und alle 6 Stunden (3 Uhr, 9 Uhr, 15 Uhr, 21 Uhr) veröffentlicht wird. Beide Versionen haben ein Prognoselimit von 240 Stunden.");
		pgInformation.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);

		Paragraph pgParameterInformation = new Paragraph(new Html("<span>Lokale Vorhersage von <strong>"
				+ dataList.size()
				+ "</strong> Parametern für weltweite Stationen, 24 Mal täglich mit einer Vorlaufzeit von 240 Stunden.</span>"));
		pgParameterInformation.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);

		HorizontalLayout hlStation = new HorizontalLayout();
		hlStation.setWidthFull();
		hlStation.setAlignItems(Alignment.BASELINE);
		hlStation.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
		btncheckAvaible.setEnabled(false);
		btncheckAvaible.setTooltipText("Die Verfügbarkeit der Wetterdaten prüfen");

		btnShowMap.setTooltipText("In Karte anzeigen");
		btnShowMap.addClickListener(click -> {
			Dialog dialog = new Dialog();
			dialog.open();
			dialog.setWidth("80%");
			dialog.setHeight("80%");

			OpenLayersMap olMap = new OpenLayersMap();
			if (ddWetterstation.getValue() == null) {
				olMap.showStationsOnMap(allStationDTOs);
			} else {
				olMap.showStationsOnMap(List.of(ddWetterstation.getValue()));
			}
			dialog.add(olMap);
		});

		hlStation.add(ddWetterstation, btncheckAvaible, btnShowMap);

		ddWetterstation.addValueChangeListener(click -> {
			if (click.getValue() == null) {
				return;
			}
			btncheckAvaible.setEnabled(true);
		});

		verticalLayout.add(new H3("Beschreibung"), pgInformation, pgParameterInformation, hlStation);
		verticalLayout.setWidth("40%");
		horizontalLayout.add(verticalLayout);
		grid.setSizeFull();
		horizontalLayout.addAndExpand(grid);
		add(horizontalLayout);
		setSizeFull();
	}

	private void prepareGrid() {
		grid.setSelectionMode(SelectionMode.SINGLE);
		grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
		grid.addClassNames(Margin.Right.MEDIUM);

		grid.addColumn(WetterParameter::getDescription).setHeader("Beschreibung").setFlexGrow(1)
				.setAutoWidth(true).setSortable(true);
		grid.addColumn(WetterParameter::getName).setHeader("Orignal Name").setFlexGrow(1).setAutoWidth(true)
				.setSortable(true);
		grid.addColumn(WetterParameter::getUnitType).setHeader("Einheitentyp").setFlexGrow(1)
				.setAutoWidth(true).setSortable(true);
		grid.addColumn(WetterParameter::getUnit).setHeader("Einheit").setFlexGrow(1).setAutoWidth(true)
				.setSortable(true);
		grid.setAllRowsVisible(true);
	}

	private void clickListenerStation() {
		String forcasBaseUrl = konfiguration.getWetterdienstBaseUrl()
				+ "values?provider=dwd&network=mosmix&parameters=hourly/large/";
		btncheckAvaible.addClickListener(click -> {
			if (grid.getColumnByKey("actions") != null) {
				grid.removeColumnByKey("actions");
			}
			grid.addComponentColumn(wetterdata -> {
				String url = forcasBaseUrl + wetterdata.getName() + "&station="
						+ ddWetterstation.getValue().getStationId();
				stationWithValues = wetterdienstService.hasStationValues(url, List.of(ddWetterstation.getValue()));

				boolean hasValues = !stationWithValues.isEmpty();

				HorizontalLayout buttonLayout = new HorizontalLayout();
				buttonLayout.setWidthFull();
				buttonLayout.getThemeList().add("spacing-xl");

				if (hasValues) {
					buttonLayout.setAlignItems(Alignment.STRETCH);
					buttonLayout.add(createChartButton(wetterdata, url), createSubscribeButton(wetterdata, url));
				}
				return buttonLayout;
			}).setHeader("Aktionen").setFlexGrow(1).setAutoWidth(true).setSortable(true).setKey("actions");
			Utils.showSuccessBox("Die Verfügbarkeit der Wetterdaten wurde geprüft.");
		});
	}

	private Button createSubscribeButton(WetterParameter wetterdata, String url) {
		Button btnSubscribe = new Button("Abonnieren");
		btnSubscribe.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
		weatherData = wetterdienstService.fetchWetterDaten(url);
		if (weatherData != null || !weatherData.isEmpty()) {
			String parameter = "hourly/large/" + weatherData.get(0).getParameter();
			weatherData.get(0).setParameter(parameter);
			weatherData.get(0).setId(ddWetterstation.getValue().getStationId());
			weatherData.get(0).setStationId(Utils.convertUppCaseToLowerCase(ddWetterstation.getValue().getName()));

			alreadySubscribed = abonnementService.checkAlreadySubscribed(weatherData);

			if (alreadySubscribed != null) {
				btnSubscribe.setText("Abonniert");
				btnSubscribe.setIcon(new Icon(VaadinIcon.BELL));
			} else {
				btnSubscribe.setText("Abonnieren");
				btnSubscribe.setIcon(new Icon(VaadinIcon.BELL_O));
				alreadySubscribed = new Abonnement();
			}
		}
		btnSubscribe.addClickListener(event -> {
			if (abonnementDialogView != null) {
				abonnementDialogView.removeFromParent();
			}
			weatherData = wetterdienstService.fetchWetterDaten(url);

			if (weatherData != null) {

				String parameter = "hourly/large/" + weatherData.get(0).getParameter();
				weatherData.get(0).setParameter(parameter);
				weatherData.get(0).setId(ddWetterstation.getValue().getStationId());
				weatherData.get(0).setStationId(Utils.convertUppCaseToLowerCase(ddWetterstation.getValue().getName()));

				abonnementDialogView = new AbonnementDialogView(route, "mosmix");
				add(abonnementDialogView);
				ComponentUtil.fireEvent(UI.getCurrent(), new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(),
						true, List.of(weatherData.get(0), weatherData.get(weatherData.size() - 1)), ""));
			}

		});
		return btnSubscribe;
	}

	private Button createChartButton(WetterParameter wetterdata, String url) {
		Button btnDisplayChart = new Button(new Icon(VaadinIcon.LINE_CHART));
		btnDisplayChart.setTooltipText("Chart anzeigen");
		btnDisplayChart.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
		btnDisplayChart.addClickListener(event -> showChart(wetterdata, url));
		return btnDisplayChart;
	}

	private Dialog showChart(WetterParameter wetterdata, String url) {
		Dialog dialog = new Dialog();
		dialog.open();
		dialog.setWidth("60%");
		weatherData = wetterdienstService.fetchWetterDaten(url);

		wetterDatenChart.displayWetterDataChart(weatherData, wetterdata.getName(), ddWetterstation.getValue(),
				"hourly");

		wetterDatenChart.setWidthFull();
		wetterDatenChart.setHeightFull();

		dialog.setDraggable(true);
		dialog.setResizable(true);

		dialog.add(wetterDatenChart);
		dialog.addResizeListener(event -> {
			wetterDatenChart.setWidthFull();
			wetterDatenChart.setHeightFull();
		});

		return dialog;
	}

}
