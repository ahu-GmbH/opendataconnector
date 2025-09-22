package de.ahu.opendata.OpenDataNrw;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.Abonnement.OpenGeoData;
import de.ahu.opendata.Abonnement.OpenGeoDataService;
import de.ahu.opendata.CsvConfiguration.CsvConfigService;
import de.ahu.opendata.CsvConfiguration.CsvConfiguration;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.ServiceUtils.CrawlerFileService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("serial")
public class SearchOpenGeoDatenView extends VerticalLayout {
	private CrawlerFileService crawlerFileService;
	private SearchOpenGeoDatenService searchOpenGeoDatenService;
	private Konfiguration konfiguration;
	private OpenGeoDataService openGeoDataService;
	private CsvConfigService csvConfigService;

	private VerticalLayout sucheProfilVerticalLayout = new VerticalLayout();
	private HorizontalLayout mainHorizontalLayout = new HorizontalLayout();
	private Grid<String> fileGrid = new Grid<>(String.class, false);
	private TreeGrid<FileDTO> treeGrid = new TreeGrid<>();

	private TextField tfSearch = new TextField();
	private Button btnSearch = new Button(new Icon(VaadinIcon.SEARCH));
	private Button btnPrepareReformat = new Button(new Icon(VaadinIcon.COG));
	private Button btnReformat = new Button("Umformatieren");

	private Button btnReformatFile = new Button(new Icon(VaadinIcon.FILE_PROCESS));
	private Button btnPreviewZipFiles = new Button(new Icon(VaadinIcon.FILE_PRESENTATION));
	private Button btnSubscribe = new Button("Abonnieren", new Icon(VaadinIcon.BELL));

	private Set<FileDTO> relevantFiles = new HashSet<>();
	private Set<FileDTO> selectedFiles = new HashSet<>();
	private VerticalLayout gridVerticalLayout = new VerticalLayout();
	private Anchor downloadLink;
	private Map<String, List<String>> selectedColumns = new HashMap<>();
	private String currentFileName = "";
	private String currentFileContent = "";

