package de.ahu.opendata.OpenDataNrw;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.SAXException;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.ServiceUtils.CrawlerFileService;
import de.ahu.opendata.ServiceUtils.MetadataParserService;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@Slf4j
public class GridOpenDataNrw extends VerticalLayout {
	private String openGeoDataBaseUrl;

	private String geodatenkatalogBaseUrl;

	private Button btCrawl = new Button("Durchsuchen");

	private VerticalLayout verticalLayout = new VerticalLayout();

	private Select<String> ddMainCategories = new Select<String>();

	private Select<String> ddSubCategories = new Select<String>();

	private Select<String> ddSubSubCategories = new Select<String>();

	private MultiSelectComboBox<String> cbDateiType = new MultiSelectComboBox<String>();

	private TreeDataProvider<FileDTO> dataProvider;
	private TreeData<FileDTO> treeData;

	private final TreeGrid<FileDTO> treeGrid = new TreeGrid<>();
	private VerticalLayout wrapperVerticalLayout = new VerticalLayout();
	private HorizontalLayout horizontalLayout = new HorizontalLayout();

	private OpenDataNrwService opendataService;
	private CrawlerFileService crawlerService;
	private Konfiguration konfiguration;

	private Map<String, String> subCategories = new HashMap<String, String>();

	private Map<String, String> subsubCategories = new HashMap<String, String>();

	private String finalUrl;

	public GridOpenDataNrw() {
		opendataService = SpringApplicationContext.getBean(OpenDataNrwService.class);
		konfiguration = SpringApplicationContext.getBean(Konfiguration.class);
		crawlerService = SpringApplicationContext.getBean(CrawlerFileService.class);
		openGeoDataBaseUrl = konfiguration.getOpenGeoDataBaseUrl();
		geodatenkatalogBaseUrl = konfiguration.getGeodatenKatalogBaseUrl();

		Map<String, String> categories = opendataService.fetchCategories(openGeoDataBaseUrl);
		List<String> title = categories.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());

		ddMainCategories.setItems(title);
		ddMainCategories.setWidthFull();
		ddMainCategories.setLabel("Hauptkategorie");

		ddSubCategories.setWidth("400px");

		cbDateiType.setLabel("Die MimeType, nach den Sie durchsuchen möchten.");
		cbDateiType.setItems(List.of(".csv", ".zip", ".xlsx", ".txt", ".pdf"));

		Stream.of(ddMainCategories, ddSubCategories, ddSubSubCategories).forEach(btn -> {
			btn.setWidthFull();
		});
		cbDateiType.setWidthFull();

		ddMainCategories.addValueChangeListener(c -> {
			ddSubCategories.setLabel("Unterkategorie für " + c.getValue());
			subCategories = opendataService.fetchCategories(openGeoDataBaseUrl + categories.get(c.getValue()) + "/");

			List<String> titelSubCategories = subCategories.entrySet().stream().map(Map.Entry::getKey)
					.collect(Collectors.toList());
			ddSubCategories.setItems(titelSubCategories);
			verticalLayout.add(ddSubCategories);

			if (ddSubCategories.getValue() == null) {
				ddSubSubCategories.removeFromParent();
				cbDateiType.removeFromParent();
				btCrawl.removeFromParent();
			}

		});

		ddSubCategories.addValueChangeListener(c -> {
			if (c.getValue() == null) {
				return;
			}
			ddSubSubCategories.setLabel("Unterkategorie für " + c.getValue());
			cbDateiType.setValue(List.of());
			finalUrl = openGeoDataBaseUrl + categories.get(ddMainCategories.getValue()) + "/"
					+ subCategories.get(c.getValue()) + "/";
			subsubCategories = opendataService.fetchCategories(finalUrl);

			if (subsubCategories.isEmpty()) {
				verticalLayout.add(cbDateiType, btCrawl);
			} else {
				List<String> titelSubSubCategories = subsubCategories.entrySet().stream().map(Map.Entry::getKey)
						.collect(Collectors.toList());
				ddSubSubCategories.setItems(titelSubSubCategories);
				verticalLayout.add(ddSubSubCategories, cbDateiType, btCrawl);
			}
		});

