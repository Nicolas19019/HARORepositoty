package com.Carritos.AcademiaCarros.Controller.MySQL1;

import com.Carritos.AcademiaCarros.Model.MySQL1.Estudiante;
import com.Carritos.AcademiaCarros.Service.Mysql.EstudianteService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

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
    public Estudiante getById(@PathVariable int id) {
        return service.getEstudianteById(id)
                .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<Estudiante> create(@RequestBody Estudiante e) {
        Estudiante created = service.createEstudiante(e);
        return ResponseEntity.created(URI.create("/api/estudiantes/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public Estudiante update(@PathVariable int id, @RequestBody Estudiante e) {
        // Forzar ID del path
        e.setId(id);
        return service.updateEstudiante(e);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deleteEstudiante(id);
        return ResponseEntity.noContent().build();
    }
}
