package de.ahu.opendata.Search;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, String> {
	List<Location> findByProviderId(String providerId);
}
