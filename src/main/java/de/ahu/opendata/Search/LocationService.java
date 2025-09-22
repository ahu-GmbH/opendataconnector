package de.ahu.opendata.Search;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class LocationService {

	@Autowired
	private LocationRepository locationRepository;

	public Location getLocationById(String id) {
		return locationRepository.findById(id).orElse(null);
	}

	public List<Location> getAllLocations() {
		return locationRepository.findAll();
	}

	public List<Location> getLocationsByProviderId(String providerId) {
		return locationRepository.findByProviderId(providerId);
	}

	@Transactional
	public List<Location> saveLocations(List<Location> locations) {
		return locationRepository.saveAll(locations);
	}

}
