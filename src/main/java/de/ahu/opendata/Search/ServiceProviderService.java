package de.ahu.opendata.Search;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import jakarta.transaction.Transactional;

@Service
public class ServiceProviderService {

	@Autowired
	private ProviderRepository serviceProviderRepository;

	public List<ServiceProvider> getAllServiceProviders() {
		return serviceProviderRepository.findAll();
	}

	public ServiceProvider getServiceProviderById(String id) {
		return serviceProviderRepository.findById(id).orElse(null);
	}

	@Transactional
	public ServiceProvider createOrUpdateServiceProvider(ServiceProvider serviceProvider) {
		ServiceProvider currentServiceProvider;
		if (serviceProvider.getId() == null) {
			currentServiceProvider = new ServiceProvider();
		} else {
			currentServiceProvider = serviceProviderRepository.findById(serviceProvider.getId()).orElseThrow(
					() -> new IllegalArgumentException("No service provider found for id: " + serviceProvider.getId()));
		}
		currentServiceProvider.setLabel(serviceProvider.getLabel());
		currentServiceProvider.setHistorisch(serviceProvider.getHistorisch());
		currentServiceProvider.setVorhersage(serviceProvider.getVorhersage());

		return serviceProviderRepository.save(currentServiceProvider);
	}

	public ServiceProvider findServiceProvider(WetterStationDTO station) {
		if (Boolean.TRUE.equals(station.getIsHistorical()) && !Boolean.TRUE.equals(station.getIsForecast())) {
			return findHistoricalProvider().orElseThrow(
					() -> new IllegalStateException("No historical provider found for station: " + station.getName()));
		} else if (Boolean.TRUE.equals(station.getIsForecast()) && !Boolean.TRUE.equals(station.getIsHistorical())) {
			return findForecastProvider().orElseThrow(
					() -> new IllegalStateException("No forecast provider found for station: " + station.getName()));
		} else {
			return findDefaultProvider().orElseThrow(
					() -> new IllegalStateException("No default provider found for station: " + station.getName()));
		}
	}

	private Optional<ServiceProvider> findHistoricalProvider() {
		if (getAllServiceProviders().isEmpty()) {
			throw new IllegalStateException("No service providers available.");
		}
		return getAllServiceProviders().stream().filter(provider -> Boolean.TRUE.equals(provider.getHistorisch()))
				.findFirst();
	}

	private Optional<ServiceProvider> findForecastProvider() {
		if (getAllServiceProviders().isEmpty()) {
			throw new IllegalStateException("No service providers available.");
		}
		return getAllServiceProviders().stream().filter(provider -> Boolean.TRUE.equals(provider.getVorhersage()))
				.findFirst();
	}

	private Optional<ServiceProvider> findDefaultProvider() {
		if (getAllServiceProviders().isEmpty()) {
			throw new IllegalStateException("No service providers available.");
		}
		return getAllServiceProviders().stream().filter(provider -> !Boolean.TRUE.equals(provider.getHistorisch())
				&& !Boolean.TRUE.equals(provider.getVorhersage())).findFirst();
	}
}
