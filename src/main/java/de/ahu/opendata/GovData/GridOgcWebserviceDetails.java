package de.ahu.opendata.GovData;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.stream.Streams;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import de.ahu.opendata.Utils.SpringApplicationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StyleSheet("style.css")
public class GridOgcWebserviceDetails extends VerticalLayout {

	private static final long serialVersionUID = 1L;
	private OgcWebserviceParser ogcWebserviceParser;
	private Grid<OgcWebserviceDTO> ogcWebServiceGrid = new Grid<OgcWebserviceDTO>();

	public GridOgcWebserviceDetails() {
		ogcWebserviceParser = SpringApplicationContext.getBean(OgcWebserviceParser.class);

		ogcWebServiceGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER,
				GridVariant.LUMO_ROW_STRIPES);
		ogcWebServiceGrid.addClassNames("text-xs", Margin.Left.MEDIUM);

		ogcWebServiceGrid.addColumn(OgcWebserviceDTO::getLabel).setFlexGrow(0).setAutoWidth(true).setHeader("Titel");
		ogcWebServiceGrid.addColumn(OgcWebserviceDTO::getResourceUrl).setFlexGrow(1).setAutoWidth(true)
				.setHeader("Ressource-Url");
		ogcWebServiceGrid.addColumn(OgcWebserviceDTO::getFormat).setHeader("Ressource-Format").setFlexGrow(1)
				.setAutoWidth(true);
		ogcWebServiceGrid.addComponentColumn(resources -> {

			if (StringUtils.containsIgnoreCase(resources.getResourceUrl(), "WFS")
					&& StringUtils.containsIgnoreCase(resources.getFormat(), "WFS")
					|| StringUtils.containsIgnoreCase(resources.getResourceUrl(), "WMS")
							&& StringUtils.containsIgnoreCase(resources.getFormat(), "WMS")
					|| resources.getFormat().isBlank()
							&& StringUtils.containsIgnoreCase(resources.getResourceUrl(), "WMS")) {
				Button btnOgcDetails = new Button(new Icon(VaadinIcon.FILE_PRESENTATION));
				btnOgcDetails.setTooltipText("Detaillierte Information");

				btnOgcDetails.addClickListener(click -> {
					String webServiceTyp = resources.getResourceUrl().contains("WFS") ? "WFS" : "WMS";
					Dialog ogcWebDialog = new Dialog("Detailierte Informationen zu dem eingelesenen " + webServiceTyp);
					ogcWebDialog.open();
					ogcWebDialog.addClassName("responsive-dialog");
					OgcWebserviceDTO parseResult = ogcWebserviceParser.fetchResource(resources.getResourceUrl());

					if (parseResult == null) {
						ogcWebDialog.add(new Span("Keine Informationen verfügbar"));
						return;
					}
					String providerName = parseResult.getProviderName();
					String title = parseResult.getLabel();
					String description = parseResult.getDescription();
					String keywords = parseResult.getKeywords();
					String version = parseResult.getServiceVersion();
					String serviceFees = parseResult.getServiceFees();

					List<OgcLayerDTO> ogcLayers = parseResult.getOgcLayers();

					if (ogcLayers != null) {
						if (webServiceTyp.equals("WFS")) {
							ogcLayers = ogcLayers.stream().sorted((a, b) -> a.getLabel().compareTo(b.getLabel()))
									.toList();
						} else {
							ogcLayers = ogcLayers.stream().sorted((a, b) -> {
								try {
									String labelA = a.getLabel() != null ? a.getLabel() : "";
									String labelB = b.getLabel() != null ? b.getLabel() : "";

									if (labelA.matches("\\d+") && labelB.matches("\\d+")) {
										int numA = Integer.parseInt(labelA);
										int numB = Integer.parseInt(labelB);
										return Integer.compare(numA, numB);
									} else if (labelA.matches("\\d+")) {
										return -1;
									} else if (labelB.matches("\\d+")) {
										return 1;
									} else {
										return labelA.compareTo(labelB);
									}
								} catch (Exception e) {
									return a.getLabel().compareTo(b.getLabel());
								}
							}).toList();
						}
						ogcLayers.stream().forEach(layer -> layer.setUrl(resources.getResourceUrl()));
					}

					Details detailsAnbieter = new Details(new H5("Anbieter"), new Span(providerName));
					Details detailsTitel = new Details(new H5("Titel"), new Span(title));
					Details detailsBeschreibung = new Details(new H5("Beschreibung"), new Span(description));
					Details detailsOycLayers = new Details(new H5("OgcLayers"),
							ogcLayers != null ? showOgcLayers(ogcLayers) : new Span("Kein OgcLayers verfügbar"));
					detailsOycLayers.setWidthFull();

					Details detailsKeyword = new Details(new H5("Schlüsselworte"), new Span(keywords));
					Details detailsVersion = new Details(new H5("Version"), new Span(version));
					Details detailsLizenz = new Details(new H5("Lizenz"), new Span(serviceFees));

					VerticalLayout detailsVL = new VerticalLayout();

					HorizontalLayout anbieterHL = createDetailsWithIcon(detailsAnbieter,
							providerName != null && !providerName.isEmpty());
					HorizontalLayout titelHL = createDetailsWithIcon(detailsTitel, title != null && !title.isEmpty());
					HorizontalLayout beschreibungHL = createDetailsWithIcon(detailsBeschreibung,
							description != null && !description.isEmpty());
					HorizontalLayout ogcLayersHL = createDetailsWithIcon(detailsOycLayers,
							ogcLayers != null && ogcLayers.size() > 0);
					HorizontalLayout keywordHL = createDetailsWithIcon(detailsKeyword,
							keywords != null && !keywords.isEmpty());
					HorizontalLayout versionHL = createDetailsWithIcon(detailsVersion,
							version != null && !version.isEmpty());
					HorizontalLayout lizenzHL = createDetailsWithIcon(detailsLizenz,
							serviceFees != null && !serviceFees.isEmpty());

					Streams.of(detailsAnbieter, detailsTitel, detailsBeschreibung, detailsOycLayers, detailsKeyword,
							detailsVersion, detailsLizenz).forEach(details -> {
								details.setOpened(true);
							});
					Streams.of(anbieterHL, titelHL, beschreibungHL, ogcLayersHL, keywordHL, versionHL, lizenzHL)
							.forEach(vl -> {
								vl.addClassNames(LumoUtility.Margin.Bottom.NONE);
							});

					ogcLayersHL.setWidthFull();
					detailsVL.add(anbieterHL, titelHL, beschreibungHL, keywordHL, versionHL, lizenzHL, ogcLayersHL);
					ogcWebDialog.add(detailsVL);

					ogcWebDialog.setMaxHeight("100%");
					ogcWebDialog.setWidth("80%");

				});

				return btnOgcDetails;
			}
			return new Span("");
		});
		ogcWebServiceGrid.setHeight("250px");
		ogcWebServiceGrid.setMaxHeight("500px");

