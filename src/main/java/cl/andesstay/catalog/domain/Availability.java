package cl.andesstay.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Disponibilidad de una unidad en un día concreto.
 *
 * Sustituye al contador único {@code Unit.availableSlots} como fuente de verdad. La diferencia es
 * la que separa un hostal de una planilla: con un contador, reservar la cabaña para marzo la deja
 * ocupada también en julio. Con una fila por día, cada noche se reserva por separado, que es lo que
 * el caso pide cuando habla de «administrar disponibilidad».
 *
 * REPARTO DE RESPONSABILIDADES, para que no queden dos fuentes de verdad:
 *
 *   · {@code Unit.availableSlots} pasa a ser la CAPACIDAD DIARIA POR OMISIÓN: cuántas plazas tiene
 *     la unidad en un día del que todavía no existe fila.
 *   · {@code Availability} manda sobre los días que sí tienen fila.
 *
 * Las filas se crean de forma perezosa, la primera vez que alguien consulta o reserva ese día. Así
 * no hay que sembrar un calendario de años por adelantado, y una unidad nueva queda disponible
 * desde el primer momento.
 *
 * El {@code @Version} es lo que impide el overbooking cuando dos recepcionistas confirman a la vez
 * sobre la última plaza: la segunda transacción falla con bloqueo optimista y se traduce en un 409.
 */
@Entity
@Table(
        name = "CATALOG_AVAILABILITY",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_disponibilidad_unidad_fecha",
                columnNames = {"UNIT_ID", "FECHA"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "availability_seq")
    @SequenceGenerator(name = "availability_seq", sequenceName = "SEQ_CATALOG_AVAILABILITY",
            allocationSize = 1)
    private Long id;

    @Column(name = "UNIT_ID", nullable = false)
    private Long unitId;

    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    @Column(name = "CUPOS_TOTALES", nullable = false)
    private Integer cuposTotales;

    @Column(name = "CUPOS_DISPONIBLES", nullable = false)
    private Integer cuposDisponibles;

    /**
     * Bloqueo optimista. Sin esto, dos confirmaciones simultáneas sobre la misma plaza leen el
     * mismo valor, las dos lo decrementan y las dos escriben: el cupo baja una sola vez y la
     * unidad queda sobrevendida. Con la versión, la segunda escritura falla y el llamador recibe
     * un 409.
     */
    @Version
    @Column(name = "VERSION")
    private Long version;

    public void tomarUnCupo() {
        if (cuposDisponibles <= 0) {
            throw new IllegalStateException(
                    "Sin cupos para la unidad " + unitId + " el " + fecha);
        }
        cuposDisponibles--;
    }

    public void devolverUnCupo() {
        // Nunca por encima del total: devolver dos veces el mismo cupo inventaría plazas.
        if (cuposDisponibles < cuposTotales) {
            cuposDisponibles++;
        }
    }
}
