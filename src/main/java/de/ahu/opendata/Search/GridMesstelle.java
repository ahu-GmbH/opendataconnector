package de.ahu.opendata.Search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.ahu.opendata.DataUtils.StationDTO;
import de.ahu.opendata.DataUtils.StationDTOFilter;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Pegeldienst.PegelStationDTO;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterParameter;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import de.ahu.opendata.views.OpenLayersMap;

public class GridMesstelle extends Div {

	private static final long serialVersionUID = 1L;
	private Grid.Column<StationDTO> nameColumn = null;
	private Grid.Column<StationDTO> providerColumn = null;
	private Grid.Column<StationDTO> historischColumn = null;
	private Grid.Column<StationDTO> vorhersageColumn = null;
	private Grid.Column<StationDTO> btnColumn = null;
	private StationDTOFilter filter;
	private HeaderRow headerRow = null;

	protected OpenLayersMap olMap;
	protected Grid<StationDTO> messstelleGrid = null;
	private GridListDataView<StationDTO> dataProviderStations = null;
	private VerticalLayout mapVl = new VerticalLayout();

	private ComboBox<String> ddProvider = new ComboBox<>();
	private Button btnFilterStations = new Button("Filtern");
	private TextField tfMinLatitude = new TextField("Minimaler Breitengrad");
	private TextField tfMaxLatitude = new TextField("Maximaler Breitengrad");
	private TextField tfMinLongitude = new TextField("Minimaler Längengrad");
	private TextField tfMaxLongitude = new TextField("Maximale Längengrad");
	private H5 countResult = new H5();

