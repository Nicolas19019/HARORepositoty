package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Service.EstudianteService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/estudiantes")
@CrossOrigin(origins = "*")
public class EstudianteController {

    private final EstudianteService service;

    public EstudianteController(EstudianteService service) {
        this.service = service;
    }

    @GetMapping
    public List<Estudiante> getAll() {
        return service.getAllEstudiantes();
    }

    @GetMapping("/{id}")
    public Estudiante getById(@PathVariable long id) { // <-- long
        return service.getEstudianteById(id)
                .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<Estudiante> create(@RequestBody Estudiante e) {
        e.setId(null); // <-- CLAVE: garantiza INSERT
        Estudiante created = service.createEstudiante(e);
        return ResponseEntity.created(URI.create("/api/estudiantes/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public Estudiante update(@PathVariable long id, @RequestBody Estudiante e) {
        // Mejor flujo: leer y aplicar cambios (ver service abajo)
        return service.updateEstudiante(id, e);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) { // <-- long
        service.deleteEstudiante(id);
        return ResponseEntity.noContent().build();
    }
}
