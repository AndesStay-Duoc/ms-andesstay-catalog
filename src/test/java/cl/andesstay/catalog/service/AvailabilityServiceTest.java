package cl.andesstay.catalog.service;

import cl.andesstay.catalog.domain.Availability;
import cl.andesstay.catalog.domain.Hold;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.domain.UnitType;
import cl.andesstay.catalog.repository.AvailabilityRepository;
import cl.andesstay.catalog.repository.HoldRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import cl.andesstay.catalog.web.SinDisponibilidadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calendario de disponibilidad: lo que convierte el contador único en una plataforma de reservas
 * por fechas.
 *
 * Son los casos que el contrato de estados exige y que antes no se podían cumplir, porque la
 * disponibilidad era un solo número por unidad sin noción de calendario.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AvailabilityService.class)
class AvailabilityServiceTest {

    private static final LocalDate ENTRADA = LocalDate.of(2026, 11, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 11, 13);   // 3 noches: 10, 11 y 12

    @Autowired
    private AvailabilityService service;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private AvailabilityRepository availabilityRepository;
    @Autowired
    private HoldRepository holdRepository;

    private Unit cabana;
    private Unit lodge;

    @BeforeEach
    void sembrar() {
        cabana = unitRepository.save(Unit.builder()
                .hostalId(1L).name("Cabana Coigue").type(UnitType.CABANA).capacity(4)
                .pricePerNight(new BigDecimal("45000")).availableSlots(2)
                .active(true).build());

        // Una sola plaza: es la unidad con la que se prueba el agotamiento.
        lodge = unitRepository.save(Unit.builder()
                .hostalId(2L).name("Lodge Villarrica").type(UnitType.LODGE).capacity(8)
                .pricePerNight(new BigDecimal("120000")).availableSlots(1)
                .active(true).build());
    }

    private int cuposDe(Unit unidad, LocalDate fecha) {
        return availabilityRepository.findByUnitIdAndFecha(unidad.getId(), fecha)
                .map(Availability::getCuposDisponibles)
                .orElse(unidad.getAvailableSlots());
    }

    @Test
    @DisplayName("el hold descuenta un cupo por cada noche, y no el día de salida")
    void descuentaSoloLasNoches() {
        service.reservar(cabana.getId(), ENTRADA, SALIDA, "hold-1");

        assertEquals(1, cuposDe(cabana, LocalDate.of(2026, 11, 10)));
        assertEquals(1, cuposDe(cabana, LocalDate.of(2026, 11, 11)));
        assertEquals(1, cuposDe(cabana, LocalDate.of(2026, 11, 12)));
        // El 13 es el día de salida: el huésped se va esa mañana y la unidad queda libre.
        assertEquals(2, cuposDe(cabana, SALIDA));
    }

    @Test
    @DisplayName("repetir el mismo holdId no vuelve a descontar")
    void holdIdempotente() {
        service.reservar(cabana.getId(), ENTRADA, SALIDA, "hold-1");
        service.reservar(cabana.getId(), ENTRADA, SALIDA, "hold-1");
        service.reservar(cabana.getId(), ENTRADA, SALIDA, "hold-1");

        assertEquals(1, cuposDe(cabana, ENTRADA), "tres intentos del mismo hold descuentan una vez");
        assertEquals(1, holdRepository.count());
    }

    @Test
    @DisplayName("sin cupo en una noche falla con SIN_DISPONIBILIDAD")
    void sinCupoFalla() {
        service.reservar(lodge.getId(), ENTRADA, SALIDA, "hold-1");

        SinDisponibilidadException e = assertThrows(SinDisponibilidadException.class,
                () -> service.reservar(lodge.getId(), ENTRADA, SALIDA, "hold-2"));

        assertEquals(lodge.getId(), e.getUnitId());
    }

    @Test
    @DisplayName("basta UNA noche sin cupo para rechazar todo el rango")
    void unaNocheOcupadaRechazaElRango() {
        // La noche del 11 queda tomada por otra reserva corta.
        service.reservar(lodge.getId(), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12), "corto");

