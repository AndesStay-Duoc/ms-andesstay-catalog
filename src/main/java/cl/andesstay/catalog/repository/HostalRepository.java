package cl.andesstay.catalog.repository;

import cl.andesstay.catalog.domain.Hostal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HostalRepository extends JpaRepository<Hostal, Long> {

    List<Hostal> findByActivoTrueOrderByNombre();

    Optional<Hostal> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
