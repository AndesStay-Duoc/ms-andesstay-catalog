package cl.andesstay.catalog.repository;

import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.domain.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByActiveTrue();

    List<Unit> findByTypeAndActiveTrue(UnitType type);

    List<Unit> findByAvailableSlotsGreaterThanAndActiveTrue(int minSlots);

    /** Unidades de un establecimiento de la red. */
    List<Unit> findByHostalIdAndActiveTrue(Long hostalId);

    /**
     * Unidades libres TODAS las noches del rango [desde, hasta) y con capacidad suficiente.
     *
     * El NOT EXISTS es la clave: descarta la unidad si existe UNA sola noche del rango con los
     * cupos agotados. Buscar las que tienen alguna noche libre devolvería unidades imposibles de
     * reservar de corrido, que es el error clásico de este filtro.
     *
     * Las noches sin fila de disponibilidad cuentan como libres: las filas se crean de forma
     * perezosa y, mientras no existan, manda la capacidad diaria por omisión de la unidad. Por eso
     * se exige además availableSlots > 0, o una unidad con capacidad diaria cero aparecería libre
     * para todo el futuro.
     */
    @Query("""
            SELECT u FROM Unit u
            WHERE u.active = true
              AND u.availableSlots > 0
              AND (:huespedes IS NULL OR u.capacity >= :huespedes)
              AND (:hostalId IS NULL OR u.hostalId = :hostalId)
              AND NOT EXISTS (
                    SELECT 1 FROM Availability a
                    WHERE a.unitId = u.id
                      AND a.fecha >= :desde
                      AND a.fecha < :hasta
                      AND a.cuposDisponibles <= 0)
            ORDER BY u.id
            """)
    List<Unit> buscarDisponibles(@Param("desde") LocalDate desde,
                                 @Param("hasta") LocalDate hasta,
                                 @Param("huespedes") Integer huespedes,
                                 @Param("hostalId") Long hostalId);
}
