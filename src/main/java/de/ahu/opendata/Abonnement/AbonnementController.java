package de.ahu.opendata.Abonnement;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.ahu.opendata.OpenDataNrw.SearchOpenGeoDatenService;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterDatenDTO;
import de.ahu.opendata.Wetterdienst.WetterdienstService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/subscription")
@Slf4j
public class AbonnementController {

	@Autowired
	private AbonnementService abonnementService;

	@Autowired
	private SearchOpenGeoDatenService searchOpenGeoDatenService;

	@Autowired
	private WetterdienstService wetterdienstService;

	@GetMapping(value = "/data")
	public ResponseEntity<String> fetchAboData(@RequestParam @NotBlank String id) {
		return abonnementService.fetchAbonnementData(id).orElseGet(
				() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Kein Abonnement für die " + id + " gefunden."));
	}

	@GetMapping(value = "/incremental_data")
	public ResponseEntity<String> getIncrementalData(@RequestParam @NotBlank String id) {
		return abonnementService.fetchInrementalData(id).orElseGet(
				() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Kein Abonnement für die " + id + " gefunden."));
	}

	@PostMapping(value = "/data")
	public ResponseEntity<String> createSubscriptionData(@RequestBody @Valid AbonnementDTO aboDto) {
		Abonnement abonnement = abonnementService.findAbonnementByParameterAndLocationAndDateiFormat(
				aboDto.getParameter(), aboDto.getLocationId(), aboDto.getDateiFormat());
		if (abonnement != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Ein Abonnement für den Parameter: "
					+ aboDto.getParameter() + " und Standort: " + aboDto.getLocationId() + " existiert bereits.");
		} else {
			abonnement = abonnementService.createAbonnementDTO(aboDto);
		}

		if (abonnement.getUrl().contains("dwd")) {
			var startDatum = aboDto.getStartDatum() != null ? aboDto.getStartDatum() : abonnement.getStartDatum();
			var endDatum = aboDto.getEndDatum() != null ? aboDto.getEndDatum() : abonnement.getEndDatum();

			String url = abonnement.getUrl() + abonnement.getParameter() + "&station=" + abonnement.getLocationId()
					+ "&date=" + startDatum + "/" + endDatum;

			List<WetterDatenDTO> wetterDaten = wetterdienstService.fetchWetterDaten(url);

			if (wetterDaten.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			String formattedData = wetterdienstService.formatWetterDaten(wetterDaten, abonnement.getDateiFormat());

			return ResponseEntity.ok().contentType(Utils.getMediaType(abonnement.getDateiFormat())).body(formattedData);
		}

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Kein Abonnement für den Parameter: "
				+ aboDto.getParameter() + " und Standort: " + aboDto.getLocationId() + " gefunden.");
	}

	@PostMapping(value = "/values", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getOpenHygonContent(@RequestBody RequestPayload payload) {
		if (payload.getRelevanteFiles() == null || payload.getRelevanteFiles().isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("Es wurden keine Dateien ausgewählt. Bitte wählen Sie mindestens eine Datei aus.");
		}
		ByteArrayOutputStream baos = searchOpenGeoDatenService.mergeSelectedColumns(payload.getHeaders(),
				payload.getColumns(), new HashSet<>(payload.getRelevanteFiles()));

		if (baos != null && baos.size() > 0) {
			String csvContent = baos.toString(StandardCharsets.UTF_8);
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(csvContent);
		} else {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Fehler beim Verarbeiten der Daten.");
		}
	}

}
