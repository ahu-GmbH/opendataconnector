package de.ahu.opendata.Konfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.ahu.opendata.CsvConfiguration.CsvConfiguration;
import de.ahu.opendata.CsvConfiguration.CsvConfigurationRepository;
import de.ahu.opendata.CsvConfiguration.HeaderMapping;
import de.ahu.opendata.Search.ProviderRepository;
import de.ahu.opendata.Search.ServiceProvider;
import jakarta.annotation.PostConstruct;

@Service
public class DatabaseInitializer {

	@Autowired
	private CsvConfigurationRepository configRepository;

	@Autowired
	private ProviderRepository providerRepository;

	@PostConstruct
	public void init() {
		if (configRepository.count() == 0) {
			initPegelStation();
			initNiederStation();
		}

		if (providerRepository.count() == 0) {
			initServiceProvider();
		}

	}

	private void initServiceProvider() {
		ServiceProvider wetterdienstHist = new ServiceProvider();
		wetterdienstHist.setLabel("Wetterdienst");
		wetterdienstHist.setHistorisch(true);
		wetterdienstHist.setVorhersage(false);
		providerRepository.save(wetterdienstHist);

		ServiceProvider wetterdienstForecast = new ServiceProvider();
		wetterdienstForecast.setLabel("Wetterdienst");
		wetterdienstForecast.setHistorisch(false);
		wetterdienstForecast.setVorhersage(true);
		providerRepository.save(wetterdienstForecast);

		ServiceProvider openGeo = new ServiceProvider();
		openGeo.setLabel("OpenGeoDataNrw");
		openGeo.setHistorisch(false);
		openGeo.setVorhersage(false);
		providerRepository.save(openGeo);

	}

	private void initNiederStation() {
		CsvConfiguration config = new CsvConfiguration();
		config.setLabel("Niederschlag Stationen");
		config.setFilePattern("nieder_stationen");
		config.setRoleType("MeteorologischeMessstelle");

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "IGNORE", null, "1"));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "LATITUDE", "station_latitude", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "LONGITUDE", "station_longitude", null));
		config.addHeaderMapping(
				new HeaderMapping(config.getFilePattern(), "ALTERNATIVBEZEICHNUNG1", "station_name", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "BEZEICHNUNG", "station_no", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "GEBIETSKENNZAHL", "catchment_no", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "GEBIETSNAME", "catchment_name", null));

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###1", "LANUV_Info_1", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###2", "LANUV_Info_2", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###3", "LANUV_Info_3", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###4", "LANUV_MW", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###5", "LANUV_HN7W", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###6", "LANUV_MN7W", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###7", "LANUV_N7W", null));
		configRepository.save(config);
	}

	private void initPegelStation() {
		CsvConfiguration config = new CsvConfiguration();
		config.setLabel("Pegel Stationen");
		config.setFilePattern("pegel_stationen");
		config.setRoleType("Gewaesser");

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "IGNORE", null, "1"));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "LATITUDE", "station_latitude", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "LONGITUDE", "station_longitude", null));
		config.addHeaderMapping(
				new HeaderMapping(config.getFilePattern(), "ALTERNATIVBEZEICHNUNG1", "station_name", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "BEZEICHNUNG", "station_no", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "GEBIETSKENNZAHL", "catchment_no", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "GEBIETSNAME", "catchment_name", null));

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###1", "LANUV_Info_1", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###2", "LANUV_Info_2", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###3", "LANUV_Info_3", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "X_MNW", "LANUV_MNW", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "X_MW", "LANUV_MW", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "X_MHW", "LANUV_MHW", null));

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###4", "station_carteasting", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###5", "station_cartnorthing", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###6", "CATCHMENT_SIZE", null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "###7", "DIST_TO_CONFL", null));

		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "GEWAESSERKENNZAHL", null, null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "STATIONIERUNG_M", null, null));
		config.addHeaderMapping(new HeaderMapping(config.getFilePattern(), "ROLLENTYP", null, "Gewaesser"));

		configRepository.save(config);
	}
}