		add(ogcWebServiceGrid);

	}

	private Component showOgcLayers(List<OgcLayerDTO> ogcLayers) {
		Grid<OgcLayerDTO> gridOgcLayers = new Grid<OgcLayerDTO>();
		gridOgcLayers.setItems(ogcLayers);
		if (ogcLayers.isEmpty()) {
			return new Span("Kein OgcLayers verfügbar");
		}
		gridOgcLayers.addColumn(OgcLayerDTO::getLabel).setFlexGrow(1).setAutoWidth(true).setHeader("Titel");
		gridOgcLayers.addColumn(OgcLayerDTO::getDescription).setFlexGrow(2).setAutoWidth(true)
				.setHeader("Beschreibung");

//		if (!ogcLayers.isEmpty() && ogcLayers.get(0).getUrl().contains("WFS")) {
//			gridOgcLayers.addComponentColumn(ogcLayer -> {
//				Button btnShowLayer = new Button(new Icon(VaadinIcon.MAP_MARKER));
//
//				btnShowLayer.addClickListener(click -> {
//					Dialog ogcLayerDialog = new Dialog();
//					ogcLayerDialog.open();
//					ogcLayerDialog.setSizeFull();
//
//					OpenLayersMap mapView = new OpenLayersMap();
//					mapView.showOgcLayer(ogcLayer);
//					ogcLayerDialog.add(mapView);
//				});
//				return btnShowLayer;
//			}).setFlexGrow(1).setAutoWidth(true).setHeader("");
//		}
		gridOgcLayers.setWidthFull();
		gridOgcLayers.setAllRowsVisible(true);

		Div container = new Div(gridOgcLayers);
		container.getStyle().set("overflow", "auto");
		container.setHeight("100%");
		container.setWidthFull();
		return gridOgcLayers;
	}

	private HorizontalLayout createDetailsWithIcon(Details details, boolean isValid) {
		Icon icon;
		if (isValid) {
			icon = VaadinIcon.CHECK_CIRCLE.create();
			icon.setColor("green");
		} else {
			icon = VaadinIcon.WARNING.create();
			icon.setColor("gold");
		}
		icon.getStyle().set("width", "var(--icon-size-small)");
		icon.getStyle().set("height", "var(--icon-size-small)");
		icon.getStyle().set("min-width", "var(--icon-size-small)");
		icon.getStyle().set("min-height", "var(--icon-size-small)");

		HorizontalLayout layout = new HorizontalLayout(icon, details);
		layout.setAlignItems(FlexComponent.Alignment.BASELINE);

		return layout;
	}

	public void setOgcWebserviceDetailsGridContent(DataSetDTO dataSetDTO) {
		ogcWebServiceGrid.setItems(dataSetDTO.getOgcWebserviceDTO());
	}

}
