package de.ahu.opendata.GovData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.views.MainLayout;
import de.ahu.opendata.views.OpenLayersMap;

@Route(value = "govdata", layout = MainLayout.class)
public class GridGovData extends VerticalLayout {

	private static final long serialVersionUID = 1L;

	private TextField tfSuchbegriff = new TextField();

	private Grid<DataSetDTO> gridDataSet = new Grid<>();
	private GovDataService govDataService;
	private Konfiguration konfiguration;

	private ComboBox<String> ddOrganization = new ComboBox<String>();

	private ComboBox<String> ddGroup = new ComboBox<String>();

	private ComboBox<String> ddFileFormat = new ComboBox<String>();

	private HorizontalLayout horizontalLayout = new HorizontalLayout();

	private VerticalLayout verticalLayout = new VerticalLayout();

	private VerticalLayout wrapperGridVerticalLayout = new VerticalLayout();

	private Button btnCollectFiles = new Button(new Icon(VaadinIcon.RECORDS));
	private Button btnExportLinks = new Button(new Icon(VaadinIcon.FILE_O));
	private Button btnResetFiler = new Button(new Icon(VaadinIcon.REFRESH));
	private Button btnSuchenDataSet = new Button(new Icon(VaadinIcon.SEARCH));

	private Map<String, String> organizations;
	private Map<String, String> groups;
	private Set<String> foundFiles = new HashSet<>();
	private List<String> initialGroupItems;
	private List<String> initialOrganizationItems;
	private List<String> initialFileFormatItems;

	private String govdataBaseUrl;

