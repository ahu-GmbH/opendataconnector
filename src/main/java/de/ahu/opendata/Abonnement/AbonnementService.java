package de.ahu.opendata.Abonnement;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.ahu.opendata.CsvConfiguration.CsvConfigService;
import de.ahu.opendata.CsvConfiguration.CsvConfiguration;
import de.ahu.opendata.DataUtils.RestDTO;
import de.ahu.opendata.DataUtils.StationDTO.Measurement;
import de.ahu.opendata.Konfiguration.Konfiguration;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Pegeldienst.WasserstandDTO;
import de.ahu.opendata.Pegeldienst.WasserstandService;
import de.ahu.opendata.Search.SearchGeoDataService;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterDatenDTO;
import de.ahu.opendata.Wetterdienst.WetterdienstService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AbonnementService {

	@Autowired
	public AbonnementRepository abonnementRepository;

	@Autowired
	public Konfiguration konfiguration;

	@Autowired
	private SearchGeoDataService searchOpenGeoDatenService;

	@Autowired
	private WetterdienstService wetterdienstService;

	@Autowired
	private CsvConfigService csvConfigService;

	@Autowired
	private WasserstandService wasserstandService;

	public List<Abonnement> findAllAbonnements() {
		List<Abonnement> abos = abonnementRepository.findAll();
		return (!abos.isEmpty()
				? abos.stream().sorted(Comparator.comparing(Abonnement::getLabel)).collect(Collectors.toList())
				: Collections.emptyList());
	}

	public Abonnement findAbonnementById(String id) {
		return abonnementRepository.findById(id)
				.orElseThrow(() -> new RuntimeException(String.format("Projekt nicht gefunden. ID: %s", id)));
	}

	@Transactional
	public Abonnement createOrUpdateAbonnement(Abonnement abonnement) {
		Abonnement targetAbonnement;
		if (abonnement.getId() == null) {
			targetAbonnement = new Abonnement();
		} else {
			targetAbonnement = findAbonnementById(abonnement.getId());
		}

		targetAbonnement.setLabel(abonnement.getLabel());
		targetAbonnement.setDescription(abonnement.getDescription());
		targetAbonnement.setParameter(abonnement.getParameter());
		targetAbonnement.setUrl(abonnement.getUrl());
		targetAbonnement.setDateiFormat(abonnement.getDateiFormat());
		targetAbonnement.setStartDatum(abonnement.getStartDatum());
		targetAbonnement.setEndDatum(abonnement.getEndDatum());
		targetAbonnement.setLocationId(abonnement.getLocationId());
		targetAbonnement.setSubscriptionUrl(abonnement.getSubscriptionUrl());

		return abonnementRepository.save(targetAbonnement);
	}

	@Transactional
	public Abonnement createAbonnementDTO(AbonnementDTO aboDto) {
		Abonnement abonnement = new Abonnement();
		abonnement.setLabel(aboDto.getLabel());
		abonnement.setParameter(aboDto.getParameter());
		abonnement.setLocationId(aboDto.getLocationId());
		abonnement.setDateiFormat(aboDto.getDateiFormat());
		abonnement.setStartDatum(aboDto.getStartDatum() != null ? aboDto.getStartDatum() : LocalDate.now());
		abonnement.setEndDatum(aboDto.getEndDatum() != null ? aboDto.getEndDatum() : LocalDate.now().plusDays(1));
		if (aboDto.getParameter().contains("hourly/large/")) {
			abonnement.setUrl(konfiguration.getWetterdienstBaseUrlMosmix());
		} else {
			abonnement.setUrl(konfiguration.getWetterdienstBaseUrlObservation());
		}
		abonnement.setSubscriptionUrl("/api/subscription/data?parameter=" + aboDto.getParameter() + "&locationId="
				+ aboDto.getLocationId() + "&format=" + aboDto.getDateiFormat() + "&fields=" + aboDto.getFields());

		return abonnementRepository.save(abonnement);
	}

	public Abonnement findAbonnementByParameterAndLocationAndDateiFormat(String parameter, String locationId,
			String format) {
		return abonnementRepository.findByParameterAndLocationIdAndDateiFormat(parameter, locationId, format)
				.orElse(null);
	}

	public void deleteAbonnementById(String id) {
		Abonnement abonnement = abonnementRepository.findById(id)
				.orElseThrow(() -> new RuntimeException(String.format("Abonnement nicht gefunden. ID: %s", id)));
		abonnementRepository.delete(abonnement);
	}

	public <T extends RestDTO> Abonnement checkAlreadySubscribed(List<WetterDatenDTO> dto) {
		List<Abonnement> abos = findAllAbonnements();

		Abonnement abonnement = abos.stream().filter(abo -> abo.getParameter() != null
				&& abo.getParameter().equals(dto.getFirst().getParameter())
				&& abo.getLocationId().equals(dto.getFirst().getId())
				&& abo.getStartDatum().toString().equals(Utils.parseGermanDate(dto.getFirst().getDate()).toString())
				&& abo.getEndDatum().toString().equals(Utils.parseGermanDate(dto.getLast().getDate()).toString()))
				.findFirst().orElse(new Abonnement());

		if (abonnement.getId() != null) {
			return abonnement;
		}
		return null;
	}

	public void updateStartDayOfAbonnement() {
		LocalDate today = LocalDate.now();

		for (Abonnement abo : findAllAbonnements()) {

			if (abo.getLastUpdated() == null || !abo.getLastUpdated().isEqual(today)) {

				if (abo.getUrl().contains("mosmix")) {
					if (abo.getStartDatum() == null || abo.getStartDatum().isBefore(today)) {
						abo.setStartDatum(today);
					}
					abo.setEndDatum(abo.getEndDatum().plusDays(1));
				}

				if (abo.getUrl().contains("observation")) {
					if (abo.getStartDatum() == null || abo.getStartDatum().isBefore(today)) {
						abo.setStartDatum(abo.getStartDatum().plusDays(1));
					}
					abo.setEndDatum(abo.getEndDatum().plusDays(1));
				}

				abo.setLastUpdated(today);
				abonnementRepository.save(abo);
			}
		}
	}

	public Optional<ResponseEntity<String>> fetchInrementalData(String id) {
		Abonnement abonnement = findAbonnementById(id);
		if (abonnement == null) {
			return Optional.empty();
		}

		String url = abonnement.getUrl();
		ByteArrayOutputStream baos = null;

		if (url.contains("oberflaechengewaesser")) {
			List<FileDTO> extractedFiles = searchOpenGeoDatenService.incrementalUpdateDate(url);
			FileDTO relevantFile = extractedFiles.stream()
					.filter(file -> file.getTitle().contains(abonnement.getParameter())).findFirst().orElse(null);
			if (relevantFile != null) {
				CsvConfiguration csvConfig = csvConfigService
						.getCurrentCsvConfiguration(Utils.extractFilenameWithoutExtension(relevantFile.getTitle()));
				String newFileContent = getCsvEntriesAsString(relevantFile.getFilePath(), abonnement, true);
				baos = csvConfigService.reformatFile(newFileContent, csvConfig.getFilePattern());
			}
		}
		if (baos != null) {
			if (abonnement.getDateiFormat().equals("ahuManagerformat")) {
				return Optional.of(ResponseEntity.ok().contentType(Utils.getMediaType(".csv")).body(baos.toString()));
			}
		}
		return Optional.of(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Kein vorhandene Daten für den Parameter: "
				+ abonnement.getParameter() + " und Standort: " + abonnement.getLocationId() + " gefunden."));
	}

	public Optional<ResponseEntity<String>> fetchAbonnementData(String id) {
		Abonnement abonnement = findAbonnementById(id);
		if (abonnement == null) {
			return Optional.empty();
		}

		String url = abonnement.getUrl();
		String formattedData = null;
		ByteArrayOutputStream baos = null;

		if (url.contains("dwd")) {

			String requestUrl = url + abonnement.getParameter() + "&station=" + abonnement.getLocationId() + "&date="
					+ abonnement.getStartDatum() + "/" + abonnement.getEndDatum();

			List<WetterDatenDTO> wetterDaten = wetterdienstService.fetchWetterDaten(requestUrl);
			checkIsEmptyList(wetterDaten);
			formattedData = wetterdienstService.formatWetterDaten(wetterDaten, abonnement.getDateiFormat());

		} else if (url.contains("grundwasser")) {
			List<FileDTO> extractedFile = new ArrayList<>();
			FileDTO relevantFile = searchOpenGeoDatenService.processSingleCsvFile(abonnement.getParameter(),
					abonnement.getUrl());
			if (relevantFile != null) {
				extractedFile.add(relevantFile);
			}
			List<Measurement> values = searchOpenGeoDatenService.extractGrundWasserStand(extractedFile,
					abonnement.getLocationId(), abonnement.getParameter());
			checkIsEmptyList(values);

			CsvConfiguration csvConfig = csvConfigService
					.getCurrentCsvConfiguration(Utils.extractFilenameWithoutExtension(relevantFile.getTitle()));

			String newFileContent = getCsvEntriesAsString(relevantFile.getFilePath(), abonnement, false);
			baos = csvConfigService.reformatFile(newFileContent, csvConfig.getFilePattern());
		} else if (url.contains("oberflaechengewaesser")) {
			List<FileDTO> extractedFiles = searchOpenGeoDatenService
					.extractContentZipFiles(Set.of(new FileDTO("", url)));
			FileDTO relevantFile = extractedFiles.stream()
					.filter(file -> file.getTitle().contains(abonnement.getParameter())).findFirst().orElse(null);
			CsvConfiguration csvConfig = csvConfigService
					.getCurrentCsvConfiguration(Utils.extractFilenameWithoutExtension(relevantFile.getTitle()));

			String newFileContent = getCsvEntriesAsString(relevantFile.getFilePath(), abonnement, false);
			baos = csvConfigService.reformatFile(newFileContent, csvConfig.getFilePattern());

		} else if (url.contains("measurements.csv")) {
			List<WasserstandDTO> values = wasserstandService.fetchVorhersageData(abonnement.getLocationId());
			checkIsEmptyList(values);
			formattedData = wetterdienstService.formatWetterDaten(values, abonnement.getDateiFormat());

		} else if (url.contains("measurements.json")) {
			List<WasserstandDTO> values = wasserstandService.fetchHistoricalData(abonnement.getLocationId());
			checkIsEmptyList(values);
			formattedData = wetterdienstService.formatWetterDaten(values, abonnement.getDateiFormat());
		}

		if (formattedData != null) {
			return Optional.of(ResponseEntity.ok().contentType(Utils.getMediaType(abonnement.getDateiFormat()))
					.body(formattedData));
		}

		if (baos != null) {
			if (abonnement.getDateiFormat().equals("ahuManagerformat")) {
				return Optional.of(ResponseEntity.ok().contentType(Utils.getMediaType(".csv")).body(baos.toString()));
			}
		}
		return Optional.of(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Kein Abonnement für den Parameter: "
				+ abonnement.getParameter() + " und Standort: " + abonnement.getLocationId() + " gefunden."));
	}

	private <T extends RestDTO> Optional<ResponseEntity<String>> checkIsEmptyList(List<T> values) {
		return (values.isEmpty()) ? Optional.of(ResponseEntity.notFound().build()) : null;
	}

	public String getCsvEntriesAsString(String fileContentPath, Abonnement abonnement, boolean isNeedUpdate) {
		StringBuilder result = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(fileContentPath))) {
			String headerLine = reader.readLine();
			if (headerLine == null || headerLine.isEmpty()) {
				return "";
			}

			String[] headers = headerLine.split(";");
			if (headers.length == 0) {
				return "";
			}
			result.append(headerLine).append("\n");

			String line = "";
			if (isNeedUpdate) {
				while ((line = reader.readLine()) != null) {
					String[] row = line.split(";");
					if (row.length > 0 && row[0].trim().equals(abonnement.getLocationId())
							&& !Arrays.stream(row).skip(1).allMatch(String::isBlank)) {
						LocalDate rowDate = LocalDate.parse(row[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
						if (rowDate.isAfter(abonnement.getEndDatum())) {
							result.append(line).append("\n");
						}
					}
				}
			} else {
				while ((line = reader.readLine()) != null) {
					String[] row = line.split(";");

					if (fileContentPath.contains("gw-wasserstand")) {
						if (row.length > 0 && row[1].trim().equals(abonnement.getLocationId())
								&& !Arrays.stream(row).skip(1).allMatch(String::isBlank)) {
							LocalDate rowDate = LocalDate.parse(row[3], DateTimeFormatter.ISO_LOCAL_DATE);
							if (rowDate.isBefore(abonnement.getEndDatum())
									|| rowDate.isEqual(abonnement.getEndDatum())) {
								result.append(line).append("\n");
							}
						}
					} else {
						if (row.length > 0 && row[0].trim().equals(abonnement.getLocationId())
								&& !Arrays.stream(row).skip(1).allMatch(String::isBlank)) {
							LocalDate rowDate = LocalDate.parse(row[1], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
							if (rowDate.isBefore(abonnement.getEndDatum())
									|| rowDate.isEqual(abonnement.getEndDatum())) {
								result.append(line).append("\n");
							}
						}
					}

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result.toString();
	}
}