        assertThrows(SinDisponibilidadException.class,
                () -> service.reservar(lodge.getId(), ENTRADA, SALIDA, "largo"),
                "una estadía que cruza una noche ocupada no se puede confirmar");
    }

    @Test
    @DisplayName("el release devuelve los cupos y es idempotente")
    void releaseDevuelveYNoDuplica() {
        service.reservar(cabana.getId(), ENTRADA, SALIDA, "hold-1");
        assertEquals(1, cuposDe(cabana, ENTRADA));

        service.liberar("hold-1");
        assertEquals(2, cuposDe(cabana, ENTRADA));

        // Devolver dos veces la misma plaza inventaría disponibilidad.
        service.liberar("hold-1");
        service.liberar("hold-1");
        assertEquals(2, cuposDe(cabana, ENTRADA), "el cupo no puede subir por encima del total");

        assertEquals(Hold.Estado.LIBERADO, holdRepository.findById("hold-1").orElseThrow().getEstado());
    }

    @Test
    @DisplayName("liberar un hold inexistente no es un error")
    void releaseDeHoldInexistente() {
        service.liberar("nunca-existio");
        assertEquals(0, holdRepository.count());
    }

    @Test
    @DisplayName("tras liberar, la unidad se puede volver a reservar")
    void cicloCompleto() {
        service.reservar(lodge.getId(), ENTRADA, SALIDA, "hold-1");
        service.liberar("hold-1");
        service.reservar(lodge.getId(), ENTRADA, SALIDA, "hold-2");

        assertEquals(0, cuposDe(lodge, ENTRADA));
    }

    @Test
    @DisplayName("la búsqueda por fechas excluye la unidad con alguna noche llena")
    void busquedaPorFechas() {
        List<Unit> antes = unitRepository.buscarDisponibles(ENTRADA, SALIDA, null, null);
        assertEquals(2, antes.size(), "al principio las dos unidades están libres");

        service.reservar(lodge.getId(), ENTRADA, SALIDA, "hold-1");

        List<Unit> despues = unitRepository.buscarDisponibles(ENTRADA, SALIDA, null, null);
        assertEquals(1, despues.size());
        assertEquals(cabana.getId(), despues.get(0).getId(), "el lodge agotado ya no aparece");
    }

    @Test
    @DisplayName("la búsqueda filtra por hostal")
    void busquedaPorHostal() {
        // El caso habla de una red de 20 establecimientos: hay que poder preguntar por uno solo.
        List<Unit> delPrimero = unitRepository.buscarDisponibles(ENTRADA, SALIDA, null, 1L);

        assertEquals(1, delPrimero.size());
        assertEquals(cabana.getId(), delPrimero.get(0).getId());
    }

    @Test
    @DisplayName("la búsqueda respeta la capacidad pedida")
    void busquedaPorCapacidad() {
        List<Unit> paraSeis = unitRepository.buscarDisponibles(ENTRADA, SALIDA, 6, null);

        assertEquals(1, paraSeis.size());
        assertEquals(lodge.getId(), paraSeis.get(0).getId(), "la cabaña admite 4, no 6");
    }

    @Test
    @DisplayName("un rango invertido o vacío se rechaza")
    void rangoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reservar(cabana.getId(), SALIDA, ENTRADA, "h"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reservar(cabana.getId(), ENTRADA, ENTRADA, "h"));
    }

    @Test
    @DisplayName("el calendario crea las filas que faltan con la capacidad por omisión")
    void calendarioPerezoso() {
        assertTrue(availabilityRepository.findAll().isEmpty(), "no hay filas sembradas");

        List<Availability> dias = service.calendario(cabana.getId(), ENTRADA, SALIDA);

        assertEquals(3, dias.size());
        assertTrue(dias.stream().allMatch(d -> d.getCuposDisponibles() == 2));
        assertTrue(dias.stream().allMatch(d -> d.getCuposTotales() == 2));
    }
}
