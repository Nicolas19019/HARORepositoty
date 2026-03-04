package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Service.ModuloAprendizajeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

record FavoritosModuloRequest(String curso, List<String> modulos) {}

@RestController
@RequestMapping({
        "/api/modulos-aprendizaje",
        "/api/modulo-aprendizaje",
        "/api/modulos-aprendisaje"
})
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

    @GetMapping("/estudiante/{idEstudiante}/resumen")
    public ModuloAprendizajeService.ResumenAprendizaje getResumen(@PathVariable Long idEstudiante) {
        return service.getResumen(idEstudiante);
    }

    @GetMapping({
            "/admin/estudiantes-modulo",
            "/admin/estudiantes-modulo/",
            "/admin/estudiantes-modulos",
            "/admin/estudiantes_modulo"
    })
    public List<ModuloAprendizajeService.EstudianteUsoModulo> getEstudiantesConUsoModulo() {
        return service.getEstudiantesConUsoModulo();
    }

    @GetMapping({
            "/admin/resultados-examen",
            "/admin/resultados-examen/",
            "/admin/resultados-simulacro",
            "/admin/resultados_examen"
    })
    public List<ModuloAprendizajeService.ResultadoExamenAdmin> getResultadosExamen() {
        return service.getResultadosExamenPresentados();
    }

    @GetMapping("/estudiante/{idEstudiante}/favoritos")
    public List<String> getFavoritos(@PathVariable Long idEstudiante,
                                     @RequestParam String curso) {
        return service.getFavoritos(idEstudiante, curso);
    }

    @PutMapping("/estudiante/{idEstudiante}/favoritos")
    public ResponseEntity<Void> saveFavoritos(@PathVariable Long idEstudiante,
                                               @RequestBody FavoritosModuloRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }
        service.saveFavoritos(idEstudiante, req.curso(), req.modulos());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<ModuloAprendizaje> create(@RequestBody ModuloAprendizaje input) {
        ModuloAprendizaje created = service.create(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/upsert")
    public ResponseEntity<ModuloAprendizaje> upsert(@RequestBody ModuloAprendizaje input) {
        ModuloAprendizaje row = service.upsert(input);
        return ResponseEntity.ok(row);
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
