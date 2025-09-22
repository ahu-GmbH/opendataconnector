package de.ahu.opendata.ViewUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow.HeaderCell;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.ahu.opendata.CsvConfiguration.CsvConfigService;
import de.ahu.opendata.CsvConfiguration.CsvConfiguration;
import de.ahu.opendata.CsvConfiguration.HeaderMapping;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;

@SuppressWarnings("serial")
public class GridHeaderMapping extends VerticalLayout {

	private CsvConfigService csvConfigService;
	private CsvConfiguration currentCsvConfiguration;
	private Konfiguration konfiguration;

	private Grid<HeaderMapping> gridHeaderMapping = new Grid<>(HeaderMapping.class, false);
	private final Dialog dialogCsvConfig = new Dialog();

	private ListDataProvider<HeaderMapping> dataProvider = null;
	private List<HeaderMapping> currentHeaderMappings = new ArrayList<>();
	private Select<String> ddRollenTyp = new Select<>();

	private HorizontalLayout btnLayout = new HorizontalLayout();
	private HorizontalLayout hlRollenTyp = new HorizontalLayout();

	private Button btnExport = new Button("Exportieren");
	private Button btnStoreConfig = new Button("Konfiguration speichern");
	private Button addRowButton = new Button("Neue Zeile hinzufügen");
	private Button btnReformat = new Button("Umformatieren");
	private FileDTO currentFile;
	private List<HeaderCell> headerCells = null;

	public GridHeaderMapping(CsvConfiguration currtConfig, FileDTO currentFile, List<HeaderCell> headerRows) {
		this.csvConfigService = SpringApplicationContext.getBean(CsvConfigService.class);
		this.konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		this.currentFile = currentFile;
		this.currentCsvConfiguration = currtConfig;
		this.headerCells = headerRows;

		initializeUI();
		setupListeners();

		if (currtConfig != null) {
			createCsvConfigurationDialog(currtConfig);
			initGridHeaderMapping();
		} else {
			createCsvConfigurationDialog(null);
		}
	}

