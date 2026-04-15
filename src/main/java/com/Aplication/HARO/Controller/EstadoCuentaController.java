package com.Aplication.HARO.Controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Service.EstadoCuentaService;

/**
 * Estado resumido de estado cuenta.
 */
@RestController
@RequestMapping("/api/estados-cuenta")
@CrossOrigin(origins = "*")
public class EstadoCuentaController {

    private final EstadoCuentaService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public EstadoCuentaController(EstadoCuentaService service) {
        this.service = service;
    }

/**
 * Lista los registros de estado cuenta.
 */
    @GetMapping
    public List<EstadoCuenta> getAll() {
        return service.getAllEstadosCuenta();
    }

/**
 * Obtiene un registro de estado cuenta por su identificador.
 */
    @GetMapping("/{id}")
    public EstadoCuenta getById(@PathVariable Long id) {
        return service.getEstadoCuentaById(id)
                .orElseThrow(() -> new NoSuchElementException("Estado de cuenta no encontrado: " + id));
    }

/**
 * Crea un nuevo registro de estado cuenta.
 */
    @PostMapping
    public ResponseEntity<EstadoCuenta> create(@RequestBody EstadoCuenta e) {
        // Asegura INSERT
        e.setId(null);
        EstadoCuenta created = service.createEstadoCuenta(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

/**
 * Actualiza un registro existente de estado cuenta.
 */
    @PutMapping("/{id}")
    public EstadoCuenta update(@PathVariable Long id, @RequestBody EstadoCuenta e) {
        return service.updateEstadoCuenta(id, e);
    }

/**
 * Elimina un registro de estado cuenta.
 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteEstadoCuenta(id);
        return ResponseEntity.noContent().build();
    }
}
