package de.ahu.opendata.CsvConfiguration;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.ahu.opendata.Utils.Utils;
import jakarta.transaction.Transactional;

@Service
public class CsvConfigService {

	@Autowired
	private CsvConfigurationRepository configRepository;

	@Autowired
	private HeaderMappingRepository headerMappingRepository;

	@Transactional
	public List<CsvConfiguration> fetchAllCsvConfigurations() {
		return configRepository.findAll();
	}

	@Transactional
	public List<HeaderMapping> fetchHeaderMappingByConfigId(String id) {
		List<HeaderMapping> headerMapping = headerMappingRepository.findAll();
		return headerMapping.stream().filter(mapping -> mapping.getConfig().getId().equals(id))
				.collect(Collectors.toList());
	}

	public CsvConfiguration getCurrentCsvConfiguration(String currentFileName) {
		List<CsvConfiguration> csvConfigurations = fetchAllCsvConfigurations();
		CsvConfiguration csvConfig = csvConfigurations.stream()
				.filter(csvCon -> currentFileName.equals(csvCon.getFilePattern())).findFirst().orElse(null);
		return csvConfig;
	}

	@Transactional
	public CsvConfiguration AddOrUpdateCsvConfiguration(CsvConfiguration csvConfiguration) {
		CsvConfiguration currentConfig;
		if (csvConfiguration.getId() == null) {
			currentConfig = new CsvConfiguration();
		} else {
			currentConfig = configRepository.findById(csvConfiguration.getId()).orElseThrow(
					() -> new IllegalArgumentException("No configuration found for id: " + csvConfiguration.getId()));
		}
		currentConfig.setLabel(csvConfiguration.getLabel());
		currentConfig.setFilePattern(csvConfiguration.getFilePattern());
		currentConfig.setRoleType(csvConfiguration.getRoleType());
		CsvConfiguration savedConfig = configRepository.save(currentConfig);

		List<HeaderMapping> currentMappings = savedConfig.getHeaderMappings();
		currentMappings.clear();
		currentMappings.addAll(csvConfiguration.getHeaderMappings());

		for (HeaderMapping mapping : savedConfig.getHeaderMappings()) {
			mapping.setConfig(savedConfig);
		}

		return configRepository.save(savedConfig);
	}

	public ByteArrayOutputStream reformatFile(String inputFileContent, String filePattern) {
		CsvConfiguration config = configRepository.findByFilePattern(filePattern)
				.orElseThrow(() -> new IllegalArgumentException("No configuration found for key: " + filePattern));

		List<String> lines = Utils.parseCsvContent(inputFileContent);
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("Empty input file");
		}

		String[] originalHeaders = lines.get(0).split(";");
		List<String> reformattedLines = new ArrayList<>();

		String headerLine = config.getHeaderMappings().stream().map(HeaderMapping::getTargetHeader)
				.collect(Collectors.joining(";"));
		reformattedLines.add(headerLine);

		if (!config.getHeaderMappings().isEmpty()) {
			StringBuilder secondRow = new StringBuilder();
			boolean first = true;
			for (HeaderMapping mapping : config.getHeaderMappings()) {
				if (!first) {
					secondRow.append(";");
				}
				first = false;

				if ("IGNORE".equalsIgnoreCase(mapping.getTargetHeader())) {
					secondRow.append("1");
				} else if (mapping.getSourceHeader() != null) {
					secondRow.append(mapping.getSourceHeader());
				} else {
					secondRow.append("");
				}
			}
			reformattedLines.add(secondRow.toString());
		}

		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			String[] fields = line.split(";");
			Map<String, String> sourceFields = new HashMap<>();
			for (int j = 0; j < Math.min(originalHeaders.length, fields.length); j++) {
				sourceFields.put(originalHeaders[j], fields[j].trim());
			}

			StringBuilder reformattedLine = new StringBuilder();
			reformattedLine.append(";");

			boolean first = true;
			for (HeaderMapping mapping : config.getHeaderMappings()) {
				if ("IGNORE".equalsIgnoreCase(mapping.getTargetHeader())) {
					continue;
				}
				if (!first) {
					reformattedLine.append(";");
				}
				first = false;

				String targetHeader = mapping.getTargetHeader();
				String value;

				if ("ROLLENTYP".equalsIgnoreCase(targetHeader)) {
					value = mapping.getFixedValue();
				} else {
					value = mapping.getValue(sourceFields);
					if (value == null) {
						value = "";
					}
				}

				reformattedLine.append(value);
			}

			reformattedLines.add(reformattedLine.toString());
		}
		return Utils.writeToByteArray(reformattedLines);
	}

	public void deleteHeaderMapping(HeaderMapping headerMapping) {
		HeaderMapping existingMapping = headerMappingRepository.findById(headerMapping.getId())
				.orElseThrow(() -> new IllegalArgumentException("No mapping found for id: " + headerMapping.getId()));
		headerMappingRepository.delete(existingMapping);
	}

}
