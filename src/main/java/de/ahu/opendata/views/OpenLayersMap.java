package de.ahu.opendata.views;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.function.SerializableConsumer;

import de.ahu.opendata.DataUtils.RestDTO;
import de.ahu.opendata.GovData.OgcLayerDTO;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("serial")
@NpmPackage(value = "ol", version = "8.2.0")
@NpmPackage(value = "ol-ext", version = "4.0.13")
@NpmPackage(value = "ol-layerswitcher", version = "4.1.1")
@NpmPackage(value = "ol-popup", version = "5.1.0")
@Tag("openlayers")
@JsModule("./src/openlayers-connector.js")
@Slf4j
public class OpenLayersMap extends Div {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	public OpenLayersMap() {
		this.getParent().ifPresent(null);
		initConnector();

		addDetachListener(detach -> {
			shutDown();
		});
	}

	private void initConnector() {
		runBeforeClientResponse(
				ui -> ui.getPage().executeJs("window.Vaadin.Flow.openLayersConnector.initLazy($0)", getElement()));
	}

	private void runBeforeClientResponse(SerializableConsumer<UI> command) {
		getElement().getNode().runWhenAttached(ui -> ui.beforeClientResponse(this, context -> command.accept(ui)));
	}

	public void shutDown() {
		UI.getCurrent().getPage().executeJs("window.Vaadin.Flow.openLayersConnector.clearAll($0)", getElement());
	}

	public <T extends RestDTO> void showStationsOnMap(List<T> stations) {
		runBeforeClientResponse(ui -> {
			boolean isWetterstation = false;
			if (stations != null && !stations.isEmpty()) {
				isWetterstation = stations.get(0) instanceof WetterStationDTO;
			}
			String jsArray = buildStationsJson(stations);
			ui.getPage().executeJs("window.Vaadin.Flow.openLayersConnector.showStations($0, $1, $2)", getElement(),
					jsArray, isWetterstation);
		});
	}

	public void showGeolocation(String geoJsonString) {
		runBeforeClientResponse(ui -> {
			ui.getPage().executeJs("window.Vaadin.Flow.openLayersConnector.showGeolocation($0, $1)", getElement(),
					geoJsonString);
		});
	}

	public void showOgcLayer(OgcLayerDTO ogcLayer) {
		runBeforeClientResponse(ui -> {
			String ogcLayerJson = buildOgcLayerJson(ogcLayer);
			ui.getPage().executeJs("window.Vaadin.Flow.openLayersConnector.showOgcLayer($0, $1)", getElement(),
					ogcLayerJson);
		});
	}

	private String buildOgcLayerJson(OgcLayerDTO ogcLayer) {
		try {
			return objectMapper.writeValueAsString(ogcLayer);
		} catch (Exception e) {
			e.printStackTrace();
			return "{}";
		}
	}

	private <T extends RestDTO> String buildStationsJson(List<T> stations) {
		try {
			if (stations == null || stations.isEmpty()) {
				return "[]";
			}
			return objectMapper.writeValueAsString(stations);
		} catch (Exception e) {
			e.printStackTrace();
			return "[]";
		}
	}

}