	public GridGovData() {
		govDataService = SpringApplicationContext.getBean(GovDataService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		govdataBaseUrl = konfiguration.getGovdataBaseUrl();
		prepareGrid();

		tfSuchbegriff.setPlaceholder("Suchbegriff eingeben");
		ddOrganization.setLabel("Datenbereitsteller");

		organizations = govDataService
				.fetchOrganizationAndGroupGovData(govdataBaseUrl + "organization_list?all_fields=true");
		initialOrganizationItems = organizations.entrySet().stream().map(Map.Entry::getKey)
				.collect(Collectors.toList());
		ddOrganization.setItems(initialOrganizationItems);

		ddOrganization.addValueChangeListener(changed -> {
			if (ddOrganization.getValue() == null) {
				ddOrganization.setItems(initialOrganizationItems);
			}
		});

		groups = govDataService.fetchOrganizationAndGroupGovData(govdataBaseUrl + "group_list?all_fields=true");
		initialGroupItems = groups.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());
		ddGroup.setItems(initialGroupItems);
		ddGroup.setLabel("Kategorien");
		ddGroup.addValueChangeListener(changed -> {
			if (ddGroup.getValue() == null) {
				ddGroup.setItems(initialGroupItems);
			}
		});
		initialFileFormatItems = List.of("application/vnd.ogc.wms_xml", "application/vnd.ogc.wfs_xml", "pdf", "html",
				"wms_srvc", "gml", "download", "zip", "csv");
		ddFileFormat.setItems(initialFileFormatItems);
		ddFileFormat.setLabel("Ressource-Format");
		ddFileFormat.addValueChangeListener(changed -> {
			if (ddFileFormat.getValue() == null) {
				ddFileFormat.setItems(initialFileFormatItems);
			}
		});

		btnSuchenDataSet.setText("Suche starten");
		btnSuchenDataSet.addClickShortcut(Key.ENTER);
		UI currentUI = UI.getCurrent();

		btnSuchenDataSet.addClickListener(click -> {

			String goalUrl = buildUrl(0, 1);
			govDataService.fetchGovDataService(goalUrl).block();
			Integer totalCount = govDataService.getLastResultCount();

			if (totalCount != null) {
				H2 h2Result = new H2(totalCount + " Treffer");
				h2Result.setWidthFull();
				HorizontalLayout toolHorizontalLayout = new HorizontalLayout(h2Result);
				toolHorizontalLayout.setAlignItems(Alignment.STRETCH);

				String tempddGroup = ddGroup.getValue();
				String tempddOrganization = ddOrganization.getValue();
				String tempddFileFormat = ddFileFormat.getValue();

				if (tempddGroup != null && !tempddGroup.isEmpty()) {
					List<String> newGroupItems = govDataService.updateGroupTags();
					ddGroup.setItems(newGroupItems);
					if (newGroupItems.contains(tempddGroup)) {
						ddGroup.setValue(tempddGroup);
					}
				} else {
					ddGroup.setItems(initialGroupItems);
				}

				if (tempddOrganization != null && !tempddOrganization.isEmpty()) {
					List<String> newOrgItems = govDataService.updateOrganizationTags();
					ddOrganization.setItems(newOrgItems);
					if (newOrgItems.contains(tempddOrganization)) {
						ddOrganization.setValue(tempddOrganization);
					}
				} else {
					ddOrganization.setItems(initialOrganizationItems);
				}

				if (tempddFileFormat != null && !tempddFileFormat.isEmpty()) {
					List<String> newFormatItems = govDataService.updateFormatTags();
					ddFileFormat.setItems(newFormatItems);
					if (newFormatItems.contains(tempddFileFormat)) {
						ddFileFormat.setValue(tempddFileFormat);
					}
				} else {
					ddFileFormat.setItems(govDataService.updateFormatTags());
				}

				CallbackDataProvider<DataSetDTO, Void> dataProvider = new CallbackDataProvider<>(query -> {
					int offset = query.getOffset();
					int limit = query.getLimit();
					List<DataSetDTO> dataSetList = govDataService.fetchGovDataService(buildUrl(offset, limit)).block();

					currentUI.access(() -> {
						if (tempddGroup != null && !tempddGroup.isEmpty()) {
							List<String> newGroupItems = govDataService.updateGroupTags();
							ddGroup.setItems(newGroupItems);
							if (newGroupItems.contains(tempddGroup)) {
								ddGroup.setValue(tempddGroup);
							}
						} else {
							ddGroup.setItems(initialGroupItems);
						}

						if (tempddOrganization != null && !tempddOrganization.isEmpty()) {
							List<String> newOrgItems = govDataService.updateOrganizationTags();
							ddOrganization.setItems(newOrgItems);
							if (newOrgItems.contains(tempddOrganization)) {
								ddOrganization.setValue(tempddOrganization);
							}
						} else {
							ddOrganization.setItems(initialOrganizationItems);
						}

						if (tempddFileFormat != null && !tempddFileFormat.isEmpty()) {
							List<String> newFormatItems = govDataService.updateFormatTags();
							ddFileFormat.setItems(newFormatItems);
							if (newFormatItems.contains(tempddFileFormat)) {
								ddFileFormat.setValue(tempddFileFormat);
							}
						} else {
							ddFileFormat.setItems(govDataService.updateFormatTags());
						}
					});

					return dataSetList != null ? dataSetList.stream() : Stream.empty();
				}, query -> totalCount);

				gridDataSet.setDataProvider(dataProvider);
				gridDataSet.setSizeFull();
				wrapperGridVerticalLayout.removeAll();

				toolHorizontalLayout.add(btnCollectFiles);
				wrapperGridVerticalLayout.add(toolHorizontalLayout);
				wrapperGridVerticalLayout.addAndExpand(gridDataSet);
				wrapperGridVerticalLayout.setSizeFull();
				horizontalLayout.add(wrapperGridVerticalLayout);

				Executors.newSingleThreadExecutor().execute(() -> {
					int limit = 1000;
					for (int offset = 0; offset < totalCount; offset += limit) {
						govDataService.fetchGovDataService(buildUrl(offset, limit)).block();
					}
					currentUI.access(() -> {
						if (tempddGroup != null && !tempddGroup.isEmpty()) {
							List<String> newGroupItems = govDataService.updateGroupTags();
							ddGroup.setItems(newGroupItems);
							if (newGroupItems.contains(tempddGroup)) {
								ddGroup.setValue(tempddGroup);
							}
						} else {
							ddGroup.setItems(initialGroupItems);
						}

						if (tempddOrganization != null && !tempddOrganization.isEmpty()) {
							List<String> newOrgItems = govDataService.updateOrganizationTags();
							ddOrganization.setItems(newOrgItems);
							if (newOrgItems.contains(tempddOrganization)) {
								ddOrganization.setValue(tempddOrganization);
							}
						} else {
							ddOrganization.setItems(initialOrganizationItems);
						}

						if (tempddFileFormat != null && !tempddFileFormat.isEmpty()) {
							List<String> newFormatItems = govDataService.updateFormatTags();
							ddFileFormat.setItems(newFormatItems);
							if (newFormatItems.contains(tempddFileFormat)) {
								ddFileFormat.setValue(tempddFileFormat);
							}
						} else {
							ddFileFormat.setItems(govDataService.updateFormatTags());
						}
					});
				});
			}
		});

		btnCollectFiles.setTooltipText("Ressource als Datei exportieren");

		btnCollectFiles.addClickListener(click -> {
			foundFiles = new HashSet<>();

			Dialog dialogCollectFiles = new Dialog();
			dialogCollectFiles.open();
			dialogCollectFiles.setWidth("60%");
			dialogCollectFiles.setResizable(true);
			dialogCollectFiles.setDraggable(true);

			Predicate<OgcWebserviceDTO> formatFilter = ddFileFormat.getValue() != null
					? dto -> dto.getFormat().contains(ddFileFormat.getValue())
							|| dto.getResourceUrl().contains(ddFileFormat.getValue())
					: dto -> true;

			gridDataSet.getLazyDataView().getItems().filter(item -> item.getNumberResources() > 0)
					.flatMap(item -> item.getOgcWebserviceDTO().stream())
					.filter(dto -> dto.getResourceUrl() != null && !dto.getResourceUrl().isEmpty()).filter(formatFilter)
					.map(OgcWebserviceDTO::getResourceUrl).forEach(foundFiles::add);

			StreamResource resource = createExcelResource();
			Anchor downloadLink = new Anchor(resource, "Download Links als Datei");
			downloadLink.getElement().setAttribute("download", true);

			VerticalLayout verticalLayoutCollectFiles = new VerticalLayout();
			HorizontalLayout headerLayout = new HorizontalLayout();
			headerLayout.setAlignItems(Alignment.BASELINE);
			headerLayout.add(new H4(foundFiles.size() + " Treffer gefunden"), downloadLink);
			verticalLayoutCollectFiles.add(headerLayout);

			AtomicInteger counter = new AtomicInteger(1);
			foundFiles.stream().filter(file -> file != null && !file.isBlank())
					.forEach(file -> verticalLayoutCollectFiles.add(new Span(counter.getAndIncrement() + ". " + file)));

			btnExportLinks.addClickListener(c -> {
				downloadLink.setHref(createExcelResource());
			});

			dialogCollectFiles.add(verticalLayoutCollectFiles);
		});

		Stream.of(ddOrganization, ddGroup, ddFileFormat, tfSuchbegriff, btnSuchenDataSet).forEach(item -> {
			item.setWidthFull();
		});

		Image opengeoDataLogo = new Image("images/govdata_logo.png", "itnrw");
		opengeoDataLogo.addClassNames(LumoUtility.MaxWidth.FULL);
		HorizontalLayout logoHl = new HorizontalLayout(opengeoDataLogo);
		logoHl.setWidthFull();

		Paragraph infoParagraph = new Paragraph();
		infoParagraph.add(logoHl);
		infoParagraph.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);
		H4 filterh3 = new H4("Filtermöglichkeiten");
		filterh3.setWidthFull();

