import Map from 'ol/Map';
import View from 'ol/View';
import VectorLayer from 'ol/layer/Vector';
import TileLayer from 'ol/layer/Tile';
import OSM from 'ol/source/OSM';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import { Style, Icon, Stroke, Fill } from 'ol/style';
import Overlay from 'ol/Overlay';
import { fromLonLat, transformExtent } from 'ol/proj';
import GeoJSON from 'ol/format/GeoJSON';
import TileWMS from 'ol/source/TileWMS';
import GML3 from "ol/format/GML3";
import { createStringXY } from 'ol/coordinate';
import MousePosition from 'ol/control/MousePosition';
import { FullScreen, ScaleLine, defaults as defaultControls } from 'ol/control';
import Attribution from 'ol/control/Attribution.js';

import * as olProj from 'ol/proj';
import proj4 from 'proj4';
import { register } from 'ol/proj/proj4';
import { setUserProjection } from 'ol/proj';


let map;
let stationLayer;
let geoLayer;
let ogcLayer;
let popup;
const utmProjName = 'EPSG:3857';
proj4.defs(utmProjName, "+proj=utm +zone=33 +ellps=ETRS89 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs +type=crs");
proj4.defs('EPSG:4326', "+proj=longlat +datum=WGS84 +no_defs");
proj4.defs('EPSG:25833', '+proj=utm +zone=33 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs');
register(proj4);

const utmProjection = olProj.get(utmProjName);

const mousePositionControl = new MousePosition({
	coordinateFormat: createStringXY(0),
	projection: utmProjection
});

setUserProjection(utmProjection);

const style = document.createElement('style');
style.innerHTML = `
.ol-fullscreen {
  		position: absolute;
   		bottom: 10px; 
   		left: 10px;
   		z-index: 1000;
}
.ol-popup {
    position: absolute;
    background-color: #f9f9f9; /* Slightly off-white for a softer look */
    padding: 10px 15px; /* More padding for better spacing */
    border: 1px solid #999; /* Slightly darker border */
    border-radius: 5px; /* Softer corners */
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); /* Default shadow */
    bottom: 12px;
    left: -50px;
    min-width: 120px; /* Slightly wider for better readability */
    text-align: left; /* Left-align for better text flow */
    font-family: 'Arial', sans-serif; /* Clean, modern font */
    transition: transform 0.2s ease, box-shadow 0.2s ease; /* Smooth transition for hover effects */
}

.ol-popup:hover {
    transform: scale(1.05); /* Slightly enlarge the popup on hover */
    box-shadow: 0 4px 10px rgba(0, 0, 0, 0.4); /* Deeper shadow for a "lifted" effect */
}

.ol-popup:after, .ol-popup:before {
    top: 100%;
    border: solid transparent;
    content: " ";
    height: 0;
    width: 0;
    position: absolute;
    pointer-events: none;
    transition: border-top-color 0.2s ease; /* Smooth transition for the arrow color */
}

.ol-popup:after {
    border-top-color: #f9f9f9; /* Match the background color */
    border-width: 10px;
    left: 50%; /* Center the arrow */
    margin-left: -10px;
}

.ol-popup:before {
    border-top-color: #999; /* Match the border color */
    border-width: 11px;
    left: 50%;
    margin-left: -11px;
}

/* Ensure the arrow's border color updates if the popup's border color changes on hover */
.ol-popup:hover:after {
    border-top-color: #f9f9f9; /* Keep the arrow color consistent with the background */
}

.ol-popup:hover:before {
    border-top-color: #999; /* Keep the arrow's border color consistent */
}
  
.ol-attribution {
        position: absolute;
        bottom: 10px;
        right: 10px;
        background-color: rgba(255, 255, 255, 0.8); 
        padding: 2px 5px;
        border-radius: 3px;
}
.ol-attribution ul {
        margin: 0;
        padding: 0;
        list-style: none;
        display: inline;
}
.ol-attribution li {
        display: inline;
        margin-right: 5px;
}
.detailed-popup {
    padding: 15px; /* More padding for a spacious feel */
    background: #f9f9f9; /* Slightly off-white background */
    border: 1px solid #999; /* Slightly darker border */
    border-radius: 8px; /* Softer, modern corners */
    box-shadow: 0 3px 8px rgba(0, 0, 0, 0.2); /* Deeper shadow for depth */
    max-width: 400px !important; /* Keep the max width */
    width: 400px; /* Fixed width */
    box-sizing: border-box;
    font-family: 'Arial', sans-serif; /* Clean, modern font */
    transition: transform 0.2s ease, box-shadow 0.2s ease; /* Smooth transition for hover effects */
}
.detailed-popup:hover {
    transform: scale(1.02); /* Slight scale on hover for interactivity */
    box-shadow: 0 5px 12px rgba(0, 0, 0, 0.3); /* Deeper shadow on hover */
}
.detailed-popup h3 {
    margin: 0 0 15px 0; /* More spacing below the title */
    color: #2c3e50; /* Darker color for the title */
    font-size: 18px; /* Slightly larger title */
    font-weight: 600; /* Bold but not too heavy */
    border-bottom: 1px solid #ddd; /* Subtle separator line */
    padding-bottom: 8px; /* Space between title and line */
}
.detailed-popup div {
    margin: 8px 0; /* More spacing between rows */
    font-size: 14px; /* Readable font size */
    color: #333; /* Darker text for readability */
}
.detailed-popup strong {
    color: #555; /* Slightly lighter color for labels */
    font-weight: 500; /* Medium weight for labels */
    margin-right: 5px; /* Space between label and value */
}
.detailed-popup .status-normal {
    color: #27ae60; /* Green for "normal" status */
    font-weight: 500;
}
.detailed-popup .status-unknown {
    color: #7f8c8d; /* Gray for "unknown" status */
    font-style: italic;
}
`;
document.head.appendChild(style);

