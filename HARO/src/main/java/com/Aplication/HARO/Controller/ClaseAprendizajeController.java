package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Service.ClaseAprendizajeService;
import com.Aplication.HARO.Service.ContenidoClaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

record ClasePatchEstadoReq(Boolean publicada, Boolean visible) {}

@RestController
@RequestMapping("/api/clases")
@CrossOrigin(origins = "*")
public class ClaseAprendizajeController {

    private static final Logger log = LoggerFactory.getLogger(ClaseAprendizajeController.class);

    private final ClaseAprendizajeService claseService;
    private final ContenidoClaseService contenidoService;

    public ClaseAprendizajeController(ClaseAprendizajeService claseService,
                                      ContenidoClaseService contenidoService) {
        this.claseService = claseService;
        this.contenidoService = contenidoService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClaseAprendizaje> getAllAdmin() {
        try {
            return claseService.getAllAdmin();
        } catch (RuntimeException ex) {
            log.error("Error listando clases admin: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    @GetMapping("/publicadas")
    @PreAuthorize("hasAnyRole('ADMIN','ESTUDIANTE')")
    public List<ClaseAprendizaje> getPublicadas(@RequestParam(required = false) String curso,
                                                @RequestParam(required = false) String area) {
        return claseService.getPublicadas(curso, area);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaseAprendizaje> create(@RequestBody ClaseAprendizaje in) {
        ClaseAprendizaje created = claseService.create(in);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaseAprendizaje update(@PathVariable Long id, @RequestBody ClaseAprendizaje in) {
        return claseService.update(id, in);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaseAprendizaje patchEstado(@PathVariable Long id, @RequestBody ClasePatchEstadoReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Body requerido");
        }
        return claseService.patchEstado(id, req.publicada(), req.visible());
    }

    @GetMapping("/{id}/contenidos")
    @PreAuthorize("hasAnyRole('ADMIN','ESTUDIANTE')")
    public List<ContenidoClase> getContenidos(@PathVariable Long id, Authentication authentication) {
        try {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            return isAdmin ? contenidoService.getByClaseForAdmin(id)
                    : contenidoService.getByClasePublicada(id);
        } catch (RuntimeException ex) {
            log.error("Error listando contenidos de clase {}: {}", id, ex.getMessage(), ex);
            return List.of();
        }
    }
}
