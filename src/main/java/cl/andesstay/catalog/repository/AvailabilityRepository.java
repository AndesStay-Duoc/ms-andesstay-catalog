package cl.andesstay.catalog.repository;

import cl.andesstay.catalog.domain.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    Optional<Availability> findByUnitIdAndFecha(Long unitId, LocalDate fecha);

    /** Filas existentes del rango [desde, hasta), ordenadas por fecha. */
    List<Availability> findByUnitIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFecha(
            Long unitId, LocalDate desde, LocalDate hasta);
}
