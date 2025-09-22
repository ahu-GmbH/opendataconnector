package de.ahu.opendata.CsvConfiguration;

import java.util.ArrayList;
import java.util.List;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "csv_configurations")
@Getter
@Setter
public class CsvConfiguration extends BaseEntity {
	@Column(name = "file_pattern", nullable = false, unique = true, length = 100)
	private String filePattern;

	@Column(name = "role_type", nullable = true, length = 100)
	private String roleType;

	@OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<HeaderMapping> headerMappings = new ArrayList<>();

	public void addHeaderMapping(HeaderMapping mapping) {
		mapping.setConfig(this);
		headerMappings.add(mapping);
	}

}
