package de.ahu.opendata.Search;

import java.util.ArrayList;
import java.util.List;

import de.ahu.opendata.DataUtils.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "provider")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceProvider extends BaseEntity {

	private Boolean historisch;
	private Boolean vorhersage;

	@OneToMany(mappedBy = "provider", cascade = CascadeType.ALL)
	private List<Location> stations = new ArrayList<>();

}
