package de.ahu.opendata.Abonnement;

import java.time.LocalDate;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "abonnement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Abonnement extends BaseEntity {

	@Column(name = "file_format")
	private String dateiFormat;

	private String parameter;

	@Column(name = "location_id")
	private String locationId;

	@Column(name = "start_datum")
	private LocalDate startDatum;

	@Column(name = "end_datum")
	private LocalDate endDatum;

	@Column(name = "sub_url")
	private String subscriptionUrl;

}