	public SearchOpenGeoDatenView() {
		crawlerFileService = SpringApplicationContext.getBean(CrawlerFileService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		searchOpenGeoDatenService = SpringApplicationContext.getBean(SearchOpenGeoDatenService.class);
		openGeoDataService = SpringApplicationContext.getBean(OpenGeoDataService.class);
		csvConfigService = SpringApplicationContext.getBean(CsvConfigService.class);
		initSearchProfileLayout();
		setSizeFull();
	}

	public void initSearchProfileLayout() {
		sucheProfilVerticalLayout.add(new H4("Suchprofil für OpenGeodata.NRW"));
		sucheProfilVerticalLayout.addClassNames(LumoUtility.BoxShadow.SMALL);
		sucheProfilVerticalLayout.setWidth("20%");

		tfSearch.setPlaceholder("Suche nach Geodaten");
		tfSearch.setWidthFull();
		tfSearch.setClearButtonVisible(true);

		btnSearch.addClickShortcut(Key.ENTER);

		HorizontalLayout horizontalLayout = new HorizontalLayout(tfSearch, btnSearch);
		horizontalLayout.setWidthFull();

		btnSearch.addClickShortcut(Key.ENTER);
		btnSearch.setTooltipText("Durchsuchen");
		btnSearch.addClickListener(click -> {
			if (tfSearch.getValue() == null || tfSearch.getValue().isEmpty()) {
				Utils.showHinweisBox("Geben Sie bitte einen Suchbegriff ein.");
				return;
			}
			if (downloadLink != null) {
				downloadLink.removeFromParent();
			}
			gridVerticalLayout.removeFromParent();
			treeGrid.removeFromParent();

			crawlerFileService = new CrawlerFileService(konfiguration.getOpenGeoDataBaseUrl(), 5);
			try {
				crawlerFileService.crawlFiles(konfiguration.getGwStartUrl(), 0, "zip");
			} finally {
				crawlerFileService.shutdown();
			}

			relevantFiles = crawlerFileService.fetchFoundFiles().stream()
					.filter(file -> StringUtils.containsIgnoreCase(file.getUrl(), tfSearch.getValue()))
					.filter(file -> !StringUtils.containsIgnoreCase(file.getUrl(), "_Shape"))
					.collect(Collectors.toSet());

			if (!relevantFiles.isEmpty()) {
				fileGrid.setSelectionMode(Grid.SelectionMode.MULTI);
				fileGrid.setAllRowsVisible(true);
				List<String> foundFiles = relevantFiles.stream().map(Utils::extractFileName).sorted()
						.collect(Collectors.toList());
				fileGrid.setItems(foundFiles);
				fileGrid.removeAllColumns();

				fileGrid.addComponentColumn(fileName -> {
					FileDTO file = relevantFiles.stream().filter(f -> Utils.extractFileName(f).equals(fileName))
							.findFirst().orElse(null);

					if (file != null) {
						Anchor downloadLink = new Anchor(file.getUrl(), fileName);
						downloadLink.setTarget("_blank");
						downloadLink.getElement().setAttribute("download", true);
						return downloadLink;
					}
					return new NativeLabel(fileName);
				}).setHeader("Dateiname");

				fileGrid.asMultiSelect().addValueChangeListener(event -> {
					selectedFiles.clear();
					event.getValue().forEach(fileName -> {
						relevantFiles.stream().filter(file -> Utils.extractFileName(file).equals(fileName)).findFirst()
								.ifPresent(selectedFiles::add);
					});
				});
				sucheProfilVerticalLayout.add(fileGrid, btnPreviewZipFiles);
			}
		});

		btnPreviewZipFiles.setText("Zip-Datei(en) entpacken");
		btnPreviewZipFiles.addClickListener(c -> {
			if (!selectedFiles.isEmpty()) {
				gridVerticalLayout.removeFromParent();
				displayZipContentsWithPreview(new ArrayList<>(selectedFiles));
			} else {
				Utils.showHinweisBox("Bitte wählen Sie mindestens eine Datei aus");
			}
		});

		btnPrepareReformat.setText("Struktur konfigurieren");
		btnPrepareReformat.addClickListener(c -> {
			if (!selectedFiles.isEmpty()) {
				Dialog dialog = new Dialog();
				dialog.open();
				dialog.setHeight("40%");
				dialog.setWidth("80%");

				selectedColumns = searchOpenGeoDatenService.clearFiles(new ArrayList<>(selectedFiles), selectedColumns);

				VerticalLayout dialogLayout = new VerticalLayout();
				dialogLayout.setPadding(false);
				dialogLayout.setSpacing(true);

				List<String> mergedHeaders = new ArrayList<>();
				String stationFileName = null;
				for (String tableName : selectedColumns.keySet()) {
					if (tableName.contains("stationen")) {
						stationFileName = tableName;
						mergedHeaders.addAll(selectedColumns.get(tableName));
					}
				}
				for (String tableName : selectedColumns.keySet()) {
					if (!tableName.equals(stationFileName)) {
						List<String> selectedCols = selectedColumns.get(tableName);
						long count = selectedCols.stream().filter(col -> col.equalsIgnoreCase("station_no")).count();
						if (count > 1) {
							selectedCols = selectedCols.stream().filter(new HashSet<>()::add)
									.collect(Collectors.toList());
						}

						mergedHeaders.addAll(selectedCols);
					}
				}

				if (mergedHeaders.isEmpty()) {
					dialog.add(new H4("Wählen Sie mindestens 1 Spalte von einer oder mehreren Tabellen."));
					dialog.setHeight("10%");
					dialog.setWidth("30%");
					return;
				}

				List<String> reorderedHeaders = new ArrayList<>(new LinkedHashSet<>(mergedHeaders));
				Grid<String> headerGrid = new Grid<>();
				headerGrid.addClassName(LumoUtility.Border.ALL);
				headerGrid.addClassName(LumoUtility.BorderColor.CONTRAST_50);
				headerGrid.setColumnReorderingAllowed(true);
				headerGrid.setAllRowsVisible(true);

				for (int i = 0; i < reorderedHeaders.size(); i++) {
					final int columnIndex = i;
					headerGrid.addColumn(item -> reorderedHeaders.get(columnIndex))
							.setHeader(reorderedHeaders.get(columnIndex)).setAutoWidth(true)
							.setKey(reorderedHeaders.get(columnIndex));
				}
				headerGrid.setItems(Collections.singletonList(""));

				headerGrid.addColumnReorderListener(event -> {
					if (event.isFromClient()) {
						reorderedHeaders.clear();
						reorderedHeaders.addAll(
								event.getColumns().stream().map(Grid.Column::getKey).collect(Collectors.toList()));
					}
				});

				HorizontalLayout hl = new HorizontalLayout();
				hl.setAlignItems(Alignment.CENTER);
				hl.setJustifyContentMode(JustifyContentMode.END);
				hl.addClassNames(LumoUtility.Margin.Top.MEDIUM);
				hl.setWidthFull();

				btnSubscribe.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
				btnReformatFile.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
				hl.add(btnReformatFile, btnSubscribe);

				dialogLayout.add(new Paragraph(new H5("Die umzuformatieren Datei wird folgende Struktur haben:")),
						headerGrid, hl);
				dialog.add(dialogLayout);

				btnSubscribe.addClickListener(click -> {
					OpenGeoData openGeo = new OpenGeoData();
					openGeo.setHeaders(reorderedHeaders);
					openGeo.setColumns(selectedColumns);
					openGeo.setRelevanteFiles(selectedFiles.stream().map(FileDTO::getUrl).collect(Collectors.toList()));
					openGeo.setFormat(".csv");
					openGeo.setLabel(Utils.extractFilenameWithoutExtension(selectedFiles.iterator().next().getUrl()));
					openGeo.setLastUpdated(LocalDate.now());

					openGeoDataService.createOrUpdateOpenGeoData(openGeo);
					Utils.showSuccessBox("Erfolgreich gespeichert");
				});

				btnReformatFile.setText("Umformatieren");
				btnReformatFile.addClickListener(click -> {
					if (!selectedColumns.isEmpty()) {
						searchOpenGeoDatenService.removeEmptyEntry(selectedColumns);
					}
					if (downloadLink != null) {
						downloadLink.removeFromParent();
					}
					ObjectMapper objectMapper = new ObjectMapper();
					Map<String, Object> payload = new HashMap<>();
					payload.put("relevanteFiles",
							selectedFiles.stream().map(FileDTO::getUrl).collect(Collectors.toList()));
					payload.put("headers", reorderedHeaders);
					payload.put("columns", selectedColumns);
					payload.put("format", ".csv");

					String jsonPayload = "";
					try {
						jsonPayload = objectMapper.writeValueAsString(payload);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}

					String returnedContent = WebUtils
							.postContentWebClient("http://localhost:8080/api/subscription/values", jsonPayload);
					if (returnedContent != null) {
						StreamResource resource = new StreamResource(LocalDateTime.now() + ".csv",
								() -> new ByteArrayInputStream(returnedContent.getBytes()));
						downloadLink = new Anchor();
						downloadLink.setHref(resource);
						downloadLink.getElement().setAttribute("download", true);
						dialog.add(downloadLink);
						downloadLink.getElement().callJsFunction("click");

						Utils.showSuccessBox("Dateien erfolgreich zusammengeführt");
					} else {
						Utils.showErrorBox("Error: No result URL received.");
					}

				});
			} else {
				Utils.showHinweisBox("Bitte wählen Sie mindestens eine Datei aus");
			}
		});

		btnReformat.addClickListener(c -> {
			CsvConfiguration csvConfig = csvConfigService.getCurrentCsvConfiguration(currentFileName);

			ByteArrayOutputStream baos = csvConfigService.reformatFile(currentFileContent, csvConfig.getFilePattern());
			StreamResource resource = new StreamResource(currentFileName + ".csv",
					() -> new ByteArrayInputStream(baos.toByteArray()));
			resource.setContentType("text/csv;charset=UTF-8");
			Anchor downloadLink = new Anchor(resource, "");
			downloadLink.getElement().setAttribute("download", true);
			downloadLink.getElement().callJsFunction("click");
			add(downloadLink);
			Utils.showSuccessBox("Dateien erfolgreich umformatiert.");
		});

		sucheProfilVerticalLayout.setAlignItems(Alignment.START);
		sucheProfilVerticalLayout.setJustifyContentMode(JustifyContentMode.START);
		sucheProfilVerticalLayout.add(horizontalLayout);

		mainHorizontalLayout.add(sucheProfilVerticalLayout);
		mainHorizontalLayout.setWidthFull();
		mainHorizontalLayout.setAlignItems(Alignment.START);
		add(mainHorizontalLayout);
	}

	private void displayZipContentsWithPreview(List<FileDTO> zipFiles) {
		treeGrid.removeAllColumns();
		treeGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
		treeGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
		treeGrid.addClassNames(LumoUtility.Margin.Right.MEDIUM);

		TreeData<FileDTO> treeData = new TreeData<>();
		searchOpenGeoDatenService.fillTreeData(zipFiles, treeData);

		TreeDataProvider<FileDTO> dataProvider = new TreeDataProvider<>(treeData);

		treeGrid.setDataProvider(dataProvider);
		treeGrid.setAllRowsVisible(true);
		treeGrid.addHierarchyColumn(file -> file.getTitle()).setHeader("Dateiname").setFlexGrow(2).setSortable(true);
		treeGrid.addComponentColumn(file -> {
			if (file.getUrl() != null) {
				return null;
			}
			Button previewButton = new Button(new Icon(VaadinIcon.FILE_SEARCH), click -> {
				gridVerticalLayout.removeAll();

				List<String> selectedCols = selectedColumns.computeIfAbsent(file.getTitle(), k -> new ArrayList<>());
				currentFileName = file.getTitle();
				currentFileContent = file.getContent();

				Grid<String[]> grid = createCsvPreviewGridWithSelection(currentFileContent, file.getTitle(),
						selectedCols);

				grid.setSizeFull();
				grid.setAllRowsVisible(true);

				gridVerticalLayout.add(new H4("Vorschau der ersten 50 Datensätzen von " + file.getTitle()), grid,
						new HorizontalLayout(btnPrepareReformat, btnReformat));
				gridVerticalLayout.setWidth("80%");
				mainHorizontalLayout.add(gridVerticalLayout);
			});
			previewButton.setTooltipText("Vorschau");
			return previewButton;
		}).setFlexGrow(1);
		treeGrid.expand(new ArrayList<>(treeData.getRootItems()));

		sucheProfilVerticalLayout.add(treeGrid);
	}

	private Grid<String[]> createCsvPreviewGridWithSelection(String csvContent, String tableName,
			List<String> selectedColumnsList) {
		Grid<String[]> grid = new Grid<>();
		grid.setHeight("30%");

		String[] lines = csvContent.split("\n");
		if (lines.length == 0) {
			return grid;
		}
		String[] headers = lines[0].split(";");
		if (headers.length == 0) {
			return grid;
		}

		List<String[]> data = new ArrayList<>();
		// Math.min(lines.length, 21)
		for (int i = 1; i < Math.min(lines.length, 50); i++) {
			String[] row = lines[i].split(";");
			String[] adjustedRow = new String[headers.length];
			for (int j = 0; j < headers.length; j++) {
				adjustedRow[j] = j < row.length ? row[j] : "";
			}
			data.add(adjustedRow);
		}

		for (int i = 0; i < headers.length; i++) {
			final int columnIndex = i;
			final String headerName = headers[i].trim();

			HorizontalLayout headerLayout = new HorizontalLayout();
			headerLayout.setSpacing(false);
			headerLayout.setPadding(false);
			headerLayout.setMargin(false);

			NativeLabel headerLabel = new NativeLabel(headerName);
			headerLayout.add(headerLabel);

			Checkbox checkbox = new Checkbox();
			checkbox.setValue(selectedColumnsList.contains(headerName));
			checkbox.addValueChangeListener(event -> {
				if (event.getValue()) {
					selectedColumnsList.add(headerName);
					selectedColumns.put(tableName, selectedColumnsList);
				} else {
					selectedColumnsList.remove(headerName);
				}
			});
			headerLayout.add(checkbox);

			grid.addColumn(row -> columnIndex < row.length ? row[columnIndex] : "").setHeader(headerLayout)
					.setResizable(true).setAutoWidth(true);
		}

		grid.setItems(data);
		return grid;
	}

}
