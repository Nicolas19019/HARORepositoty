package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Service.ProfesorService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/profesores")
@CrossOrigin(origins = "*")
public class ProfesorController {

    private final ProfesorService service;

    public ProfesorController(ProfesorService service) {
        this.service = service;
    }

    @GetMapping
    public List<Profesor> getAll() {
        return service.getAllProfesores();
    }

    @GetMapping("/{id}")
    public Profesor getById(@PathVariable int id) {
        return service.getProfesorById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<Profesor> create(@RequestBody Profesor p) {
        Profesor created = service.createProfesor(p);
        return ResponseEntity.created(URI.create("/api/profesores/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Profesor update(@PathVariable long id, @RequestBody Profesor p) {
        p.setId(id);
        return service.updateProfesor(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deleteProfesor(id);
        return ResponseEntity.noContent().build();
    }
}
