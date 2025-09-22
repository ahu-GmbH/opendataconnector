package de.ahu.opendata.Search;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.Abonnement.AbonnementDialogView;
import de.ahu.opendata.CsvConfiguration.CsvConfigService;
import de.ahu.opendata.CsvConfiguration.CsvConfiguration;
import de.ahu.opendata.DataUtils.DataEvents;
import de.ahu.opendata.DataUtils.StationDTO;
import de.ahu.opendata.DataUtils.StationDTO.Measurement;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Pegeldienst.PegelStationDTO;
import de.ahu.opendata.Pegeldienst.WasserstandDTO;
import de.ahu.opendata.Pegeldienst.WasserstandService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.ViewUtils.GridHeaderMapping;
import de.ahu.opendata.ViewUtils.ReformatCsvDialog;
import de.ahu.opendata.Wetterdienst.WetterDatenDTO;
import de.ahu.opendata.Wetterdienst.WetterParameter;
import de.ahu.opendata.Wetterdienst.WetterParameterEnum;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import de.ahu.opendata.Wetterdienst.WetterdienstService;

public class MesswerteDialog extends Dialog {
	private static final long serialVersionUID = 1L;

	private WasserstandService wasserstandService;
	private WetterdienstService wetterdienstService;
	private SearchGeoDataService searchOpenGeoDatenService;
	private CsvConfigService csvConfigService;
	private Konfiguration konfiguration;

	private ReformatCsvDialog reformatCsvDialog = null;
	private AbonnementDialogView abonnementDialogView = null;
	private GridHeaderMapping gridHeaderMapping = null;

	private VerticalLayout dialogVerticalLayout = null;
	private Div wrapperGridValue = new Div();
	private ComboBox<WetterParameter> ddParameter = new ComboBox<>();
	private Select<String> ddFiles = new Select<>();
	private Select<String> ddPegelTimeSeries = new Select<>();

	private Button btnAbonnement = new Button("Abonnieren", new Icon(VaadinIcon.BELL));
	private Button btnReformat = new Button("Umformatieren");
	private List<Measurement> values = new ArrayList<>();

	private Grid<String[]> gridValues = null;
	private Grid<StationDTO.Measurement> gridMesurementValues = new Grid<>(StationDTO.Measurement.class, false);
	private ListDataProvider<Measurement> dataProviderValues = null;
	private StationDTO currentSt;
	private String convertValue = "";
	private Select<StationDTO> messstelleDd = new Select<>();
	private HorizontalLayout buttonHl = new HorizontalLayout();
	private List<FileDTO> contentFiles = new ArrayList<FileDTO>();
	private boolean isGw = false;

	public MesswerteDialog(List<StationDTO> listStation, List<FileDTO> extractedContentFiles,
			Map<WetterParameter, List<WetterStationDTO>> stationsByForecastParameter, boolean isGrundWasser) {

		wasserstandService = SpringApplicationContext.getBean(WasserstandService.class);
		wetterdienstService = SpringApplicationContext.getBean(WetterdienstService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		searchOpenGeoDatenService = SpringApplicationContext.getBean(SearchGeoDataService.class);
		csvConfigService = SpringApplicationContext.getBean(CsvConfigService.class);
		this.contentFiles = extractedContentFiles;
		this.isGw = isGrundWasser;

		dialogVerticalLayout = new VerticalLayout();
		ddParameter.setWidth("100%");
		ddParameter.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);
		btnAbonnement.setEnabled(false);

		buttonHl.setWidth("50%");
		buttonHl.setAlignItems(Alignment.BASELINE);

		if (listStation.size() == 1) {
			currentSt = listStation.get(0);
			ddParameter.setLabel("Verfügbare Parameter für die Station " + currentSt.getName());
			handleSingleStation(stationsByForecastParameter);
		} else {
			messstelleDd.setLabel("Ausgewählte Stationen");
			messstelleDd.setItems(listStation);
			ddFiles.setLabel("Verfügbare Parameter für ausgewähte Stationen ");
			messstelleDd.setItemLabelGenerator(StationDTO::getName);
			ddFiles.setWidth("70%");
			btnAbonnement.setEnabled(false);

			btnReformat.setEnabled(false);
			if (isGrundWasser) {
				List<String> fileNames = extractedContentFiles.stream()
						.filter(file -> file.getTitle().contains("wasserstand")).map(file -> file.getTitle())
						.collect(Collectors.toList());
				ddFiles.setItems(fileNames);
			} else {
				List<FileDTO> filteredFiles = extractedContentFiles.stream()
						.filter(file -> !file.getTitle().contains("station") && !file.getTitle().contains("messstelle"))
						.collect(Collectors.toList());
				ddFiles.setItems(filteredFiles.stream().map(FileDTO::getTitle).collect(Collectors.toList()));
			}

			btnAbonnement.addClickListener(c -> {
				handleMultiAbonnements(listStation);
			});

			handleUmformat();

			buttonHl.add(ddFiles, messstelleDd, btnAbonnement, btnReformat);
			add(buttonHl);
		}

		messstelleDd.addValueChangeListener(click -> {
			handleValuesChanges();
		});

		ddFiles.addValueChangeListener(click -> {
			handleValuesChanges();
		});

		setWidth("75%");
		open();
	}

