package de.ahu.opendata.CsvConfiguration;

import java.util.Map;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "csv_header_mappings")
@Getter
@Setter
@NoArgsConstructor
public class HeaderMapping extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "config_id", nullable = false)
	private CsvConfiguration config;

	@Column(name = "target_header", nullable = false, length = 100)
	private String targetHeader;

	@Column(name = "source_header", length = 100)
	private String sourceHeader;

	@Column(name = "fixed_value", length = 100)
	private String fixedValue;

	public HeaderMapping(String label, String targetHeader, String sourceHeader, String fixedValue) {
		super.setLabel(label);
		this.targetHeader = targetHeader;
		this.sourceHeader = sourceHeader;
		this.fixedValue = fixedValue;
	}

	public String getValue(Map<String, String> sourceFields) {
		if (this.fixedValue != null && !this.fixedValue.isEmpty()) {
			return this.fixedValue;
		}
		if (this.sourceHeader == null || !sourceFields.containsKey(this.sourceHeader)) {
			return "";
		}
		String sourceFieldValue = sourceFields.get(this.sourceHeader);
		return sourceFieldValue.contains(".") ? sourceFieldValue.replace(".", ",") : sourceFieldValue;
	}
}
