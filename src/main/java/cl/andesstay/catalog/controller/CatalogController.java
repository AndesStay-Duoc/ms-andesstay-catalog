package cl.andesstay.catalog.controller;

import cl.andesstay.catalog.domain.Availability;
import cl.andesstay.catalog.domain.Hold;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.repository.HostalRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import cl.andesstay.catalog.service.AvailabilityService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final UnitRepository unitRepository;
    private final HostalRepository hostalRepository;
    private final AvailabilityService availabilityService;

    /**
     * GET /api/catalog/units[?from=&to=&huespedes=]
     *
     * Sin fechas devuelve todas las unidades activas, como antes. Con ellas devuelve solo las que
     * están libres TODAS las noches del rango y tienen capacidad suficiente, que es la búsqueda
     * que de verdad sirve para reservar.
     *
     * El rol Cliente está incluido a propósito: un huésped que no puede ver el catálogo no puede
     * elegir qué reservar. Antes quedaba fuera, y el contrato de rutas lo documentaba como una
     * contradicción conocida.
     */
    @GetMapping("/units")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<List<Unit>> listUnits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer huespedes,
            @RequestParam(required = false) Long hostalId) {

        if (from == null && to == null) {
            return ResponseEntity.ok(hostalId == null
                    ? unitRepository.findByActiveTrue()
                    : unitRepository.findByHostalIdAndActiveTrue(hostalId));
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("Para filtrar por fechas hacen falta 'from' y 'to'.");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'to' tiene que ser posterior a 'from'.");
        }
        return ResponseEntity.ok(unitRepository.buscarDisponibles(from, to, huespedes, hostalId));
    }

    /** POST /api/catalog/units — Solo Admin */
    @PostMapping("/units")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Unit> createUnit(@Valid @RequestBody Unit unit) {
        validarHostal(unit.getHostalId());
        // El id lo asigna la secuencia: aceptar uno del cuerpo permitiria sobrescribir otra unidad.
        unit.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(unitRepository.save(unit));
    }

    /**
     * El hostal tiene que existir antes de colgarle una unidad.
     *
     * No hay clave foranea en la base, porque la columna admite nulos por las unidades anteriores
     * a este campo, asi que la comprobacion va aqui: sin ella, un hostalId inventado pasaria sin
     * ruido y la unidad quedaria huerfana otra vez.
     */
    private void validarHostal(Long hostalId) {
        if (hostalId != null && !hostalRepository.existsById(hostalId)) {
            throw new IllegalArgumentException("El hostal " + hostalId + " no existe.");
        }
    }

    /** GET /api/catalog/units/{id} */
    @GetMapping("/units/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<Unit> getUnit(@PathVariable Long id) {
        return ResponseEntity.ok(unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada")));
    }

    /**
     * GET /api/catalog/units/{id}/availability?from=&to=
     *
     * Calendario noche a noche del rango. Es lo que permite a la interfaz pintar qué días están
     * libres en lugar de mostrar un único número que no distingue fechas.
     */
    @GetMapping("/units/{id}/availability")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<List<Availability>> availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(availabilityService.calendario(id, from, to));
    }

    /**
     * PUT /api/catalog/units/{id} — Admin actualiza tarifa, capacidad diaria o descripción.
     *
     * Ojo con availableSlots: ya no es el contador de plazas libres, sino la CAPACIDAD DIARIA POR
     * OMISIÓN que se aplica a las noches sin fila de calendario. Las noches ya reservadas
     * conservan su propio conteo.
     */
    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Unit> updateUnit(@PathVariable Long id, @Valid @RequestBody Unit unitData) {
        Unit existing = unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada"));
        validarHostal(unitData.getHostalId());
        existing.setHostalId(unitData.getHostalId());
        existing.setName(unitData.getName());
        existing.setType(unitData.getType());
        existing.setCapacity(unitData.getCapacity());
        existing.setPricePerNight(unitData.getPricePerNight());
        existing.setAvailableSlots(unitData.getAvailableSlots());
        existing.setDescription(unitData.getDescription());
        return ResponseEntity.ok(unitRepository.save(existing));
    }

    /** DELETE /api/catalog/units/{id} — Soft delete */
    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
        Unit existing = unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada"));
        existing.setActive(false);
        unitRepository.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ── Endpoints internos: solo los llama ms-andesstay-reservations ─────────
    //
    // Ya NO son anónimos. Antes /api/catalog/internal/** estaba en permitAll, de modo que
    // cualquiera que alcanzara el puerto 8082 podía mover los cupos sin token. El BFF lo tapaba
    // con un denyAll, pero eso solo protege a quien pasa por el BFF. Ahora exigen un JWT válido
    // con rol de operación, y CatalogClient propaga el del llamante.

    /**
     * POST /api/catalog/internal/units/{id}/hold
     *
     * Compromete un cupo por cada noche de [from, to). Todo o nada, e idempotente por holdId:
     * reintentarlo no descuenta dos veces.
     */
    @PostMapping("/internal/units/{id}/hold")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<Hold> hold(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String holdId) {
        return ResponseEntity.ok(availabilityService.reservar(id, from, to, holdId));
    }

    /**
     * POST /api/catalog/internal/holds/{holdId}/release
     *
     * Devuelve los cupos. Idempotente: un hold inexistente o ya liberado no es un error, porque
     * devolver dos veces la misma plaza inventaría disponibilidad.
     */
    @PostMapping("/internal/holds/{holdId}/release")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<Void> release(@PathVariable String holdId) {
        availabilityService.liberar(holdId);
        return ResponseEntity.noContent().build();
    }
}
