package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Service.ModuloAprendizajeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * DTO de entrada para favoritos de m?dulo.
 */
record FavoritosModuloRequest(String curso, List<String> modulos) {}

@RestController
@RequestMapping("/api/modulos-aprendizaje")
@CrossOrigin(originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://ceaharo.com",
        "https://www.ceaharo.com",
        "https://*.ceaharo.com"
})
/**
 * Controlador REST para modulo aprendizaje.
 */
public class ModuloAprendizajeController {

    private final ModuloAprendizajeService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public ModuloAprendizajeController(ModuloAprendizajeService service) {
        this.service = service;
    }

/**
 * Lista los registros de modulo aprendizaje.
 */
    @GetMapping
    public List<ModuloAprendizaje> getAll() {
        return service.getAll();
    }

/**
 * Obtiene un registro de modulo aprendizaje por su identificador.
 */
    @GetMapping("/{id}")
    public ModuloAprendizaje getById(@PathVariable Long id) {
        return service.getById(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

/**
 * Lista los modulos asociados a un estudiante.
 */
    @GetMapping("/estudiante/{idEstudiante}")
    public List<ModuloAprendizaje> getByEstudiante(@PathVariable Long idEstudiante) {
        return service.getByEstudiante(idEstudiante);
    }

    /**
     * Lista los modulos de un estudiante filtrados por curso.
     */
    @GetMapping("/estudiante/{idEstudiante}/curso/{curso}")
    public List<ModuloAprendizaje> getByEstudianteAndCurso(@PathVariable Long idEstudiante,
                                                            @PathVariable String curso) {
        return service.getByEstudianteAndCurso(idEstudiante, curso);
    }

/**
 * Obtiene el resumen de avance del modulo para un estudiante.
 */
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
/**
 * Lista estudiantes con informacion consolidada para el modulo.
 */
    public List<ModuloAprendizajeService.EstudianteUsoModulo> getEstudiantesConUsoModulo() {
        return service.getEstudiantesConUsoModulo();
    }

    @GetMapping({
            "/admin/resultados-examen",
            "/admin/resultados-examen/",
            "/admin/resultados-simulacro",
            "/admin/resultados_examen"
    })
/**
 * Lista resultados consolidados para la vista administrativa.
 */
    public List<ModuloAprendizajeService.ResultadoExamenAdmin> getResultadosExamen() {
        return service.getResultadosExamenPresentados();
    }

    /**
     * Consulta los modulos favoritos de un estudiante para un curso.
     */
    @GetMapping("/estudiante/{idEstudiante}/favoritos")
    public List<String> getFavoritos(@PathVariable Long idEstudiante,
                                     @RequestParam String curso) {
        return service.getFavoritos(idEstudiante, curso);
    }

    /**
     * Guarda la lista de modulos favoritos de un estudiante.
     */
    @PutMapping("/estudiante/{idEstudiante}/favoritos")
    public ResponseEntity<Void> saveFavoritos(@PathVariable Long idEstudiante,
                                               @RequestBody FavoritosModuloRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }
        service.saveFavoritos(idEstudiante, req.curso(), req.modulos());
        return ResponseEntity.noContent().build();
    }

/**
 * Crea un nuevo registro de modulo aprendizaje.
 */
    @PostMapping
    public ResponseEntity<ModuloAprendizaje> create(@RequestBody ModuloAprendizaje input) {
        ModuloAprendizaje created = service.create(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

/**
 * Crea o actualiza un modulo de aprendizaje.
 */
    @PostMapping("/upsert")
    public ResponseEntity<ModuloAprendizaje> upsert(@RequestBody ModuloAprendizaje input) {
        ModuloAprendizaje row = service.upsert(input);
        return ResponseEntity.ok(row);
    }

/**
 * Actualiza un registro existente de modulo aprendizaje.
 */
    @PutMapping("/{id}")
    public ModuloAprendizaje update(@PathVariable Long id, @RequestBody ModuloAprendizaje input) {
        return service.update(id, input);
    }

/**
 * Elimina un registro de modulo aprendizaje.
 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
