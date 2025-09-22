package de.ahu.opendata.Abonnement;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "open_geodata")
@Getter
@Setter
public class OpenGeoData extends BaseEntity {

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, List<String>> columns;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> headers;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "relevante_files", columnDefinition = "jsonb")
	private List<String> relevanteFiles;

	@Column(name = "format")
	private String format;

}
