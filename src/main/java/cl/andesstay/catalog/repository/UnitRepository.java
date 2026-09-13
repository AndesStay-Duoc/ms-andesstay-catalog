package cl.andesstay.catalog.repository;

import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByActiveTrue();
    List<Unit> findByTypeAndActiveTrue(UnitType type);
    List<Unit> findByAvailableSlotsGreaterThanAndActiveTrue(int minSlots);
}
