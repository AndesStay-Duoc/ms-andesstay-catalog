package cl.andesstay.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reserva de cupo sobre un rango de noches.
 *
 * Existe para que {@code hold} y {@code release} sean idempotentes. Sin este registro no habría
 * forma de distinguir un reintento de red de una segunda reserva legítima, y reintentar un
 * {@code hold} descontaría el cupo dos veces. El {@code holdId} lo genera el llamador
 * —ms-andesstay-reservations usa el id de la reserva— y es la clave primaria.
 *
 * Guarda también el rango, porque {@code release} necesita saber qué noches devolver y el
 * llamador no tiene por qué repetírselo.
 */
@Entity
@Table(name = "CATALOG_HOLDS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hold {

    public enum Estado {
        /** El cupo está comprometido. */
        ACTIVO,
        /** El cupo ya fue devuelto; un release repetido no vuelve a sumarlo. */
        LIBERADO
    }

    @Id
    @Column(name = "HOLD_ID", length = 64)
    private String holdId;

    @Column(name = "UNIT_ID", nullable = false)
    private Long unitId;

    /** Primera noche comprometida. */
    @Column(name = "DESDE", nullable = false)
    private LocalDate desde;

    /** Día de salida: NO se comprometen cupos para esta fecha. */
    @Column(name = "HASTA", nullable = false)
    private LocalDate hasta;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private Estado estado;

    @Column(name = "CREADO_EN", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "LIBERADO_EN")
    private LocalDateTime liberadoEn;
}
