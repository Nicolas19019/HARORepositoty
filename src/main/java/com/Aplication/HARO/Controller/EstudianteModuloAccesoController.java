package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para estudiante modulo acceso.
 */
@RestController
@RequestMapping("/api/modulos-aprendizaje/acceso")
@CrossOrigin(origins = "*")
public class EstudianteModuloAccesoController {

    /**
     * DTO de entrada para registro modulo.
     */
    public record RegistroModuloRequest(
            Long idEstudiante,
            String email,
            String usuario,
            String documento,
            String origen
    ) {}

    private final EstudianteModuloAccesoService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public EstudianteModuloAccesoController(EstudianteModuloAccesoService service) {
        this.service = service;
    }

    /**
     * Registra o actualiza el acceso de un estudiante al modulo.
     */
    @PostMapping("/registro")
    public ResponseEntity<EstudianteModuloAccesoService.RegistroResponse> registrarModulo(
            @RequestBody RegistroModuloRequest req) {
        EstudianteModuloAccesoService.RegistroResponse out = service.registrarModulo(
                new EstudianteModuloAccesoService.RegistroRequest(
                        req == null ? null : req.idEstudiante(),
                        req == null ? null : req.email(),
                        req == null ? null : req.usuario(),
                        req == null ? null : req.documento(),
                        req == null ? null : req.origen()
                )
        );
        return ResponseEntity.ok(out);
    }

/**
 * Lista estudiantes con informacion consolidada para el modulo.
 */
    @GetMapping("/admin/estudiantes")
    public List<EstudianteModuloAccesoService.EstudianteModuloAdminRow> getEstudiantesRegistrados() {
        return service.getEstudiantesRegistradosModulo();
    }

/**
 * Obtiene el resumen administrativo del acceso al modulo.
 */
    @GetMapping("/admin/resumen")
    public EstudianteModuloAccesoService.ResumenAdmin getResumen() {
        return service.getResumenAdmin();
    }

/**
 * Consulta el registro de acceso de un estudiante al modulo.
 */
    @GetMapping("/estudiante/{idEstudiante}")
    public ResponseEntity<?> getRegistroByEstudiante(@PathVariable Long idEstudiante) {
        return service.getRegistroByEstudiante(idEstudiante)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