	private void handleMultiAbonnements(List<StationDTO> listStation) {
		if (abonnementDialogView != null) {
			abonnementDialogView.removeFromParent();
		}
		WetterDatenDTO firstEntry = new WetterDatenDTO();
		WetterDatenDTO lastEntry = new WetterDatenDTO();

		if (gridValues != null && gridValues.getListDataView().getItemCount() > 0) {
			String[] firstMessValue = gridValues.getListDataView().getItem(0);
			String[] lastMessValue = gridValues.getListDataView()
					.getItem(gridValues.getListDataView().getItemCount() - 1);
			StringBuilder stringBuilder = new StringBuilder();

			if (ddFiles.getValue().contains("gw-wasserstand")) {
				listStation.forEach(st -> {
					if (st instanceof WetterStationDTO messstelle) {
						if (stringBuilder.length() > 0) {
							stringBuilder.append(",");
						}
						stringBuilder.append(messstelle.getStationId());
					}
				});
				firstEntry.setId(stringBuilder.toString());
				firstEntry.setDate(firstMessValue[3]);
				lastEntry.setDate(lastMessValue[3]);
			} else {
				firstEntry.setId(firstMessValue[0]);
				firstEntry.setDate(firstMessValue[1]);
				lastEntry.setDate(lastMessValue[1]);
			}

			String labels = "";
			for (int i = 0; i < listStation.size(); i++) {
				if (i > 0) {
					labels += ",";
				}
				labels += listStation.get(i).getName();
			}
			firstEntry.setStationId(labels);
			if (ddFiles.getValue() != null) {
				firstEntry.setParameter(ddFiles.getValue());
			}

		}

		String baseUrl = "";
		if (!contentFiles.isEmpty()) {
			if (ddParameter.getValue() != null) {
				if (!ddParameter.getValue().getOriginalName().contains("/")) {
					baseUrl = this.konfiguration.getWetterdienstBaseUrlMosmix();
				} else {
					baseUrl = this.konfiguration.getWetterdienstBaseUrlObservation();
				}

			} else if (ddFiles.getValue() != null) {
				Optional<FileDTO> relevantFile = contentFiles.stream()
						.filter(file -> file.getTitle().equals(ddFiles.getValue())).findFirst();
				baseUrl = relevantFile.isPresent() == true ? relevantFile.get().getUrl() : "";
			}
		} else if (ddPegelTimeSeries.getValue() != null && !ddPegelTimeSeries.getValue().isEmpty()) {
			if (ddPegelTimeSeries.getValue().startsWith("H")) {
				baseUrl = wasserstandService.historicalDataUrl;
			} else {
				baseUrl = wasserstandService.forecastDataUrl;
			}
		} else if (currentSt.getIsHistorical() != null && currentSt.getIsHistorical() && contentFiles.isEmpty()) {
			baseUrl = this.konfiguration.getWetterdienstBaseUrlObservation();
		} else if (currentSt.getIsForecast() != null && currentSt.getIsForecast() && contentFiles.isEmpty()) {
			baseUrl = this.konfiguration.getWetterdienstBaseUrlMosmix();
		}

		abonnementDialogView = new AbonnementDialogView("", baseUrl);
		add(abonnementDialogView);
		ComponentUtil.fireEvent(UI.getCurrent(), new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(), true,
				List.of(firstEntry, lastEntry), ""));
	}

	private void handleSingleAbonnement() {
		if (abonnementDialogView != null) {
			abonnementDialogView.removeFromParent();
		}

		WetterDatenDTO firstEntry = new WetterDatenDTO();
		WetterDatenDTO secondEntry = new WetterDatenDTO();

		if (gridValues != null && gridValues.getListDataView().getItemCount() > 0) {
			String[] firstMessValue = gridValues.getListDataView().getItem(0);
			String[] lastMessValue = gridValues.getListDataView()
					.getItem(gridValues.getListDataView().getItemCount() - 1);

			if (ddFiles.getValue().contains("gw-wasserstand")) {
				firstEntry.setId(firstMessValue[1]);
				firstEntry.setDate(firstMessValue[3]);
				secondEntry.setDate(lastMessValue[3]);
			} else {
				firstEntry.setId(firstMessValue[0]);
				firstEntry.setDate(firstMessValue[1]);
				secondEntry.setDate(lastMessValue[1]);
			}

			firstEntry.setStationId(currentSt.getName());

			if (ddFiles.getValue() != null) {
				firstEntry.setParameter(ddFiles.getValue());
			}
			if (ddParameter.getValue() != null) {
				if (currentSt.getIsForecast() != null && currentSt.getIsForecast()) {
					firstEntry.setParameter("hourly/large/" + ddParameter.getValue().getOriginalName());
				} else if (currentSt.getIsHistorical() != null && currentSt.getIsHistorical()) {
					firstEntry.setParameter(ddParameter.getValue().getOriginalName());
				}
			}

		}
		String baseUrl = "";
		if (!contentFiles.isEmpty()) {
			if (ddParameter.getValue() != null) {
				if (!ddParameter.getValue().getOriginalName().contains("/")) {
					baseUrl = this.konfiguration.getWetterdienstBaseUrlMosmix();
				} else {
					baseUrl = this.konfiguration.getWetterdienstBaseUrlObservation();
				}

			} else if (ddFiles.getValue() != null) {
				Optional<FileDTO> relevantFile = contentFiles.stream()
						.filter(file -> file.getTitle().equals(ddFiles.getValue())).findFirst();
				baseUrl = relevantFile.isPresent() == true ? relevantFile.get().getUrl() : "";
			}
		} else if (ddPegelTimeSeries.getValue() != null && !ddPegelTimeSeries.getValue().isEmpty()) {
			if (ddPegelTimeSeries.getValue().startsWith("H")) {
				baseUrl = wasserstandService.historicalDataUrl;
			} else {
				baseUrl = wasserstandService.forecastDataUrl;
			}
		} else if (currentSt.getIsHistorical() != null && currentSt.getIsHistorical() && contentFiles.isEmpty()) {
			baseUrl = this.konfiguration.getWetterdienstBaseUrlObservation();
		} else if (currentSt.getIsForecast() != null && currentSt.getIsForecast() && contentFiles.isEmpty()) {
			baseUrl = this.konfiguration.getWetterdienstBaseUrlMosmix();
		}

		abonnementDialogView = new AbonnementDialogView("", baseUrl);
		add(abonnementDialogView);
		ComponentUtil.fireEvent(UI.getCurrent(), new DataEvents.DataSaveEvent<WetterDatenDTO>(UI.getCurrent(), true,
				List.of(firstEntry, secondEntry), ""));
	}

	private void handleValuesChanges() {
		if (ddFiles.getValue() == null || messstelleDd.getValue() == null) {
			Utils.showHinweisBox("Füllen Sie bitte beide Dropdowns mit Wert aus.");
			return;
		}
		if (isGw) {
			String messstelleId = null;
			if (messstelleDd.getValue() instanceof WetterStationDTO messstelle) {
				messstelleId = messstelle.getStationId();
			}
			displayGrid(messstelleId);
		}
	}

	private void handleUmformat() {
		btnReformat.addClickListener(c -> {
			if (ddFiles.getValue() != null) {
				String fileFattern = ddFiles.getValue().substring(0, ddFiles.getValue().lastIndexOf("."));
				CsvConfiguration csvConfig = csvConfigService.getCurrentCsvConfiguration(fileFattern);

				FileDTO relevantFile = contentFiles.stream().filter(file -> file.getTitle().equals(ddFiles.getValue()))
						.findFirst().get();

				if (gridHeaderMapping != null) {
					gridHeaderMapping.removeFromParent();
				}
				if (gridValues != null) {
					gridHeaderMapping = new GridHeaderMapping(csvConfig, relevantFile,
							gridValues.getHeaderRows().get(0).getCells());
				}

			}
		});
	}

	private void handleSingleStation(Map<WetterParameter, List<WetterStationDTO>> stationsByForecastParameter) {

		if (currentSt instanceof WetterStationDTO messstelle) {
			if (messstelle.getIsHistorical() != null && messstelle.getIsHistorical()) {
				btnAbonnement.setEnabled(false);
				List<WetterParameter> filteredParameters = messstelle.getPathParameters().stream()
						.filter(Objects::nonNull).toList();

				ddParameter.setItemLabelGenerator(WetterParameter::getOriginalName);
				ddParameter.setItems(filteredParameters.stream().collect(Collectors.toSet()));

				buttonHl.add(ddParameter, btnAbonnement);
				add(buttonHl);
				if (messstelle.getPathParameters().isEmpty()) {
					ddParameter.setPlaceholder("Keine Parameter vorhanden");
				} else if (messstelle.getPathParameters().size() == 1) {
					ddParameter.setValue(messstelle.getPathParameters().get(0));
					ddParameterChange(messstelle.getPathParameters().get(0), messstelle);
				}

			} else if (messstelle.getIsForecast() != null && messstelle.getIsForecast()) {
				btnAbonnement.setEnabled(false);
				ddParameter.setItems(stationsByForecastParameter.keySet());
				ddParameter.setItemLabelGenerator(WetterParameter::getDescription);
				buttonHl.add(ddParameter, btnAbonnement);
				add(buttonHl);
				if (stationsByForecastParameter.keySet().isEmpty()) {
					ddParameter.setPlaceholder("Keine Parameter vorhanden");
				} else if (stationsByForecastParameter.keySet().size() == 1) {
					ddParameter.setValue(stationsByForecastParameter.keySet().iterator().next());
					btnAbonnement.setEnabled(true);
					ddParameterChange(stationsByForecastParameter.keySet().iterator().next(), messstelle);
				}

			} else {
				ddFiles.setLabel("Verfügbare Dateien für die Station " + messstelle.getName());
				btnAbonnement.setEnabled(false);
				btnReformat.setEnabled(false);
				if (isGw) {
					List<String> fileNames = contentFiles.stream()
							.filter(file -> file.getTitle().contains("wasserstand")).map(file -> file.getTitle())
							.collect(Collectors.toList());
					ddFiles.setItems(fileNames);
				} else {
					List<FileDTO> filteredFiles = contentFiles.stream().filter(
							file -> !file.getTitle().contains("station") && !file.getTitle().contains("messstelle"))
							.collect(Collectors.toList());
					ddFiles.setItems(filteredFiles.stream().map(FileDTO::getTitle).collect(Collectors.toList()));
				}
				ddFiles.setWidth("50%");
				buttonHl.add(ddFiles, btnAbonnement, btnReformat);
				dialogVerticalLayout.add(buttonHl);
				add(dialogVerticalLayout);
			}
		} else if (currentSt instanceof PegelStationDTO pegelstation) {
			ddPegelTimeSeries.setLabel("Zeitreihe");
			List<String> timeSeries = new ArrayList<>();
			if (pegelstation.getIsHistorical() != null && pegelstation.getIsHistorical()) {
				timeSeries.add("Historisch");
			}
			if (pegelstation.getIsForecast() != null && pegelstation.getIsForecast()) {
				timeSeries.add("Vorhersage");
			}
			ddPegelTimeSeries.setItems(timeSeries);
			ddPegelTimeSeries.setWidth("50%");
			buttonHl.add(ddPegelTimeSeries, btnAbonnement);
			add(buttonHl);

			if (timeSeries.size() >= 1) {
				ddPegelTimeSeries.setValue(timeSeries.get(0));
				ddPegelTimSeriesValueChange(ddPegelTimeSeries.getValue(), pegelstation);
				btnAbonnement.setEnabled(true);
			}
		}

		ddFiles.addValueChangeListener(e -> {
			if (e.getValue() != null && currentSt instanceof WetterStationDTO messstelle) {
				values.clear();

				if (isGw) {
					displayGrid(messstelle.getStationId());
				} else {
					Optional<FileDTO> relevantFile = contentFiles.stream()
							.filter(file -> file.getTitle().equals(ddFiles.getValue())).findFirst();

					if (reformatCsvDialog != null) {
						remove(reformatCsvDialog);
					}
					reformatCsvDialog = new ReformatCsvDialog();
					gridValues = reformatCsvDialog.createCsvPreviewGrid(relevantFile.get().getFilePath(),
							messstelle.getStationId());

					if (gridValues.getListDataView().getItemCount() > 0) {
						add(reformatCsvDialog);
						setHeight("100%");
					} else {
						btnReformat.setEnabled(false);
						Utils.showHinweisBox("Keine Daten vorhanden");
					}
				}

			}
		});

		btnAbonnement.addClickListener(c -> {
			handleSingleAbonnement();
		});
		handleUmformat();

		ddParameter.addValueChangeListener(event -> {
			if (event.getValue() != null && currentSt != null) {
				ddParameterChange(event.getValue(), currentSt);
				btnAbonnement.setEnabled(true);
			}
		});

		ddPegelTimeSeries.addValueChangeListener(change -> {
			if (change.getValue() != null) {
				ddPegelTimSeriesValueChange(change.getValue(), currentSt);
				btnAbonnement.setEnabled(true);
			}
		});
	}

	private void displayGrid(String messstelleId) {
		List<String> values = searchOpenGeoDatenService.extractGrundWasser(this.contentFiles, messstelleId,
				ddFiles.getValue());

		if (values.size() > 1) {
			String[] headers = values.get(0).split(";", -1);
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			List<String[]> rows = values.stream().skip(1).map(line -> line.split(";", -1))
					.sorted(Comparator.comparing(row -> LocalDate.parse(row[3], formatter))).toList();
			if (gridValues != null) {
				gridValues.removeFromParent();
			}

			gridValues = new Grid<>();
			for (int i = 0; i < headers.length; i++) {
				final int colIndex = i;
				gridValues.addColumn(row -> row.length > colIndex ? row[colIndex] : "").setHeader(headers[i]);
			}
			gridValues.setSizeFull();
			gridValues.setItems(rows);
			add(gridValues);
			setHeightFull();

			btnAbonnement.setEnabled(true);
			btnReformat.setEnabled(true);
		} else {
			Utils.showHinweisBox("Keine Daten vorhanden");
			if (gridValues != null) {
				gridValues.removeFromParent();
			}
			btnAbonnement.setEnabled(false);
			btnReformat.setEnabled(false);
		}
	}

	private void ddParameterChange(WetterParameter wetterParam, StationDTO st) {
		if (wetterParam != null && st instanceof WetterStationDTO wetter) {
			resetGrid();
			String url;
			if (wetter.getIsHistorical() != null && wetter.getIsHistorical()) {
				url = konfiguration.getWetterdienstBaseUrlObservation() + wetterParam.getOriginalName() + "&station="
						+ wetter.getStationId() + "&date=" + Utils.formatDateOnly(wetter.getStartDate()) + "/"
						+ Utils.formatDateOnly(wetter.getEndDate());
			} else if (wetter.getIsForecast() != null && wetter.getIsForecast()) {
				url = konfiguration.getWetterdienstBaseUrlMosmix() + "hourly/large/" + wetterParam.getOriginalName()
						+ "&station=" + wetter.getStationId();
			} else {
				return;
			}
			List<WetterDatenDTO> wetterDaten = wetterdienstService.fetchWetterDaten(url);
			if (wetterDaten.isEmpty()) {
				showNoDataMessage();
			} else {
				values.clear();
				wetterDaten
						.forEach(dto -> values.add(new Measurement(dto.getStationId(), dto.getDate(), dto.getValue())));
				refreshGrid(values);
			}
		}
	}

	private void ddPegelTimSeriesValueChange(String value, StationDTO st) {
		List<WasserstandDTO> wasserstand = new ArrayList<>();
		values.clear();
		if (value.equals("Historisch") && st instanceof PegelStationDTO pegel) {
			wasserstand = wasserstandService.fetchHistoricalData(pegel.getId());
			wasserstand.forEach(
					dto -> values.add(new Measurement(st.getId(), dto.getTimeStamp(), dto.getCurrentMeasuredValue())));

		} else if (value.equals("Vorhersage") && st instanceof PegelStationDTO pegel) {
			wasserstand = wasserstandService.fetchVorhersageData(pegel.getId());
			if (wasserstand.isEmpty()) {
				return;
			}
			AtomicInteger i = new AtomicInteger(0);
			wasserstand.forEach(measurement -> {
				values.add(new Measurement(st.getId(),
						(i.getAndIncrement() == 0) ? measurement.getTimespanStart() : measurement.getTimespanEnd(),
						measurement.getCurrentMeasuredValue()));
			});

		}
		if (wasserstand.isEmpty()) {
			showNoDataMessage();
		}
		resetGrid();
		refreshGrid(values);
	}

	private void showNoDataMessage() {
		Utils.showHinweisBox("Keine Daten vorhanden");
		close();
	}

	private void resetGrid() {
		if (gridMesurementValues != null) {
			gridMesurementValues.removeFromParent();
			gridMesurementValues = new Grid<>(Measurement.class, false);
		} else {
			gridMesurementValues = new Grid<>(Measurement.class, false);
		}
	}

	private void refreshGrid(List<Measurement> values) {
		WetterParameterEnum weParameterEnum = Stream.of(WetterParameterEnum.values())
				.filter(wp -> wp != null && StringUtils.containsIgnoreCase(convertValue, wp.toString().toLowerCase()))
				.findFirst().orElse(null);

		gridMesurementValues.addColumn(Measurement::getStationNo).setHeader("Station-Id").setSortable(true);
		gridMesurementValues.addColumn(Measurement::getTime).setHeader("Zeit").setSortable(true);
		gridMesurementValues.addColumn(Measurement::getValue)
				.setHeader("Wert in " + ((weParameterEnum != null) ? weParameterEnum.getUnit() : "")).setSortable(true);

		dataProviderValues = new ListDataProvider<>(values);
		gridMesurementValues.setDataProvider(dataProviderValues);
		gridMesurementValues.setSizeFull();
		wrapperGridValue.add(gridMesurementValues);
		wrapperGridValue.setSizeFull();

		dialogVerticalLayout.add(wrapperGridValue);
		dialogVerticalLayout.setSizeFull();
		add(dialogVerticalLayout);
		setHeightFull();
	}

}
