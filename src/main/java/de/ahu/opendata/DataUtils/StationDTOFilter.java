package de.ahu.opendata.DataUtils;

import com.vaadin.flow.component.grid.dataview.GridListDataView;

public class StationDTOFilter {

	private final GridListDataView<StationDTO> dataView;

	private String stationName;

	private String provider;

	public StationDTOFilter(GridListDataView<StationDTO> dataView) {
		this.dataView = dataView;
		this.dataView.addFilter(this::test);
	}

	public void setStationName(String name) {
		this.stationName = name;
		this.dataView.refreshAll();
	}

	public void setProvider(String provider) {
		this.provider = provider;
		this.dataView.refreshAll();
	}

	public boolean test(StationDTO result) {
		boolean matchesTitle = matches(result.getName(), stationName);
		boolean matchesProvider = matches(result.getProvider(), provider);
		return matchesTitle && matchesProvider;
	}

	private boolean matches(String value, String searchTerm) {
		String normalizedValue = normalize(value);
		String normalizedSearchTerm = normalize(searchTerm);

		if (normalizedSearchTerm == null || normalizedSearchTerm.isEmpty()) {
			return true;
		}

		if (normalizedValue == null || normalizedValue.isEmpty()) {
			return false;
		}
		String[] searchTerms = normalizedSearchTerm.split("\\s+");

		for (String term : searchTerms) {
			if (!normalizedValue.toLowerCase().contains(term.toLowerCase())) {
				return false;
			}
		}

		return true;
	}

	private String normalize(String input) {
		if (input == null) {
			return null;
		}
		return input.trim().replaceAll("\\s+", " ");
	}

}
