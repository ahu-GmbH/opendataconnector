package de.ahu.opendata.DataUtils;

import java.time.LocalDate;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public class BaseEntity implements Comparable<BaseEntity> {

	@Id
	@GeneratedValue(generator = "generateIfNotAssigned")
	@GenericGenerator(name = "generateIfNotAssigned", strategy = "org.hibernate.id.UUIDHexGenerator")
	@Column(length = 32)
	@Access(jakarta.persistence.AccessType.PROPERTY)
	private String id;

	@Length(min = 1)
	@NotNull
	private String label;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "base_url")
	private String url;

	@Column(name = "last_updated")
	private LocalDate lastUpdated;

	public int compareTo(BaseEntity o) {
		return (id != null) ? id.compareTo(o.getId()) : hashCode() - o.hashCode();
	}

	@Override
	public int hashCode() {
		return (id != null) ? id.hashCode() : 1;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof BaseEntity)) {
			return false;
		}
		BaseEntity be = (BaseEntity) obj;
		return (id != null) ? id.equals(be.getId()) : this == be;
	}

}
