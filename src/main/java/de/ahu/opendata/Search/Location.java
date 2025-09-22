package de.ahu.opendata.Search;

import java.time.OffsetDateTime;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lokation")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Location extends BaseEntity {

	@Column(name = "station_id")
	private String stationId;
	private double latitude;
	private double longitude;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "provider_id")
	private ServiceProvider provider;

	@Column(name = "start_date")
	private OffsetDateTime startDate;

	@Column(name = "end_date")
	private OffsetDateTime endDate;
}
