package de.ahu.opendata.Konfiguration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Configuration
@Getter
public class Konfiguration {

	@Value("${wetterdienst.url}")
	String wetterdienstBaseUrl;

	@Value("${wetterdienst-mosmix.url}")
	String wetterdienstBaseUrlMosmix;

	@Value("${wetterdienst-observation.url}")
	String wetterdienstBaseUrlObservation;

	@Value("${pegeldienst.url}")
	String pegeldienstBaseUrl;

	@Value("${opengeodata.url}")
	String openGeoDataBaseUrl;

	@Value("${govdata.url}")
	String govdataBaseUrl;

	@Value("${geodatenkatalog.url}")
	String geodatenKatalogBaseUrl;

	@Value("${proxy.host}")
	String proxyHost;

	@Value("${proxy.port}")
	Integer proxyPort;

	@Value("${opengeodata-ow.url}")
	String owStartUrl;

	@Value("${opengeodata-gw.url}")
	String gwStartUrl;
}
