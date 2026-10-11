package cl.andesstay.catalog.repository;

import cl.andesstay.catalog.domain.Hold;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, String> {
}
