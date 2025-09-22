package de.ahu.opendata.Abonnement;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, String> {
	Optional<Abonnement> findByParameterAndLocationIdAndDateiFormat(String parameter, String locationId, String format);
}
