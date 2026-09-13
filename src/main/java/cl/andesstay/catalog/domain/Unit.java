package cl.andesstay.catalog.domain;

import jakarta.persistence.*;
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