	private void initializeUI() {
		ddRollenTyp.setLabel("Rollen-Typ");
		ddRollenTyp.setItems(List.of("MeteorologischeMessstelle", "Grundwassermessstelle", "Gewaesser"));
		ddRollenTyp.setWidth("25%");

		btnReformat.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY);
		add(gridHeaderMapping);
	}

	private void setupListeners() {
		btnReformat.addClickListener(c -> {
			ddRollenTyp.setValue(null);
			CsvConfiguration csvConfig = csvConfigService.getCurrentCsvConfiguration(currentFile.getTitle());
			createCsvConfigurationDialog(csvConfig);
		});

		btnExport.addClickListener(c -> exportReformattedFile());

		btnStoreConfig.addClickListener(c -> {
			if (ddRollenTyp.getValue() != null) {
				currentCsvConfiguration.setHeaderMappings(new ArrayList<>(dataProvider.getItems()));
				CsvConfiguration savedConfig = csvConfigService.AddOrUpdateCsvConfiguration(currentCsvConfiguration);
				if (savedConfig != null) {
					currentHeaderMappings = csvConfigService.fetchHeaderMappingByConfigId(savedConfig.getId());
					initGridHeaderMapping();
				}
				Utils.showSuccessBox("Konfiguration erfolgreich gespeichert.");
			} else {
				Utils.showHinweisBox("Rollen-Typ bitte auswählen");
			}

		});

		addRowButton.addClickListener(e -> {
			HeaderMapping newRow = new HeaderMapping();
			newRow.setLabel(currentCsvConfiguration.getFilePattern());
			currentHeaderMappings.add(newRow);
			dataProvider.refreshAll();
		});

		ddRollenTyp.addValueChangeListener(event -> updateRoleType(event.getValue()));
	}

	private void exportReformattedFile() {
		CsvConfiguration csvConfig = csvConfigService.getCurrentCsvConfiguration(currentFile.getTitle());

		if (csvConfig != null) {
			ByteArrayOutputStream baos = csvConfigService.reformatFile(currentFile.getFilePath(),
					csvConfig.getFilePattern());

			StreamResource resource = new StreamResource(currentFile.getTitle() + ".csv",
					() -> new ByteArrayInputStream(baos.toByteArray()));

			UI.getCurrent().access(() -> {
				Anchor downloadLink = new Anchor(resource, "");
				downloadLink.getElement().setAttribute("download", true);
				dialogCsvConfig.add(downloadLink);
				downloadLink.getElement().callJsFunction("click");
			});

			Utils.showSuccessBox("Datei erfolgreich exportiert.");
		}
	}

	private void updateRoleType(String selectedValue) {
		if (selectedValue == null)
			return;

//		HeaderMapping mapping = currentHeaderMappings.stream()
//				.filter(hm -> "ROLLENTYP".equalsIgnoreCase(hm.getTargetHeader())).findFirst().orElseGet(() -> {
//					HeaderMapping newMapping = new HeaderMapping();
//					newMapping.setLabel(currentCsvConfiguration.getFilePattern());
//					newMapping.setTargetHeader("ROLLENTYP");
//					currentHeaderMappings.add(newMapping);
//					return newMapping;
//				});

//		mapping.setFixedValue(selectedValue);
		currentCsvConfiguration.setRoleType(selectedValue);

		dataProvider.refreshAll();
	}

	private void createCsvConfigurationDialog(CsvConfiguration csvConfig) {
		dialogCsvConfig.removeAll();
		dialogCsvConfig.setWidth("60%");

		if (csvConfig == null) {
			dialogCsvConfig.getHeader().add(new H5("Für die Datei " + currentFile.getTitle()
					+ " noch keine Konfiguration vorhanden. Erstellen Sie eine neue!"));
			initializeNewCsvConfiguration();
		} else {
			currentCsvConfiguration = csvConfig;
			currentHeaderMappings = csvConfigService.fetchHeaderMappingByConfigId(csvConfig.getId());
		}

		setupDialogLayout();
		dialogCsvConfig.open();
	}

	private void initializeNewCsvConfiguration() {
		List<String> headers = headerCells.stream().map(HeaderCell::getText).toList();

		CsvConfiguration newCsvConfiguration = new CsvConfiguration();
		newCsvConfiguration.setLabel(Utils.extractAndFormatFileName(currentFile.getTitle()));
		newCsvConfiguration.setFilePattern(currentFile.getTitle().replaceFirst("\\.[^.]+$", ""));
		newCsvConfiguration.setHeaderMappings(new ArrayList<>());

		newCsvConfiguration.getHeaderMappings()
				.add(new HeaderMapping(newCsvConfiguration.getFilePattern(), "IGNORE", null, "1"));

		for (String header : headers) {
			HeaderMapping headerMapping = new HeaderMapping();
			headerMapping.setLabel(newCsvConfiguration.getFilePattern());
			headerMapping.setSourceHeader(header);
			headerMapping.setTargetHeader(header);
			newCsvConfiguration.getHeaderMappings().add(headerMapping);
		}

		currentCsvConfiguration = newCsvConfiguration;
		currentHeaderMappings = newCsvConfiguration.getHeaderMappings();
		initGridHeaderMapping();
	}

	private void setupDialogLayout() {
		hlRollenTyp.removeAll();
		HorizontalLayout addRowHl = new HorizontalLayout(JustifyContentMode.END, addRowButton);
		addRowHl.setWidth("75%");
		hlRollenTyp.add(ddRollenTyp, addRowHl);
		hlRollenTyp.setAlignItems(Alignment.BASELINE);
		hlRollenTyp.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

		HorizontalLayout exportLayout = new HorizontalLayout(JustifyContentMode.START, btnExport);
		exportLayout.setWidth("50%");
		HorizontalLayout configLayout = new HorizontalLayout(JustifyContentMode.END, btnStoreConfig);
		configLayout.setWidth("50%");
		btnLayout.removeAll();
		btnLayout.add(exportLayout, configLayout);
		btnLayout.addClassNames(LumoUtility.Margin.Top.MEDIUM);

		dialogCsvConfig.add(hlRollenTyp, gridHeaderMapping, btnLayout);
	}

	public void initGridHeaderMapping() {
		gridHeaderMapping.removeAllColumns();
		dataProvider = new ListDataProvider<>(currentHeaderMappings);
		gridHeaderMapping.setDataProvider(dataProvider);
		gridHeaderMapping.setAllRowsVisible(true);

		gridHeaderMapping.addColumn(HeaderMapping::getSourceHeader).setHeader("Quellspalte").setFlexGrow(2)
				.setSortable(true).setResizable(true);

		gridHeaderMapping.addComponentColumn(hm -> {
			TextField targetField = new TextField();
			targetField.setValue(Optional.ofNullable(hm.getTargetHeader()).orElse(""));
			targetField.setWidthFull();
			targetField.addValueChangeListener(e -> hm.setTargetHeader(e.getValue()));
			return targetField;
		}).setHeader("Zielspalte").setFlexGrow(1).setSortable(true);

		gridHeaderMapping.addComponentColumn(hm -> {
			TextField fixedField = new TextField();
			fixedField.setValue(Optional.ofNullable(hm.getFixedValue()).orElse(""));
			fixedField.setWidthFull();
			fixedField.addValueChangeListener(e -> hm.setFixedValue(e.getValue()));
			return fixedField;
		}).setHeader("Fester Wert").setFlexGrow(1).setSortable(true);

		gridHeaderMapping.addComponentColumn(hm -> createDeleteButton(hm)).setFlexGrow(0);
	}

	private Button createDeleteButton(HeaderMapping headerMapping) {
		Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
		deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		deleteButton.addClickListener(e -> {
			Dialog confirmDialog = new Dialog();
			confirmDialog.setHeaderTitle("Bestätigung");
			VerticalLayout dialogLayout = new VerticalLayout(
					new H5("Sind Sie sicher, dass Sie diese Zeile löschen möchten?"));

			Button confirm = new Button("Ja", evt -> {
				if (headerMapping.getId() != null) {
					csvConfigService.deleteHeaderMapping(headerMapping);
				}
				currentHeaderMappings.remove(headerMapping);
				dataProvider.refreshAll();
				Utils.showSuccessBox("Header " + headerMapping.getTargetHeader() + " gelöscht.");
				confirmDialog.close();
			});
			Button cancel = new Button("Nein", evt -> confirmDialog.close());
			confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
			cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

			confirmDialog.add(dialogLayout, new HorizontalLayout(confirm, cancel));
			confirmDialog.open();
		});
		return deleteButton;
	}

}
