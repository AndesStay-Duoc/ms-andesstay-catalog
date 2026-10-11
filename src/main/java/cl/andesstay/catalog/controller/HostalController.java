package cl.andesstay.catalog.controller;

import cl.andesstay.catalog.domain.Hostal;
import cl.andesstay.catalog.domain.Unit;
import cl.andesstay.catalog.repository.HostalRepository;
import cl.andesstay.catalog.repository.UnitRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Establecimientos de la red: los «20 hostales, cabañas y lodges» del caso.
 *
 * Administrarlos es responsabilidad del Admin, igual que el inventario de unidades. Leerlos lo
 * puede hacer cualquier rol que vaya a elegir una unidad, incluido el huésped.
 */
@RestController
@RequestMapping("/api/catalog/hostales")
@RequiredArgsConstructor
public class HostalController {

    private final HostalRepository hostalRepository;
    private final UnitRepository unitRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<List<Hostal>> listar() {
        return ResponseEntity.ok(hostalRepository.findByActivoTrueOrderByNombre());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<Hostal> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(hostalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hostal " + id + " no encontrado")));
    }

    /** Unidades del establecimiento. Responde a «qué tiene el Lodge del Villarrica». */
    @GetMapping("/{id}/units")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<List<Unit>> unidades(@PathVariable Long id) {
        if (!hostalRepository.existsById(id)) {
            throw new EntityNotFoundException("Hostal " + id + " no encontrado");
        }
        return ResponseEntity.ok(unitRepository.findByHostalIdAndActiveTrue(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Hostal> crear(@Valid @RequestBody Hostal hostal) {
        if (hostalRepository.existsByCodigo(hostal.getCodigo())) {
            // El código es la referencia que usa la red para hablar del establecimiento, así que
            // duplicarlo haría ambigua cualquier conversación sobre él.
            throw new IllegalArgumentException(
                    "Ya existe un hostal con el código " + hostal.getCodigo());
        }
        hostal.setId(null);
        hostal.setCreadoEn(LocalDateTime.now());
        if (hostal.getActivo() == null) {
            hostal.setActivo(true);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(hostalRepository.save(hostal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Hostal> actualizar(@PathVariable Long id, @Valid @RequestBody Hostal datos) {
        Hostal existente = hostalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hostal " + id + " no encontrado"));

        hostalRepository.findByCodigo(datos.getCodigo())
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException(
                            "El código " + datos.getCodigo() + " ya lo usa otro hostal.");
                });

        existente.setCodigo(datos.getCodigo());
        existente.setNombre(datos.getNombre());
        existente.setComuna(datos.getComuna());
        existente.setDireccion(datos.getDireccion());
        if (datos.getActivo() != null) {
            existente.setActivo(datos.getActivo());
        }
        return ResponseEntity.ok(hostalRepository.save(existente));
    }
}
