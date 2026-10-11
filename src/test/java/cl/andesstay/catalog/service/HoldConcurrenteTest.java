package cl.andesstay.catalog.service;

import cl.andesstay.catalog.domain.Availability;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.domain.UnitType;
import cl.andesstay.catalog.repository.AvailabilityRepository;
import cl.andesstay.catalog.repository.HoldRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ítem 5.10 del checklist: dos `hold` concurrentes sobre la última plaza, solo uno gana.
 *
 * Es la prueba de que el `@Version` de {@link Availability} hace su trabajo. Sin bloqueo optimista,
 * las dos transacciones leen el mismo valor, las dos lo decrementan y las dos escriben: el cupo
 * baja una sola vez y la unidad queda sobrevendida. Es exactamente el overbooking que el caso
 * describe como problema de partida, y el único defecto de esta familia que no se puede detectar
 * mirando el código.
 *
 * NOTA SOBRE LA TRANSACCIÓN. La clase lleva `Propagation.NOT_SUPPORTED` a propósito: `@DataJpaTest`
 * envuelve cada test en una transacción que luego revierte, y dentro de una sola transacción no
 * hay concurrencia que medir. Sin esto, los dos hilos compartirían la misma y el test pasaría en
 * verde sin probar nada. El precio es que los datos sí se escriben, de ahí la limpieza explícita.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AvailabilityService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HoldConcurrenteTest {

    private static final LocalDate ENTRADA = LocalDate.of(2027, 3, 10);
    private static final LocalDate SALIDA = LocalDate.of(2027, 3, 12);   // 2 noches

    @Autowired
    private AvailabilityService service;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private AvailabilityRepository availabilityRepository;
    @Autowired
    private HoldRepository holdRepository;

    private Unit ultimaPlaza;

    @BeforeEach
    void sembrar() {
        limpiar();
        ultimaPlaza = unitRepository.save(Unit.builder()
                .hostalId(1L)
                .name("Lodge de la ultima plaza")
                .type(UnitType.LODGE)
                .capacity(8)
                .pricePerNight(new BigDecimal("120000"))
                .availableSlots(1)          // una sola: es el objeto de la prueba
                .active(true)
                .build());

        // Las filas del calendario se crean por adelantado para que los dos hilos compitan por el
        // bloqueo optimista y no por la restricción única de (unitId, fecha). Las dos carreras son
        // reales, pero esta prueba mide la primera; la segunda está cubierta en
        // `dosAltasSimultaneasDelMismoDia`.
        service.calendario(ultimaPlaza.getId(), ENTRADA, SALIDA);
    }

    @AfterEach
    void limpiar() {
        holdRepository.deleteAll();
        availabilityRepository.deleteAll();
        unitRepository.deleteAll();
    }

    /**
     * Se repite diez veces porque el resultado depende de cómo se intercalen los hilos: una sola
     * ejecución podría serializarse por casualidad y no ejercitar el bloqueo.
     */
    @RepeatedTest(10)
    @DisplayName("dos hold simultáneos sobre la última plaza: gana exactamente uno")
    void soloUnGanador() throws Exception {
        Resultado r = lanzarDosHoldSimultaneos("hold-A", "hold-B");

        assertEquals(1, r.exitos(),
                "dos hold no pueden tomar la misma plaza: eso es overbooking");
        assertEquals(1, r.fallos(),
                "el perdedor tiene que fallar, no quedarse a medias");

        // Lo que de verdad importa: el cupo bajó exactamente en uno, no en dos ni en cero.
        for (Availability dia : noches()) {
            assertEquals(0, dia.getCuposDisponibles(),
                    "la noche " + dia.getFecha() + " quedó en " + dia.getCuposDisponibles());
        }
        assertEquals(1, holdRepository.count(), "solo puede quedar registrado un hold");
    }

    @Test
    @DisplayName("el perdedor falla de forma reconocible, no con un error inesperado")
    void elPerdedorFallaDeFormaReconocible() throws Exception {
        Resultado r = lanzarDosHoldSimultaneos("hold-C", "hold-D");

        assertEquals(1, r.fallos());
        Throwable causa = r.primerFallo();

        // Se aceptan las tres formas en que puede perder, porque cuál ocurre depende del
        // intercalado y afirmar una sola haría el test inestable:
        //
        //   SinDisponibilidad  -> leyó después de que el otro confirmara
        //   bloqueo optimista  -> leyó antes y perdió al escribir
        //   integridad de datos-> ambos intentaron crear la misma fila
        //
        // Lo que NO se acepta es cualquier otra cosa: eso sería un defecto.
        String tipo = causa.getClass().getName();
        assertTrue(
                tipo.contains("SinDisponibilidad")
                        || tipo.contains("OptimisticLocking")
                        || tipo.contains("DataIntegrityViolation"),
                "el perdedor falló con algo inesperado: " + tipo + " -> " + causa.getMessage());
    }

    @Test
    @DisplayName("dos altas simultáneas del mismo día no duplican la fila del calendario")
    void dosAltasSimultaneasDelMismoDia() throws Exception {
        // La otra carrera: cuando la fila del calendario todavía no existe, los dos hilos intentan
        // crearla. La restricción única de (unitId, fecha) es lo que impide que acaben dos filas
        // para el mismo día, que dejaría la disponibilidad contada por duplicado.
        limpiar();
        Unit nueva = unitRepository.save(Unit.builder()
                .hostalId(1L).name("Sin calendario previo").type(UnitType.CABANA)
                .capacity(4).pricePerNight(new BigDecimal("45000")).availableSlots(1)
                .active(true).build());
        ultimaPlaza = nueva;

        lanzarDosHoldSimultaneos("hold-E", "hold-F");

        List<Availability> dias = noches();
        assertEquals(2, dias.size(), "una fila por noche, ni más ni menos");
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private record Resultado(int exitos, int fallos, Throwable primerFallo) {
    }

    /**
     * Lanza los dos hold en paralelo, sincronizados con un latch para que arranquen a la vez.
     *
     * Sin el latch, el primer hilo terminaría antes de que el segundo empezara y nunca habría
     * competencia.
     */
    private Resultado lanzarDosHoldSimultaneos(String holdA, String holdB) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> a = pool.submit(intento(holdA, salida));
            Future<Void> b = pool.submit(intento(holdB, salida));
            salida.countDown();

            int exitos = 0;
            int fallos = 0;
            Throwable primerFallo = null;
            for (Future<Void> f : List.of(a, b)) {
                try {
                    f.get(15, TimeUnit.SECONDS);
                    exitos++;
                } catch (ExecutionException e) {
                    fallos++;
                    if (primerFallo == null) {
                        primerFallo = e.getCause();
                    }
                }
            }
            return new Resultado(exitos, fallos, primerFallo);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Void> intento(String holdId, CountDownLatch salida) {
        return () -> {
            salida.await();
            service.reservar(ultimaPlaza.getId(), ENTRADA, SALIDA, holdId);
            return null;
        };
    }

    private List<Availability> noches() {
        return availabilityRepository
                .findByUnitIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFecha(
                        ultimaPlaza.getId(), ENTRADA, SALIDA);
    }
}