const defaultCenter = [1025623.94, 6605604.73];
window.Vaadin.Flow.openLayersConnector = {

	initLazy: function(c) {
		console.trace();
		if (c.$connector) {
			return;
		}

		c.$connector = {};

		const vectorSource = new VectorSource();
		stationLayer = new VectorLayer({
			source: vectorSource,
			style: function(feature) {
				const isWeatherStation = feature.get('type') === 'weather';
				if (isWeatherStation) {
					return new Style({
						image: new Icon({
							anchor: [0.5, 1],
							src: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png'
						})
					});
				} else {
					const effectiveState = feature.get('pegelStatus');
					let dotColor;
					switch (effectiveState) {
						case 'high':
							dotColor = 'blue';
							break;
						case 'low':
							dotColor = 'orange';
							break;
						case 'normal':
							dotColor = 'green';
							break;
						default:
							dotColor = 'gray';
							break;
					}
					return new Style({
						image: new Icon({
							anchor: [0.5, 0.5],
							src: `data:image/svg+xml;utf8,<svg width="10" height="10" xmlns="http://www.w3.org/2000/svg"><circle cx="5" cy="5" r="5" fill="${dotColor}"/></svg>`
						})
					});
				}
			}
		});

		const geoSource = new VectorSource();
		geoLayer = new VectorLayer({
			source: geoSource,
			style: new Style({
				stroke: new Stroke({
					color: 'blue',
					width: 2
				}),
				fill: new Fill({
					color: 'rgba(0, 0, 255, 0.1)'
				})
			})
		});

		ogcLayer = new VectorLayer({
			source: null,
			visible: false
		});

		map = new Map({
			target: c,
			controls: defaultControls({ rotate: false }).extend([
				new FullScreen({
					className: 'ol-fullscreen',
					label: '⤢',
					labelActive: '×',
					tipLabel: 'Toggle full-screen',
					keys: true
				}),
				new ScaleLine({ bar: false, text: true, }),
				new Attribution({
					collapsible: false
				})
			]),
			layers: [
				new TileLayer({
					source: new OSM()
				}),
				ogcLayer,
				geoLayer,
				stationLayer
			],
			view: new View({
				projection: utmProjection,
				center: defaultCenter,
				zoom: 10,
				enableRotation: false
			})
		});

		const popupElement = document.createElement('div');
		popupElement.className = 'ol-popup';
		popup = new Overlay({
			element: popupElement,
			autoPan: true,
			autoPanAnimation: {
				duration: 250
			}
		});
		map.addOverlay(popup);

		map.on('singleclick', function(evt) {
			const feature = map.forEachFeatureAtPixel(evt.pixel, function(feature) {
				return feature;
			});

			if (feature) {
				const coordinates = feature.getGeometry().getCoordinates();
				popup.setPosition(coordinates);

				const name = feature.get('name');
				const currentMeasuredValue = feature.get('currentMeasuredValue');
				const timestamp = feature.get('timeStamp');
				const description = feature.get('description');
				const stateMnwMhw = feature.get('stateMnwMhw');
				const stateNswHsw = feature.get('stateNswHsw');
				const pegelStatus = feature.get('pegelStatus');
				const hasCharacteristicValues = feature.get('hasCharacteristicValues');

				let detailedPopupContent = `<div class="detailed-popup">
		            <h3>${name}</h3>`;

				if (currentMeasuredValue !== undefined) {
					detailedPopupContent += `
					                <div style="white-space: nowrap;">
					                    <strong>Wasserstand:</strong> 
					                    <span style="color: #2980b9; font-weight: 500;">${currentMeasuredValue !== null ? currentMeasuredValue + 'cm' : 'N/A'}</span>
					                </div>`;
				}

				if (timestamp) {
					const date = new Date(timestamp);
					const day = String(date.getDate()).padStart(2, '0');
					const month = String(date.getMonth() + 1).padStart(2, '0');
					const year = date.getFullYear();
					const hours = String(date.getHours()).padStart(2, '0');
					const minutes = String(date.getMinutes()).padStart(2, '0');
					const formattedTimestamp = `${day}.${month}.${year} ${hours}:${minutes} Uhr`;
					detailedPopupContent += `
					                <div style="white-space: nowrap;">
					                    <strong>Zeitpunkt:</strong> 
					                    <span style="font-style: italic; color: #7f8c8d;">${formattedTimestamp}</span>
					                </div>`;
				}

				if (description !== null) {
					detailedPopupContent += `<div><strong>Beschreibung:</strong> ${description}</div>`;
				}

				if (stateMnwMhw) {
					const statusClass = stateMnwMhw.toLowerCase() === 'normal' ? 'status-normal' : 'status-unknown';
					detailedPopupContent += `
				                <div style="white-space: nowrap;">
				                    <strong>MNW/MHW Status:</strong> 
				                    <span class="${statusClass}">${stateMnwMhw}</span>
				                </div>`;
				}

				if (stateNswHsw) {
					const statusClass = stateNswHsw.toLowerCase() === 'normal' ? 'status-normal' : 'status-unknown';
					detailedPopupContent += `
				                <div style="white-space: nowrap;">
				                    <strong>NSW/HSW Status:</strong> 
				                    <span class="${statusClass}">${stateNswHsw}</span>
				                </div>`;
				}

				if (pegelStatus) {
					const statusClass = pegelStatus.toLowerCase() === 'normal' ? 'status-normal' : 'status-unknown';
					detailedPopupContent += `
				                <div style="white-space: nowrap;">
				                    <strong>Pegel Status:</strong> 
				                    <span class="${statusClass}">${pegelStatus}</span>
				                </div>`;
				}
				if (hasCharacteristicValues) {
					const hasAnyValue = hasCharacteristicValues.MNW || hasCharacteristicValues.MHW ||
						hasCharacteristicValues.HSW || hasCharacteristicValues.HHW;

					if (hasAnyValue) {
						detailedPopupContent += `<div><strong>Charakteristische Werte:</strong></div>`;
					}
					if (hasCharacteristicValues.MNW) {
						detailedPopupContent += `<div>Mittel der Niedrigwasserstände(MNW): ${hasCharacteristicValues.MNW} cm</div>`;
					}
					if (hasCharacteristicValues.MHW) {
						detailedPopupContent += `<div>Mittel der Hochwasserstände(MHW): ${hasCharacteristicValues.MHW} cm</div>`;
					}
					if (hasCharacteristicValues.HSW) {
						detailedPopupContent += `<div>Höchster Schifffahrtswasserstand(HSW): ${hasCharacteristicValues.HSW} cm</div>`;
					}
					if (hasCharacteristicValues.HHW) {
						detailedPopupContent += `<div>Höchster Hochwasserstand(HHW): ${hasCharacteristicValues.HHW} cm</div>`;
					}

				}

				detailedPopupContent += `</div>`;

				popupElement.innerHTML = detailedPopupContent;
				popupElement.classList.add('detailed');
			}
		});

		map.on('pointermove', function(evt) {
			const feature = map.forEachFeatureAtPixel(evt.pixel, function(feature) {
				return feature;
			});
			if (feature) {
				const coordinates = feature.getGeometry().getCoordinates();
				popup.setPosition(coordinates);
				const name = feature.get('name');
				const currentMeasuredValue = feature.get('currentMeasuredValue');
				const timestamp = feature.get('timeStamp');
				const description = feature.get('description');
				const type = feature.get('type');
				const height = feature.get('height');

				let popupContent = `
				        <div style="font-size: 16px; font-weight: bold; color: #333; border-bottom: 1px solid #ddd; padding-bottom: 5px; margin-bottom: 5px;">
				            ${name}
				        </div>`;

				if (type === 'water') {
					if (currentMeasuredValue !== undefined) {
						popupContent += `
						    <div style="font-size: 14px; color: #2c3e50; margin: 5px 0; white-space: nowrap;">
						        <span style="font-weight: 500; display: inline;">Wasserstand:</span> 
						        <span style="color: #2980b9; display: inline; margin-left: 5px;">${currentMeasuredValue !== null ? currentMeasuredValue + 'cm' : 'N/A'}</span>
						    </div>`;
					}
					if (timestamp !== undefined || timestamp !== null) {
						const date = new Date(timestamp);
						const day = String(date.getDate()).padStart(2, '0');
						const month = String(date.getMonth() + 1).padStart(2, '0');
						const year = date.getFullYear();
						const hours = String(date.getHours()).padStart(2, '0');
						const minutes = String(date.getMinutes()).padStart(2, '0');
						const formattedTimestamp = `${day}.${month}.${year} ${hours}:${minutes} Uhr`;
						popupContent += `
						            <div style="font-size: 12px; color: #7f8c8d; font-style: italic;">
						                ${formattedTimestamp}
						            </div>`;
					}

					if (description !== null) {
						popupContent += `
						            <div style="font-size: 13px; color: #555; margin-top: 5px;">
						                ${description}
						            </div>`;
					}
				} else if (type === 'weather') {
					if (height !== null) {
						popupContent += `
												    <div style="font-size: 14px; color: #2c3e50; margin: 5px 0; white-space: nowrap;">
												        <span style="font-weight: 500; display: inline;">Normalhöhennull:</span> 
												        <span style="color: #2980b9; display: inline; margin-left: 5px;">${height + ' m'}</span>
												    </div>`;
					}
				}
				popupElement.innerHTML = popupContent;

			} else {
				popup.setPosition(undefined);
			}
		});
	},

	showStations: function(element, stationsJson, isWetterstation) {
		const stations = JSON.parse(stationsJson);
		const vectorSource = stationLayer.getSource();
		vectorSource.clear();

		if (!stations || stations.length === 0) {
			map.getView().setCenter(defaultCenter);
			map.getView().setZoom(10);
			return;
		}

		const features = stations.map(station => {
			const featureProperties = {
				geometry: new Point(fromLonLat([station.longitude, station.latitude])),
				name: station.name.substring(0, 1) + station.name.substring(1).toLowerCase(),
				type: isWetterstation ? 'weather' : 'water',
				isSelected: station.isSelected || false
			};

			if (!isWetterstation) {
				featureProperties.timeStamp = station.hasWasserstand ? station.hasWasserstand.timeStamp : null;
				featureProperties.currentMeasuredValue = station.hasWasserstand ? station.hasWasserstand.currentMeasuredValue : null;
				featureProperties.stateMnwMhw = station.hasWasserstand ? station.hasWasserstand.stateMnwMhw : null;
				featureProperties.stateNswHsw = station.hasWasserstand ? station.hasWasserstand.stateNswHsw : null;
				featureProperties.pegelStatus = station.pegelStatus;
				featureProperties.description = station.description;
				featureProperties.hasCharacteristicValues = station.hasWasserstand ? station.hasWasserstand.hasCharacteristicValues : null;
			} else {
				featureProperties.height = station.height;
			}

			const feature = new Feature(featureProperties);
			return feature;
		});
		vectorSource.addFeatures(features);
		if (stations.length === 1) {
			const station = stations[0];
			map.getView().setCenter(fromLonLat([station.longitude, station.latitude]));
			map.getView().setZoom(17);
		} else if (stations.length > 1) {
			const extent = vectorSource.getExtent();
			map.getView().fit(extent, { padding: [50, 50, 50, 50], maxZoom: 7 });
		}
	},
	showGeolocation: function(element, geoJsonString) {
		const geoSource = geoLayer.getSource();
		geoSource.clear();

		try {
			const format = new GeoJSON();
			const features = format.readFeatures(geoJsonString, {
				dataProjection: 'EPSG:4326',
				featureProjection: 'EPSG:3857'
			});
			if (features.length === 0) {
				map.getView().setCenter(defaultCenter);
				map.getView().setZoom(6);
				return;
			}
			geoSource.addFeatures(features);

			const extent = geoSource.getExtent();
			if (features.length === 1 && features[0].getGeometry().getType() === 'Polygon') {
				map.getView().fit(extent, { padding: [50, 50, 50, 50], maxZoom: 10 });
			} else {
				map.getView().fit(extent, { padding: [50, 50, 50, 50], maxZoom: 8 });
			}
			map.getView().setZoom(6);
		} catch (error) {
			console.error("Error parsing GeoJSON:", error);
			map.getView().setCenter(defaultCenter);
			map.getView().setZoom(6);
		}
	},
	showOgcLayer: function(element, ogcLayerJson) {
		try {
			const ogcLayerData = JSON.parse(ogcLayerJson);
			console.log('ogcLayerData:', ogcLayerData);

			const { url, label, crs, bboxWestLongitude, bboxEastLongitude, bboxSouthLatitude, bboxNorthLatitude } = ogcLayerData;

			if (!url || !label) {
				console.error("Missing required OGC layer properties (url or label)");
				return;
			}

			let sourceCrs = 'EPSG:4326';
			if (crs) {
				let normalizedCrs = crs;
				if (crs.startsWith('urn:ogc:def:crs:EPSG::')) {
					normalizedCrs = 'EPSG:' + crs.split('::')[1];
				} else if (!crs.startsWith('EPSG:') && /^\d+$/.test(crs)) {
					normalizedCrs = 'EPSG:' + crs;
				}
				const crsList = normalizedCrs.split(';').map(c => c.trim());
				sourceCrs = crsList.find(c => c.startsWith('EPSG:') && olProj.get(c)) || 'EPSG:4326';
				if (sourceCrs !== crsList[0]) {
					console.warn(`CRS ${crs} not fully supported; using ${sourceCrs}`);
				}
			}
			console.log('CRS:', crs, 'Source CRS:', sourceCrs, 'Projection:', olProj.get(sourceCrs));

			const fixedBbox = [
				isFinite(Number(bboxWestLongitude)) ? Number(bboxWestLongitude) : -180,
				isFinite(Number(bboxSouthLatitude)) ? Number(bboxSouthLatitude) : -90,
				isFinite(Number(bboxEastLongitude)) ? Number(bboxEastLongitude) : 180,
				isFinite(Number(bboxNorthLatitude)) ? Number(bboxNorthLatitude) : 90
			];
			console.log('Fixed Bbox:', fixedBbox);

			if (fixedBbox.some(coord => !isFinite(coord))) {
				throw new Error('Bounding box contains invalid coordinates');
			}

			const vectorSource = new VectorSource({
				format: new GML3(),
				crossOrigin: 'anonymous',
				url: `${url}?service=WFS&request=GetFeature&` +
					`typename=${label}&` +
					`srsname=urn:ogc:def:crs:${crs}&` +
					`VERSION=1.1.0&OUTPUTFORMAT=text/xml; subtype=gml/3.1.1&` +
					`bbox=${fixedBbox.join(',')},urn:ogc:def:crs:${crs}`
			});
			console.log('VectorSource URL:', vectorSource.getUrl());

			vectorSource.on('featuresloadend', (event) => {
				const features = event.features;
				console.log('Loaded features:', features ? features.length : 0, features);
			});
			vectorSource.on('featuresloaderror', (event) => {
				console.error('Error loading features from WFS:', event);
			});

			ogcLayer.setSource(vectorSource);
			ogcLayer.setStyle(new Style({
				stroke: new Stroke({
					color: 'rgba(0, 0, 0, 0)',
					width: 0
				}),
				fill: new Fill({
					color: 'rgba(74, 17, 126, 0.5)'
				})
			}));

			ogcLayer.setVisible(true);

			console.log('Layer visibility:', ogcLayer.getVisible());
			console.log('Layer source:', ogcLayer.getSource());

			const transformedExtent = transformExtent(fixedBbox, sourceCrs, 'EPSG:3857');
			console.log('Transformed Extent:', transformedExtent);

			if (!transformedExtent || transformedExtent.some(isNaN)) {
				console.error('Invalid transformed extent, falling back to default view');
				map.getView().setCenter(defaultCenter);
				map.getView().setZoom(10);
			} else {
				map.getView().fit(transformedExtent, { padding: [50, 50, 50, 50], maxZoom: 10 });
				console.log('Map center:', map.getView().getCenter());
				console.log('Map zoom:', map.getView().getZoom());
			}
		} catch (error) {
			console.error("Error displaying OGC layer:", error);
			ogcLayer.setSource(null);
			ogcLayer.setVisible(false);
			map.getView().setCenter(defaultCenter);
			map.getView().setZoom(10);
		}
	},

	clearAll: function(element) {
		if (map) {
			map.setTarget(null);
			map = null;
		}
	}
};