	@SuppressWarnings("unchecked")
	public GridMesstelle(List<StationDTO> mergedStations, List<FileDTO> extractedContentFiles,
			Map<WetterParameter, List<WetterStationDTO>> stationsByForecastParameter,
			Map<String, List<? extends StationDTO>> providerList, String searchValue, boolean isGrundWasser,
			VerticalLayout gridAndFormVerticalLayout, VerticalLayout mapVerticalLayout) {
		super();

		olMap = new OpenLayersMap();
		olMap.setSizeFull();

		gridAndFormVerticalLayout.setHeightFull();
		gridAndFormVerticalLayout.setWidth("50%");

		this.mapVl = mapVerticalLayout;
		mapVl.setHeightFull();
		mapVl.setWidth("50%");

		messstelleGrid = new Grid<>(StationDTO.class, false);
		dataProviderStations = messstelleGrid.setItems(mergedStations);

		nameColumn = messstelleGrid.addColumn(station -> station.getName()).setHeader("Name").setAutoWidth(true)
				.setFlexGrow(1);
		providerColumn = messstelleGrid.addColumn(station -> station.getProvider()).setHeader("Provider/Gebiet")
				.setAutoWidth(true).setResizable(true).setFlexGrow(1);

		historischColumn = messstelleGrid.addColumn(new ComponentRenderer<>(station -> {
			if (station instanceof WetterStationDTO wetter) {
				Icon statusIndicator = new Icon();
				if (wetter.getIsHistorical() != null && wetter.getIsHistorical()) {
					statusIndicator = VaadinIcon.CHECK_CIRCLE.create();
					statusIndicator.setColor("green");
					statusIndicator.addClassName("text-body");
					statusIndicator.setSize("1em");
				}

				return statusIndicator;
			}
			if (station instanceof PegelStationDTO pegel) {
				Icon statusIndicator = new Icon();
				if (pegel.getIsHistorical() != null && pegel.getIsHistorical()) {
					statusIndicator = VaadinIcon.CHECK_CIRCLE.create();
					statusIndicator.setColor("green");
					statusIndicator.addClassName("text-body");
					statusIndicator.setSize("1em");
				}
				return statusIndicator;
			}
			return null;
		})).setHeader("Historisch").setAutoWidth(true).setFlexGrow(0);

		vorhersageColumn = messstelleGrid.addColumn(new ComponentRenderer<>(station -> {
			if (station instanceof WetterStationDTO wetter) {
				Icon statusIndicator = new Icon();
				if (wetter.getIsForecast() != null && wetter.getIsForecast()) {
					statusIndicator = VaadinIcon.CHECK_CIRCLE.create();
					statusIndicator.setColor("blue");
					statusIndicator.addClassName("text-body");
					statusIndicator.setSize("1em");
				}
				return statusIndicator;
			}
			if (station instanceof PegelStationDTO pegel) {
				Icon statusIndicator = new Icon();
				if (pegel.getIsForecast() != null && pegel.getIsForecast()) {
					statusIndicator = VaadinIcon.CHECK_CIRCLE.create();
					statusIndicator.setColor("blue");
					statusIndicator.addClassName("text-body");
					statusIndicator.setSize("1em");
				}
				return statusIndicator;
			}
			return null;
		})).setHeader("Vorhersage").setAutoWidth(true).setFlexGrow(0);

		btnColumn = messstelleGrid.addColumn(new ComponentRenderer<>(st -> {
			Button btnShowData = new Button("Daten anzeigen");
			btnShowData.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
			btnShowData.addClickListener(click -> new MesswerteDialog(List.of(st), extractedContentFiles,
					stationsByForecastParameter, isGrundWasser));
			return btnShowData;
		})).setAutoWidth(true).setFlexGrow(1);

		messstelleGrid.setSelectionMode(Grid.SelectionMode.MULTI);
		messstelleGrid.asMultiSelect();

		messstelleGrid.setColumnOrder(nameColumn, providerColumn, historischColumn, vorhersageColumn, btnColumn);
		messstelleGrid.setSizeFull();
		gridAndFormVerticalLayout.add(messstelleGrid);

		messstelleGrid.getHeaderRows().clear();
		headerRow = messstelleGrid.appendHeaderRow();
		filter = new StationDTOFilter(dataProviderStations);
		headerRow.getCell(nameColumn).setComponent(createFilterHeader(filter::setStationName));
		headerRow.getCell(providerColumn).setComponent(createFilterHeader(filter::setProvider));

		initOpenLayersMap();

		messstelleGrid.addSelectionListener(select -> {
			if (select.getAllSelectedItems().size() > 1) {
				new MesswerteDialog(select.getAllSelectedItems().stream().toList(), extractedContentFiles,
						stationsByForecastParameter, isGrundWasser);
			}
		});

		ddProvider.addValueChangeListener(event -> {
			messstelleGrid.getHeaderRows().clear();
			if (event.getValue() != null) {
				List<? extends StationDTO> selectedStations = providerList.get(event.getValue());
				if (selectedStations != null) {
					if (tfMaxLatitude.getValue() != null && tfMinLongitude.getValue() != null) {
						GridListDataView<StationDTO> dataView = messstelleGrid.getListDataView();
						if (event.getValue().equals("OpenGeoData-NRW")) {
							dataView.setFilter(st -> selectedStations.contains(st));
							providerColumn.setHeader("Gebiet");
						} else if (event.getValue().equals("Pegelonline-Dienst")) {
							dataView.setFilter(st -> selectedStations.contains(st));
							providerColumn.setHeader("Provider");
						} else {
							dataView.setFilter(st -> st.getProvider().equals(event.getValue()));
							providerColumn.setHeader("Provider");
						}
						dataProviderStations = dataView;
					} else {
						if (event.getValue().equals("OpenGeoData-NRW")) {
							providerColumn.setHeader("Gebiet");
						} else {
							providerColumn.setHeader("Provider");
						}
						dataProviderStations = messstelleGrid.setItems(selectedStations.stream()
								.sorted(Comparator.comparing(StationDTO::getName)).collect(Collectors.toList()));
					}
				}
				updateHeaderRows();
			} else {
				providerColumn.setHeader("Provider/Gebiet");
				dataProviderStations = messstelleGrid.setItems(mergedStations);
				updateHeaderRows();
			}
			messstelleGrid.getDataProvider().refreshAll();
			initOpenLayersMap();
			countResult.setText(dataProviderStations.getItemCount() + " Ergebnisse zu " + searchValue
					+ (event.getValue() != null ? " von " + event.getValue() : ""));
		});

		btnFilterStations.addClickListener(submit -> {
			double minLat = Double.parseDouble(tfMinLatitude.getValue());
			double maxLat = Double.parseDouble(tfMaxLatitude.getValue());
			double minLon = Double.parseDouble(tfMinLongitude.getValue());
			double maxLon = Double.parseDouble(tfMaxLongitude.getValue());

			if (ddProvider.getValue() != null) {
				ddProvider.setValue(null);
			}

			List<StationDTO> filteredStations = mergedStations.stream()
					.filter(station -> station.getLatitude() >= minLat && station.getLatitude() <= maxLat
							&& station.getLongitude() >= minLon && station.getLongitude() <= maxLon)
					.collect(Collectors.toList());

			if (filteredStations.isEmpty()) {
				Utils.showHinweisBox("Keine Stationen innerhalb der Bounding Box gefunden.");
			} else {
				dataProviderStations = messstelleGrid.setItems(filteredStations);
				initOpenLayersMap();
				updateHeaderRows();
			}
			countResult.setText(dataProviderStations.getItemCount() + " Ergebnisse gefunden zu " + searchValue);
		});

	}

	private void updateHeaderRows() {
		if (headerRow != null && filter != null) {
			filter = new StationDTOFilter(dataProviderStations);
			headerRow.getCell(nameColumn).setComponent(createFilterHeader(filter::setStationName));
			headerRow.getCell(providerColumn).setComponent(createFilterHeader(filter::setProvider));
		}
	}

	private Component createFilterHeader(Consumer<String> filterChangeConsumer) {
		TextField textField = new TextField();
		textField.setValueChangeMode(ValueChangeMode.EAGER);
		textField.setClearButtonVisible(true);
		textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
		textField.addValueChangeListener(e -> {
			filterChangeConsumer.accept(e.getValue());
			if (e.getValue() != null && !e.getValue().isEmpty()) {
				initOpenLayersMap();
			}
		});
		VerticalLayout layout = new VerticalLayout(textField);
		layout.getThemeList().clear();
		layout.getThemeList().add("spacing-xs");

		return layout;
	}

	private void initOpenLayersMap() {
		if (!mapVl.getChildren().anyMatch(component -> component instanceof OpenLayersMap)) {
			olMap = new OpenLayersMap();
			olMap.setSizeFull();
			olMap.showStationsOnMap(dataProviderStations.getItems().collect(Collectors.toList()));
			mapVl.add(olMap);
		} else {
			olMap.showStationsOnMap(dataProviderStations.getItems().collect(Collectors.toList()));
		}
	}

}
