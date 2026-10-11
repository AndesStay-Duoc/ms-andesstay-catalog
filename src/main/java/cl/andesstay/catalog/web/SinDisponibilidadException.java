package cl.andesstay.catalog.web;

import lombok.Getter;

import java.time.LocalDate;

/**
 * No queda cupo en alguna noche del rango pedido.
 *
 * Se traduce en {@code 409 SIN_DISPONIBILIDAD}, que es el código que el contrato exige y la regla
 * que resuelve el overbooking: confirmar sin cupo falla y la reserva permanece en CREADA.
 */
@Getter
public class SinDisponibilidadException extends RuntimeException {

    private final Long unitId;
    private final LocalDate fecha;

    public SinDisponibilidadException(Long unitId, LocalDate fecha) {
        super("Sin cupos para la unidad " + unitId + " el " + fecha);
        this.unitId = unitId;
        this.fecha = fecha;
    }
}
