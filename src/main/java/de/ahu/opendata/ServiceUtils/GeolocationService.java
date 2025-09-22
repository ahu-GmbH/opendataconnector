package de.ahu.opendata.ServiceUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.geojson.GeoJsonObject;
import org.geojson.GeometryCollection;
import org.geojson.LngLatAlt;
import org.geojson.MultiPolygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeolocationService {

	private Geometry germanGeometry;

	public Geometry germanyGeometry() {
		return germanGeometry;
	}

	public void initializeGermanGeometry() {
		try (InputStream inputStream = this.getClass().getResourceAsStream("/geojson_germany.txt")) {
			String geoJsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

			ObjectMapper objectMapper = new ObjectMapper();

			try {
				GeoJsonObject geoJsonObject = objectMapper.readValue(geoJsonString, GeoJsonObject.class);
				if (geoJsonObject instanceof GeometryCollection) {
					GeometryCollection collection = (GeometryCollection) geoJsonObject;
					List<GeoJsonObject> geometries = collection.getGeometries();
					if (!geometries.isEmpty() && geometries.get(0) instanceof MultiPolygon) {
						MultiPolygon multiPolygon = (MultiPolygon) geometries.get(0);
						germanGeometry = convertMultiPolygonToJTS(multiPolygon);
					}
				}
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				throw new RuntimeException("Failed to parse Germany GeoJSON: " + e.getMessage());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Geometry convertMultiPolygonToJTS(MultiPolygon multiPolygon) {
		GeometryFactory factory = new GeometryFactory();
		List<Polygon> jtsPolygons = multiPolygon.getCoordinates().stream().map(polygonCoords -> {
			List<LngLatAlt> outerRing = polygonCoords.get(0);
			Coordinate[] coordinates = outerRing.stream()
					.map(coord -> new Coordinate(coord.getLongitude(), coord.getLatitude())).toArray(Coordinate[]::new);

			if (coordinates.length > 0 && !coordinates[0].equals(coordinates[coordinates.length - 1])) {
				Coordinate[] closedCoords = new Coordinate[coordinates.length + 1];
				System.arraycopy(coordinates, 0, closedCoords, 0, coordinates.length);
				closedCoords[coordinates.length] = coordinates[0];
				coordinates = closedCoords;
			}
			return factory.createPolygon(coordinates);
		}).collect(Collectors.toList());

		return factory.createMultiPolygon(jtsPolygons.toArray(new Polygon[0]));

	}

}
