package cl.andesstay.catalog.service;

import cl.andesstay.catalog.domain.Availability;
import cl.andesstay.catalog.domain.Hold;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.repository.AvailabilityRepository;
import cl.andesstay.catalog.repository.HoldRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import cl.andesstay.catalog.web.SinDisponibilidadException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserva y liberación de cupo sobre el calendario de disponibilidad.
 *
 * Sustituye al antiguo {@code PATCH /internal/units/{id}/slots?delta=±1}, que tenía tres problemas:
 * no sabía de fechas, así que una reserva de marzo bloqueaba la unidad en julio; no era
 * idempotente, así que un reintento descontaba dos veces; y no era transaccional respecto del
 * rango, así que podía dejar media estadía comprometida.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final UnitRepository unitRepository;
    private final AvailabilityRepository availabilityRepository;
    private final HoldRepository holdRepository;

    /**
     * Compromete un cupo por cada noche del rango [desde, hasta).
     *
     * Todo o nada: la transacción es una sola, así que si falta cupo en cualquier noche no se
     * descuenta ninguna. Es lo que hace que confirmar sin disponibilidad deje la reserva intacta,
     * la regla que resuelve el overbooking del caso.
     *
     * Idempotente por {@code holdId}: repetirlo devuelve el hold existente sin volver a descontar.
     *
     * @throws SinDisponibilidadException si alguna noche del rango no tiene cupo
     */
    @Transactional
    public Hold reservar(Long unitId, LocalDate desde, LocalDate hasta, String holdId) {
        Hold existente = holdRepository.findById(holdId).orElse(null);
        if (existente != null) {
            log.info("[Disponibilidad] hold {} ya existía en estado {}; no se descuenta de nuevo",
                    holdId, existente.getEstado());
            return existente;
        }

        Unit unidad = unitRepository.findById(unitId)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + unitId + " no encontrada"));
        validarRango(desde, hasta);

        for (LocalDate noche : noches(desde, hasta)) {
            Availability dia = obtenerOCrear(unidad, noche);
            if (dia.getCuposDisponibles() <= 0) {
                // Deja la transacción sin efecto: ninguna noche queda descontada.
                throw new SinDisponibilidadException(unitId, noche);
            }
            dia.tomarUnCupo();
            availabilityRepository.save(dia);
        }

        Hold hold = Hold.builder()
                .holdId(holdId)
                .unitId(unitId)
                .desde(desde)
                .hasta(hasta)
                .estado(Hold.Estado.ACTIVO)
                .creadoEn(LocalDateTime.now())
                .build();
        holdRepository.save(hold);

        log.info("[Disponibilidad] hold {} tomado · unidad={} noches={} ({} a {})",
                holdId, unitId, noches(desde, hasta).size(), desde, hasta);
        return hold;
    }

    /**
     * Devuelve los cupos de un hold.
     *
     * Idempotente en los dos sentidos: un hold inexistente no es un error —puede que la reserva
     * nunca llegara a confirmarse— y uno ya liberado no vuelve a sumar. Devolver dos veces el
     * mismo cupo inventaría plazas, que es peor que no devolverlo.
     */
    @Transactional
    public void liberar(String holdId) {
        Hold hold = holdRepository.findById(holdId).orElse(null);

        if (hold == null) {
            log.info("[Disponibilidad] release de un hold inexistente {}; no hay nada que devolver", holdId);
            return;
        }
        if (hold.getEstado() == Hold.Estado.LIBERADO) {
            log.info("[Disponibilidad] hold {} ya estaba liberado; no se suma de nuevo", holdId);
            return;
        }

        for (LocalDate noche : noches(hold.getDesde(), hold.getHasta())) {
            availabilityRepository.findByUnitIdAndFecha(hold.getUnitId(), noche)
                    .ifPresent(dia -> {
                        dia.devolverUnCupo();
                        availabilityRepository.save(dia);
                    });
        }

        hold.setEstado(Hold.Estado.LIBERADO);
        hold.setLiberadoEn(LocalDateTime.now());
        holdRepository.save(hold);

        log.info("[Disponibilidad] hold {} liberado · unidad={}", holdId, hold.getUnitId());
    }

    /** Calendario del rango, creando las filas que falten, para mostrarlo en la interfaz. */
    @Transactional
    public List<Availability> calendario(Long unitId, LocalDate desde, LocalDate hasta) {
        Unit unidad = unitRepository.findById(unitId)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + unitId + " no encontrada"));
        validarRango(desde, hasta);

        List<Availability> dias = new ArrayList<>();
        for (LocalDate noche : noches(desde, hasta)) {
            dias.add(availabilityRepository.save(obtenerOCrear(unidad, noche)));
        }
        return dias;
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    /**
     * Fila del día, creándola si no existe a partir de la capacidad diaria por omisión de la
     * unidad.
     *
     * La creación perezosa evita tener que sembrar un calendario de años por adelantado y hace que
     * una unidad nueva quede disponible desde el primer momento.
     */
    private Availability obtenerOCrear(Unit unidad, LocalDate fecha) {
        return availabilityRepository.findByUnitIdAndFecha(unidad.getId(), fecha)
                .orElseGet(() -> Availability.builder()
                        .unitId(unidad.getId())
                        .fecha(fecha)
                        .cuposTotales(unidad.getAvailableSlots())
                        .cuposDisponibles(unidad.getAvailableSlots())
                        .build());
    }

    /**
     * Noches del rango: [desde, hasta). El día de salida NO se cobra ni se ocupa, porque el
     * huésped se va esa mañana y la unidad queda libre para quien entre ese mismo día.
     */
    private List<LocalDate> noches(LocalDate desde, LocalDate hasta) {
        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate d = desde; d.isBefore(hasta); d = d.plusDays(1)) {
            dias.add(d);
        }
        return dias;
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException(
                    "El rango de fechas es inválido: la salida tiene que ser posterior a la entrada.");
        }
    }
}
