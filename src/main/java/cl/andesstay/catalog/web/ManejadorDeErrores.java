package cl.andesstay.catalog.web;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de negocio a los cuerpos exactos que fija
 * infra/docs/contracts/estados.md.
 *
 * Antes no existía en ningún servicio, así que cada 409 y cada 404 documentados en el contrato
 * salían como 500 con una traza de Spring. Para el frontend eso es indistinguible de una caída del
 * backend: no podía decirle al usuario «no queda cupo» porque no tenía forma de saberlo.
 */
@Slf4j
@RestControllerAdvice
public class ManejadorDeErrores {

    /** 409 · No queda cupo en alguna noche del rango. */
    @ExceptionHandler(SinDisponibilidadException.class)
    public ResponseEntity<Map<String, Object>> sinDisponibilidad(SinDisponibilidadException e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", "SIN_DISPONIBILIDAD");
        cuerpo.put("unitId", e.getUnitId());
        cuerpo.put("fecha", e.getFecha() == null ? null : e.getFecha().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /**
     * 409 · Dos transacciones compitieron por la misma plaza y esta perdió.
     *
     * Para quien llama es exactamente lo mismo que quedarse sin cupo —la plaza ya no está—, así
     * que se responde con el mismo código y el mismo error. Distinguirlo solo trasladaría al
     * cliente un detalle de implementación del bloqueo optimista.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> conflictoDeConcurrencia(OptimisticLockingFailureException e) {
        log.warn("[Disponibilidad] conflicto de bloqueo optimista: {}", e.getMessage());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", "SIN_DISPONIBILIDAD");
        cuerpo.put("detalle", "La plaza la tomó otra operación simultánea.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /** 404 · La unidad no existe. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> noEncontrada(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "UNIDAD_NO_ENCONTRADA", "detalle", e.getMessage()));
    }

    /** 403 · Autenticado pero sin el rol necesario. Nunca 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> sinPermiso(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ROL_INSUFICIENTE"));
    }

    /** 400 · Petición mal formada: rango de fechas inválido, parámetros fuera de rango. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> peticionInvalida(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "PETICION_INVALIDA", "detalle", e.getMessage()));
    }

    /** 400 · Falló la validación del cuerpo. Se devuelve campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, Object> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "VALIDACION", "campos", campos));
    }

    /**
     * 409 · Reglas de negocio del dominio expresadas con IllegalStateException.
     *
     * No se captura Exception a secas a propósito: un fallo inesperado tiene que seguir siendo un
     * 500 con su traza en el log. Convertir todo en 409 escondería defectos reales.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> reglaDeNegocio(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "REGLA_DE_NEGOCIO", "detalle", e.getMessage()));
    }
}