		btCrawl.addClickListener(e -> {
			String mimePattern = "";
			if (cbDateiType.getValue().isEmpty()) {
				mimePattern = cbDateiType.getListDataView().getItems().map(mime -> mime.replace(".", ""))
						.collect(Collectors.joining("|"));
			} else {
				mimePattern = cbDateiType.getValue().stream().map(mime -> mime.replace(".", ""))
						.collect(Collectors.joining("|"));
			}

			if (ddSubSubCategories.getValue() == null) {
				crawlerService = new CrawlerFileService(finalUrl, 5);
				try {
					crawlerService.crawlFiles(finalUrl, 0, mimePattern);
				} finally {
					crawlerService.shutdown();
				}
			} else {
				String goalUrl = finalUrl + subsubCategories.get(ddSubSubCategories.getValue());
				if (goalUrl.endsWith("/null") && goalUrl != null) {
					goalUrl = goalUrl.substring(0, goalUrl.length() - "/null".length());
				}
				crawlerService = new CrawlerFileService(goalUrl, 5);
				try {
					crawlerService.crawlFiles(goalUrl, 0, mimePattern);
				} finally {
					crawlerService.shutdown();
				}
			}
			setupTreeGrid(crawlerService.fetchFoundFiles().stream().collect(Collectors.toList()));
			wrapperVerticalLayout.removeAll();
			wrapperVerticalLayout.add(new H2(crawlerService.fetchFoundFiles().size() + " Treffer"));
			wrapperVerticalLayout.add(treeGrid);
			wrapperVerticalLayout.setWidth("75%");
			horizontalLayout.addAndExpand(wrapperVerticalLayout);
			horizontalLayout.setSizeFull();
		});

		btCrawl.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
		btCrawl.addClickShortcut(Key.ENTER);

		Anchor link = new Anchor(konfiguration.getOpenGeoDataBaseUrl(), "OpenGeodata.NRW");
		link.setTarget("_blank");

		Image opengeoDataLogo = new Image("images/itnrw_logo.png", "itnrw");

		Paragraph infoParagraph = new Paragraph();
		infoParagraph.add(new HorizontalLayout(opengeoDataLogo), new Span("Weitere Information finden Sie unter "),
				link);
		infoParagraph.addClassNames(LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM,
				LumoUtility.Background.BASE);
		infoParagraph.setWidthFull();
		verticalLayout.setWidth("25%");
		verticalLayout.add(infoParagraph, ddMainCategories);
		verticalLayout.addClassNames(LumoUtility.BorderColor.CONTRAST_30);

		horizontalLayout.setWidthFull();
		horizontalLayout.setHeightFull();
		horizontalLayout.add(verticalLayout);