		btnResetFiler.setText("Filter zurücksetzen");
		btnResetFiler.addClickListener(reset -> {
			ddOrganization.setValue(null);
			ddFileFormat.setValue(null);
			ddGroup.setValue(null);
			tfSuchbegriff.clear();
		});

		verticalLayout.add(infoParagraph, filterh3, btnResetFiler, ddOrganization, ddGroup, ddFileFormat, tfSuchbegriff,
				btnSuchenDataSet);
		verticalLayout.addClassNames(LumoUtility.Border.RIGHT, LumoUtility.Border.BOTTOM,
				LumoUtility.BorderColor.CONTRAST_30);

		horizontalLayout.setSizeFull();
		verticalLayout.setWidth("20%");
		horizontalLayout.add(verticalLayout);

		add(horizontalLayout);
		setSizeFull();
	}

	private String buildUrl(int start, int rows) {
		StringBuilder goalUrl = new StringBuilder(govdataBaseUrl + "package_search?fq=");
		String organization = organizations.get(ddOrganization.getValue());
		String fileFormat = ddFileFormat.getValue();
		String searchTerm = tfSuchbegriff.getValue().strip();
		String group = groups.get(ddGroup.getValue());

		boolean hasPreviousFilter = false;

		if (group != null && !group.isEmpty()) {
			goalUrl.append("groups:").append(group);
			hasPreviousFilter = true;
		}

		if (organization != null && !organization.isEmpty()) {
			if (hasPreviousFilter) {
				goalUrl.append("+AND+");
			}
			goalUrl.append("organization:").append(organization);
			hasPreviousFilter = true;
		}
		if (fileFormat != null && !fileFormat.isEmpty()) {
			if (hasPreviousFilter) {
				goalUrl.append("+AND+");
			}
			goalUrl.append("res_format:").append(fileFormat);
			hasPreviousFilter = true;
		}

		if (searchTerm != null && !searchTerm.trim().isEmpty()) {
			if (hasPreviousFilter) {
				String[] parts = searchTerm.trim().split("[_\\s]+");
				if (parts.length == 1) {
					goalUrl.append("&q=name:*");
					goalUrl.append(searchTerm.toLowerCase()).append("*");
				} else if (parts.length > 1) {
					for (int i = 0; i < parts.length; i++) {
						if (i < parts.length - 1) {
							goalUrl.append("+AND+");
						}
						goalUrl.append(parts[i]);
					}
				}
			} else {
				String[] parts = searchTerm.trim().split("[_\\s]+");
				if (parts.length >= 1) {
					for (int i = 0; i < parts.length; i++) {
						if (i > 0 && i < parts.length - 1) {
							goalUrl.append("+AND+");
						}
						goalUrl.append(parts[i]);
					}
				} else {
					goalUrl.append(searchTerm.trim());
				}
			}
		}

		goalUrl.append("&rows=").append(rows);
		goalUrl.append("&start=").append(start);
		return goalUrl.toString();
	}

	private StreamResource createExcelResource() {
		return new StreamResource("Links.xlsx", () -> {
			try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				Sheet sheet = workbook.createSheet("Found Files");
				Row headerRow = sheet.createRow(0);
				headerRow.createCell(0).setCellValue("Nr.");
				headerRow.createCell(1).setCellValue("Link");

				AtomicInteger rowNum = new AtomicInteger(1);
				foundFiles.stream().filter(file -> file != null && !file.isBlank()).forEach(file -> {
					Row row = sheet.createRow(rowNum.getAndIncrement());
					row.createCell(0).setCellValue(rowNum.get());
					row.createCell(1).setCellValue(file);
				});

				sheet.autoSizeColumn(0);
				sheet.autoSizeColumn(1);

				workbook.write(out);
				return new ByteArrayInputStream(out.toByteArray());
			} catch (IOException e) {
				throw new RuntimeException("Failed to generate Excel file", e);
			}
		});
	}

	private void prepareGrid() {
		gridDataSet.setSelectionMode(SelectionMode.SINGLE);
		gridDataSet.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
		gridDataSet.addClassNames(Margin.Right.MEDIUM);

		gridDataSet.addColumn(DataSetDTO::getLabel).setHeader("Datensatztitel").setFlexGrow(1).setAutoWidth(false)
				.setResizable(true);
		gridDataSet.addColumn(data -> Utils.formatToGermanDate(data.getLastModified())).setHeader("Letzte Änderung")
				.setFlexGrow(0).setAutoWidth(true);
		gridDataSet.addColumn(DataSetDTO::getPublisherName).setHeader("Veröffentlichende Stelle").setFlexGrow(0)
				.setAutoWidth(true);
		gridDataSet.addColumn(new ComponentRenderer<>(dataset -> {
			Span numberSpan = new Span(String.valueOf(dataset.getNumberResources()));
			numberSpan.setWidthFull();
			numberSpan.addClassNames(LumoUtility.Margin.Left.XLARGE);
			return numberSpan;
		})).setHeader("Anzahl-Ressources").setFlexGrow(0).setAutoWidth(true);
		gridDataSet.addComponentColumn(data -> {
			Button btnGeolocation = new Button(new Icon(VaadinIcon.GLOBE_WIRE));
			if (data.getSpatialValue() != null) {
				btnGeolocation.addClickListener(click -> {
					Dialog geoDialog = new Dialog();
					geoDialog.open();
					geoDialog.setWidth("80%");
					geoDialog.setHeight("80%");
					geoDialog.setResizable(true);

					OpenLayersMap olMap = new OpenLayersMap();
					olMap.showGeolocation(data.getSpatialValue());
					geoDialog.add(olMap);
				});
				return btnGeolocation;
			} else {
				return new Span("");
			}
		}).setHeader("Raumbezug").setFlexGrow(0).setAutoWidth(true);
		gridDataSet.addColumn(new ComponentRenderer<>(dataset -> {
			VerticalLayout layout = new VerticalLayout();
			layout.setPadding(false);
			layout.setSpacing(false);

			Span descriptionSpan = new Span(dataset.getBeschreibung());
			descriptionSpan.getStyle().set("white-space", "normal").set("overflow-wrap", "break-word").set("display",
					"none");

			Button toggleButton = new Button("Mehr Infos");
			toggleButton.getStyle().set("background-color", "#4A90E2").set("color", "white").set("border-radius", "4px")
					.set("padding", "5px 10px");

			toggleButton.addClickListener(event -> {
				if ("Mehr Infos".equals(toggleButton.getText())) {
					descriptionSpan.getStyle().set("display", "block");
					toggleButton.setText("Weniger Infos");
				} else {
					descriptionSpan.getStyle().set("display", "none");
					toggleButton.setText("Mehr Infos");
				}
			});

			layout.add(toggleButton, descriptionSpan);
			return layout;
		}));

		gridDataSet.getElement()
				.executeJs("const style = document.createElement('style');"
						+ "style.textContent = 'vaadin-grid::part(header-cell) { font-weight: bold !important; }';"
						+ "this.shadowRoot.appendChild(style);");

		gridDataSet.setItemDetailsRenderer(new ComponentRenderer<>(GridOgcWebserviceDetails::new,
				GridOgcWebserviceDetails::setOgcWebserviceDetailsGridContent));
	}

}
