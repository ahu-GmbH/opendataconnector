package de.ahu.opendata.Abonnement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenGeoDataRepository extends JpaRepository<OpenGeoData, String> {

}