		addAndExpand(horizontalLayout);
		setSizeFull();
	}

	private void setupTreeGrid(List<FileDTO> files) {
		treeGrid.removeAllColumns();

		treeGrid.setSizeFull();
		treeGrid.setSelectionMode(SelectionMode.SINGLE);
		treeGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
		treeGrid.addClassNames(Margin.Right.MEDIUM);

		Map<String, List<FileDTO>> groupedByPath = files.stream().collect(Collectors.groupingBy(FileDTO::getTitle));

		treeData = new TreeData<>();

		groupedByPath.forEach((title, fileList) -> {
			List<FileDTO> sortedFileList = fileList.stream().sorted(Comparator.comparing(FileDTO::getLastModified))
					.collect(Collectors.toList());

			FileDTO groupNode = new FileDTO(title, null, null);
			treeData.addItem(null, groupNode);
			sortedFileList.forEach(file -> {
				treeData.addItem(groupNode, file);
			});
			if (!sortedFileList.isEmpty()) {
				groupNode.setMetadataId(sortedFileList.get(0).getMetadataId());
			}
		});

		dataProvider = new TreeDataProvider<>(treeData);
		treeGrid.setDataProvider(dataProvider);

		Grid.Column<FileDTO> fileContentColumn = treeGrid.addHierarchyColumn(file -> {
			return Utils.extractFileName(file);
		}).setHeader("Dateiname").setFlexGrow(2).setAutoWidth(true).setResizable(true).setSortable(true);

		treeGrid.addItemClickListener(event -> {
			if (event.getColumn() == fileContentColumn) {
				FileDTO selectedItem = event.getItem();
				if (selectedItem.getUrl() != null) {
					Dialog dialog = new Dialog();

					if (selectedItem.getUrl().endsWith(".zip")) {
						dialog.setHeight("150px");
						dialog.add(new H5("Möchten Sie die Datei herunterladen?"));
						Button btDownload = new Button("Ja");
						Anchor downloadLink = new Anchor(selectedItem.getUrl(), "");
						downloadLink.getElement().setAttribute("download", true);
						downloadLink.add(btDownload);

						btDownload.addClickListener(e -> {
							dialog.close();
						});

						Button btClose = new Button("Nein");
						btClose.addClickListener(e -> dialog.close());

						HorizontalLayout dlHorizontalLayout = new HorizontalLayout(downloadLink, btClose);
						dlHorizontalLayout.setJustifyContentMode(JustifyContentMode.CENTER);
						dlHorizontalLayout.setPadding(true);

						dialog.add(dlHorizontalLayout);

					} else {
						dialog.setWidthFull();
						dialog.setHeight("800px");
						InputStream fileInputStream = opendataService.fetchFileContent(selectedItem.getUrl());
						dialog.add(new FileView(fileInputStream, selectedItem));
					}
					dialog.open();
				}
			}
		});

		treeGrid.addColumn(file -> file.getType() != null ? file.getType() : "").setHeader("Type").setFlexGrow(1)
				.setAutoWidth(true).setSortable(true);
		treeGrid.addColumn(FileDTO::getLastModified).setHeader("Letzte Änderung").setFlexGrow(1).setAutoWidth(true)
				.setSortable(true);
		treeGrid.addColumn(FileDTO::getFileSize).setHeader("Dateigröße").setFlexGrow(1).setAutoWidth(true)
				.setSortable(true);

		treeGrid.addComponentColumn(file -> {
			if (file.getUrl() == null) {
				Button btShowMetaData = new Button(new Icon(VaadinIcon.EXTERNAL_LINK));
				btShowMetaData.addThemeVariants(ButtonVariant.LUMO_SMALL);
				btShowMetaData.addClickListener(event -> {
					Dialog dialog = new Dialog();
					dialog.setHeaderTitle("Metadaten-Information");
					dialog.setResizable(true);

					Anchor link = new Anchor(
							geodatenkatalogBaseUrl + "gdi-de/srv/ger/catalog.search#/metadata/" + file.getMetadataId(),
							"Geodatenkatalog.de");
					link.setTarget("_blank");

					VerticalLayout vlMetadata = new VerticalLayout();
					vlMetadata.setPadding(true);
					vlMetadata.setSpacing(true);

					Paragraph infoParagraph = new Paragraph();
					infoParagraph.add(new Span("Die Metadaten werden von Geodatenkatalog gepflegt."),
							new Span("Weitere Information finden Sie unter "), link);
					vlMetadata.add(infoParagraph);
					String gdk_URL = geodatenkatalogBaseUrl + "geonetwork/srv/api/0.1/records/" + file.getMetadataId()
							+ "/formatters/json?";

					InputStream xmlStream = opendataService.fetchXMLFromCatalog(gdk_URL);
					if (xmlStream == null) {
						Utils.showHinweisBox("Der Metadatensatz ist aktuell nicht verfügbar");
						return;
					}
					MetadataParserService parser = new MetadataParserService();
					try {
						MetadataDTO metadata = parser.parse(xmlStream);

						Span labelText = new Span();
						labelText.getElement().setProperty("innerHTML", "<b>Titel:</b> " + metadata.getLabel());

						Span abstractText = new Span();
						abstractText.getElement().setProperty("innerHTML",
								"<b>Beschreibung:</b> " + metadata.getAbstractText());

						Span referenceSystemCode = new Span();
						referenceSystemCode.getElement().setProperty("innerHTML",
								"<b>Referenzsysteme:</b> " + metadata.getReferenceSystemCode());

						Span keyWords = new Span();
						keyWords.getElement().setProperty("innerHTML",
								"<b>Schlüsselwörter:</b> " + StringUtils.join(metadata.getKeywords(), ", "));

						Span spatialExtent = new Span();
						spatialExtent.getElement().setProperty("innerHTML", "<b>Räumliche Ausdehnung:</b> ");

						vlMetadata.add(labelText, abstractText, referenceSystemCode, keyWords);
						vlMetadata.setSizeFull();

						dialog.add(vlMetadata);

					} catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException e) {
						e.printStackTrace();
					}
					dialog.setWidth("60%");
					dialog.setMaxHeight("80%");
					dialog.setResizable(true);
					dialog.setDraggable(true);

					Button closeButton = new Button("Abschließen", e -> dialog.close());
					closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
					dialog.getFooter().add(closeButton);

					dialog.open();
				});
				return btShowMetaData;
			} else {
				return null;
			}
		}).setHeader("Metadaten").setFlexGrow(1).setAutoWidth(true);
	}

}
