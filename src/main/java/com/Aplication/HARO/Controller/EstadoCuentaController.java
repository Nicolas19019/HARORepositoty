package com.Aplication.HARO.Controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Service.EstadoCuentaService;

@RestController
@RequestMapping("/api/estados-cuenta")
@CrossOrigin(origins = "*")
public class EstadoCuentaController {

    private final EstadoCuentaService service;

    public EstadoCuentaController(EstadoCuentaService service) {
        this.service = service;
    }

    @GetMapping
    public List<EstadoCuenta> getAll() {
        return service.getAllEstadosCuenta();
    }

    @GetMapping("/{id}")
    public EstadoCuenta getById(@PathVariable Long id) {
        return service.getEstadoCuentaById(id)
                .orElseThrow(() -> new NoSuchElementException("Estado de cuenta no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<EstadoCuenta> create(@RequestBody EstadoCuenta e) {
        // Asegura INSERT
        e.setId(null);
        EstadoCuenta created = service.createEstadoCuenta(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public EstadoCuenta update(@PathVariable Long id, @RequestBody EstadoCuenta e) {
        return service.updateEstadoCuenta(id, e);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteEstadoCuenta(id);
        return ResponseEntity.noContent().build();
    }
}
