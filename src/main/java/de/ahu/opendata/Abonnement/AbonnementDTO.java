package de.ahu.opendata.Abonnement;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AbonnementDTO {

	@NotBlank
	private String label;

	@NotBlank
	private String dateiFormat;

	@NotBlank
	private String parameter;

	@NotBlank
	private String locationId;

	private String fields;
	private LocalDate startDatum;
	private LocalDate endDatum;
}
