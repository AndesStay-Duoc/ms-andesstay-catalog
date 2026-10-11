package cl.andesstay.catalog.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CATALOG_UNITS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unit_seq")
    @SequenceGenerator(name = "unit_seq", sequenceName = "SEQ_CATALOG_UNITS", allocationSize = 1)
    private Long id;

    /**
     * Establecimiento de la red al que pertenece la unidad.
     *
     * La columna admite nulos a proposito, aunque el campo sea obligatorio al crear o editar: las
     * unidades que existian antes de que apareciera el hostal no lo traen, y marcar la columna como
     * NOT NULL sobre una tabla con filas habria roto el arranque. La validacion esta en
     * @NotNull, que solo se aplica a los cuerpos de las peticiones.
     */
    @NotNull(message = "La unidad tiene que pertenecer a un hostal")
    @Column(name = "HOSTAL_ID")
    private Long hostalId;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 20)
    private UnitType type;   // HABITACION, CABANA, LODGE

    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    @Column(name = "PRICE_PER_NIGHT", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "AVAILABLE_SLOTS", nullable = false)
    private Integer availableSlots;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Decrementa disponibilidad al confirmar una reserva. */
    public void decreaseAvailability() {
        if (availableSlots <= 0) throw new IllegalStateException("Sin cupos disponibles para: " + name);
        availableSlots--;
    }

    /** Incrementa disponibilidad al cancelar o hacer checkout. */
    public void increaseAvailability() {
        availableSlots++;
    }
}
