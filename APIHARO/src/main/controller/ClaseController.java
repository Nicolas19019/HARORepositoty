package com.Carritos.AcademiaCarros.Controller.MySQL1;

import com.Carritos.AcademiaCarros.Model.MySQL1.Clase;
import com.Carritos.AcademiaCarros.Service.Mysql.ClaseService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/clases")
@CrossOrigin(origins = "*")
public class ClaseController {

    private final ClaseService service;

    public ClaseController(ClaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<Clase> getAll() {
        return service.getAllClases();
    }

    @GetMapping("/{id}")
    public Clase getById(@PathVariable int id) {
        return service.getClaseById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
    }

    @PostMapping
    public ResponseEntity<Clase> create(@RequestBody Clase c) {
        Clase created = service.createClase(c);
        return ResponseEntity.created(URI.create("/api/clases/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Clase update(@PathVariable int id, @RequestBody Clase c) {
        c.setId(id);
        return service.updateClase(c);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deleteClase(id);
        return ResponseEntity.noContent().build();
    }
}
