package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Service.ModuloAprendizajeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/modulos-aprendizaje")
@CrossOrigin(origins = "*")
public class ModuloAprendizajeController {

    private final ModuloAprendizajeService service;

    public ModuloAprendizajeController(ModuloAprendizajeService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModuloAprendizaje> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ModuloAprendizaje getById(@PathVariable Long id) {
        return service.getById(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    @GetMapping("/estudiante/{idEstudiante}")
    public List<ModuloAprendizaje> getByEstudiante(@PathVariable Long idEstudiante) {
        return service.getByEstudiante(idEstudiante);
    }

    @GetMapping("/estudiante/{idEstudiante}/curso/{curso}")
    public List<ModuloAprendizaje> getByEstudianteAndCurso(@PathVariable Long idEstudiante,
                                                            @PathVariable String curso) {
        return service.getByEstudianteAndCurso(idEstudiante, curso);
    }

    @PostMapping
    public ResponseEntity<ModuloAprendizaje> create(@RequestBody ModuloAprendizaje input) {
        ModuloAprendizaje created = service.create(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ModuloAprendizaje update(@PathVariable Long id, @RequestBody ModuloAprendizaje input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
