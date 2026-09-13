package cl.andesstay.catalog.controller;

import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.repository.UnitRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final UnitRepository unitRepository;

    /** GET /api/catalog/units — Todos los activos (Admin + Operador) */
    @GetMapping("/units")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public ResponseEntity<List<Unit>> listUnits() {
        return ResponseEntity.ok(unitRepository.findByActiveTrue());
    }

    /** POST /api/catalog/units — Solo Admin */
    @PostMapping("/units")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Unit> createUnit(@Valid @RequestBody Unit unit) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitRepository.save(unit));
    }

    /** GET /api/catalog/units/{id} */
    @GetMapping("/units/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public ResponseEntity<Unit> getUnit(@PathVariable Long id) {
        return ResponseEntity.ok(unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada")));
    }

    /**
     * PUT /api/catalog/units/{id} — Admin actualiza tarifa, disponibilidad o descripción.
     * La disponibilidad disminuye automáticamente al CONFIRMAR una reserva
     * (eso lo maneja ms-reservations vía este endpoint).
     */
    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Unit> updateUnit(@PathVariable Long id,
                                           @Valid @RequestBody Unit unitData) {
        Unit existing = unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada"));
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

    /**
     * PATCH /api/catalog/internal/units/{id}/slots?delta=-1|+1
     *
     * Endpoint INTERNO (no expuesto fuera del compose, no requiere JWT de usuario).
     * Solo llamado por ms-andesstay-reservations al confirmar o cancelar/checkout.
     *   delta = -1 → CONFIRMAR reserva (ocupa un cupo)
     *   delta = +1 → CANCELAR o CHECKOUT (libera el cupo)
     */
    @PatchMapping("/internal/units/{id}/slots")
    public ResponseEntity<Void> adjustSlots(@PathVariable Long id,
                                            @RequestParam int delta) {
        Unit unit = unitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Unidad " + id + " no encontrada"));
        if (delta < 0) {
            unit.decreaseAvailability();   // lanza IllegalStateException si ya es 0
        } else if (delta > 0) {
            unit.increaseAvailability();
        }
        unitRepository.save(unit);
        return ResponseEntity.noContent().build();
    }
}
