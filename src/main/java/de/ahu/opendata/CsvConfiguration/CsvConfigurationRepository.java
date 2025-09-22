package de.ahu.opendata.CsvConfiguration;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvConfigurationRepository extends JpaRepository<CsvConfiguration, String> {
	@EntityGraph(attributePaths = "headerMappings")
	Optional<CsvConfiguration> findByFilePattern(String filePattern);
}
