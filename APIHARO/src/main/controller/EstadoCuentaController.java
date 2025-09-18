package com.Carritos.AcademiaCarros.Controller.MySQL1;

import com.Carritos.AcademiaCarros.Model.MySQL1.EstadoCuenta;
import com.Carritos.AcademiaCarros.Service.Mysql.EstadoCuentaService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

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
    public EstadoCuenta getById(@PathVariable int id) {
        return service.getEstadoCuentaById(id)
                .orElseThrow(() -> new NoSuchElementException("Estado de cuenta no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<EstadoCuenta> create(@RequestBody EstadoCuenta e) {
        EstadoCuenta created = service.createEstadoCuenta(e);
        return ResponseEntity.created(URI.create("/api/estados-cuenta/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public EstadoCuenta update(@PathVariable int id, @RequestBody EstadoCuenta e) {
        e.setId(id);
        return service.updateEstadoCuenta(e);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deleteEstadoCuenta(id);
        return ResponseEntity.noContent().build();
    }
}
