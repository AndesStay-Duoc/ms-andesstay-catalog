package cl.andesstay.catalog.config;

import cl.andesstay.catalog.domain.Hostal;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.domain.UnitType;
import cl.andesstay.catalog.repository.HostalRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Siembra el catálogo con la red de establecimientos del caso cuando está vacío.
 *
 * POR QUÉ EXISTE. El esquema se crea con {@code ddl-auto: update}, que levanta las tablas pero no
 * mete ni una fila. Sin esto, un entorno recién desplegado no tiene nada que reservar: hay que
 * entrar como Admin y dar de alta a mano el establecimiento y cada unidad antes de poder crear la
 * primera reserva. En el laboratorio, donde la infraestructura se borra con frecuencia, eso
 * significa repetir ese alta cada vez.
 *
 * POR QUÉ UN ApplicationRunner Y NO UN data.sql. Con {@code ddl-auto: update}, el
 * {@code spring.sql.init} corre ANTES de que Hibernate cree las tablas, salvo que se active
 * {@code defer-datasource-initialization}, y entonces el orden depende de una propiedad fácil de
 * perder en un refactor. Un runner corre siempre después del contexto, usa los repositorios y el
 * mismo mapeo que el resto del servicio, y se puede condicionar con lógica de verdad.
 *
 * ES IDEMPOTENTE POR CONSTRUCCIÓN, y esa es la propiedad que importa: solo siembra si la tabla de
 * establecimientos está vacía. Reiniciar el servicio, redesplegarlo o levantar una segunda
 * instancia contra la misma base no duplica nada ni pisa datos que alguien haya creado. La
 * comprobación es sobre los establecimientos y no sobre las unidades a propósito: si alguien borró
 * todas las unidades pero dejó el establecimiento, lo hizo queriendo.
 *
 * NO TOCA LA DISPONIBILIDAD. Las filas del calendario las crea {@code AvailabilityService} de forma
 * perezosa, la primera vez que se consulta o se reserva una fecha. Sembrarlas aquí obligaría a
 * decidir cuántos meses por adelantado, y quedarían obsoletas solas.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "andesstay.catalog.carga-inicial.enabled", havingValue = "true",
        matchIfMissing = true)
public class CargaInicial {

    @Bean
    public ApplicationRunner sembrarCatalogo(HostalRepository hostales, UnitRepository unidades) {
        return args -> {
            if (hostales.count() > 0) {
                log.info("[Carga inicial] El catálogo ya tiene {} establecimiento(s): no se siembra nada",
                        hostales.count());
                return;
            }

            log.info("[Carga inicial] Catálogo vacío: sembrando la red de establecimientos del caso");

            // Red de turismo de intereses especiales: montaña, lago y volcán. Las tarifas van en
            // pesos chilenos por noche, que es la moneda del caso.
            sembrar(hostales, unidades,
                    Hostal.builder()
                            .codigo("PUC-COI")
                            .nombre("Refugio Coigüe")
                            .comuna("Pucón")
                            .direccion("Camino al Volcán km 12, Pucón, La Araucanía")
                            .activo(true)
                            .build(),
                    List.of(
                            unidad("Cabaña Coigüe", UnitType.CABANA, 4, "45000", 2,
                                    "Cabaña de madera nativa con terraza y vista al Villarrica."),
                            unidad("Cabaña Araucaria", UnitType.CABANA, 6, "62000", 2,
                                    "Dos dormitorios, tinaja exterior y estacionamiento techado."),
                            unidad("Habitación Quetrupillán", UnitType.HABITACION, 2, "28000", 6,
                                    "Habitación doble con baño privado y desayuno incluido.")));

            sembrar(hostales, unidades,
                    Hostal.builder()
                            .codigo("PVA-LAZ")
                            .nombre("Lodge Lago Azul")
                            .comuna("Puerto Varas")
                            .direccion("Ruta 225 km 8, Puerto Varas, Los Lagos")
                            .activo(true)
                            .build(),
                    List.of(
                            unidad("Lodge Llanquihue", UnitType.LODGE, 8, "120000", 1,
                                    "Lodge completo frente al lago, cocina equipada y muelle propio."),
                            unidad("Cabaña Petrohué", UnitType.CABANA, 5, "55000", 3,
                                    "Cabaña con living a doble altura y salida directa al bosque."),
                            unidad("Habitación Osorno", UnitType.HABITACION, 3, "34000", 8,
                                    "Habitación triple con vista al volcán Osorno.")));

            sembrar(hostales, unidades,
                    Hostal.builder()
                            .codigo("SJM-CAJ")
                            .nombre("Hostal Cajón del Maipo")
                            .comuna("San José de Maipo")
                            .direccion("Camino al Volcán 24500, San José de Maipo, Metropolitana")
                            .activo(true)
                            .build(),
                    List.of(
                            unidad("Habitación Morado", UnitType.HABITACION, 2, "26000", 10,
                                    "Habitación matrimonial, base para el trekking al Glaciar El Morado."),
                            unidad("Cabaña Cajón", UnitType.CABANA, 4, "48000", 4,
                                    "Cabaña con estufa a leña y quincho, ideal para invierno.")));

            log.info("[Carga inicial] Sembrados {} establecimientos y {} unidades",
                    hostales.count(), unidades.count());
        };
    }

    /** Guarda el establecimiento y cuelga de él sus unidades, ya con el identificador asignado. */
    private void sembrar(HostalRepository hostales, UnitRepository unidades,
                         Hostal hostal, List<Unit> susUnidades) {
        Hostal guardado = hostales.save(hostal);
        susUnidades.forEach(u -> u.setHostalId(guardado.getId()));
        unidades.saveAll(susUnidades);
        log.info("[Carga inicial]   {} ({}) con {} unidad(es)",
                guardado.getNombre(), guardado.getCodigo(), susUnidades.size());
    }

    /**
     * Una unidad del catálogo.
     *
     * {@code capacidad} son las personas que caben; {@code cupos} es cuántas unidades iguales hay,
     * es decir cuántas reservas simultáneas admite esa misma noche. Confundirlos es fácil y tiene
     * consecuencias: con cupos en 1, la segunda reserva de la misma fecha recibe un 409.
     */
    private static Unit unidad(String nombre, UnitType tipo, int capacidad, String tarifa,
                               int cupos, String descripcion) {
        return Unit.builder()
                .name(nombre)
                .type(tipo)
                .capacity(capacidad)
                .pricePerNight(new BigDecimal(tarifa))
                .availableSlots(cupos)
                .description(descripcion)
                .active(true)
                .build();
    }
}
