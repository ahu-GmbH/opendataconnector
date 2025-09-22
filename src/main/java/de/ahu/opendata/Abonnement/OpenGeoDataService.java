package de.ahu.opendata.Abonnement;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class OpenGeoDataService {
	@Autowired
	private OpenGeoDataRepository openGeoDataRepository;

	public List<OpenGeoData> findAllOpenGeoData() {
		return openGeoDataRepository.findAll();
	}

	public void deleteOpenGeoDataById(String id) {
		OpenGeoData openGeoData = openGeoDataRepository.findById(id)
				.orElseThrow(() -> new RuntimeException(String.format("OpenGeoData nicht gefunden. ID: %s", id)));
		openGeoDataRepository.delete(openGeoData);
	}

	public OpenGeoData findOpenGeoDataById(String id) {
		return openGeoDataRepository.findById(id)
				.orElseThrow(() -> new RuntimeException(String.format("OpenGeoData nicht gefunden. ID: %s", id)));
	}

	@Transactional
	public OpenGeoData createOrUpdateOpenGeoData(OpenGeoData openGeoData) {
		OpenGeoData openGeo;
		if (openGeoData.getId() == null) {
			openGeo = new OpenGeoData();
		} else {
			openGeo = findOpenGeoDataById(openGeoData.getId());
		}

		openGeo.setColumns(openGeoData.getColumns());
		openGeo.setHeaders(openGeoData.getHeaders());
		openGeo.setRelevanteFiles(openGeoData.getRelevanteFiles());
		openGeo.setFormat(openGeoData.getFormat());
		openGeo.setLastUpdated(openGeoData.getLastUpdated());
		openGeo.setLabel(openGeoData.getLabel());
		return openGeoDataRepository.save(openGeoData);
	}

}
