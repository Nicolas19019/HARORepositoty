package com.Aplication.HARO.Controller;



import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Clase;

import com.Aplication.HARO.Service.ClaseService;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(created); // sin Location
    }

    @PutMapping("/{id}")
    public Clase update(@PathVariable long id, @RequestBody Clase e) {
        e.setId(id);
        return service.updateClase(e);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deleteClase(id);
        return ResponseEntity.noContent().build();
    }
}
