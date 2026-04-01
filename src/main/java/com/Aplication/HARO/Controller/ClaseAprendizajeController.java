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
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

record ClasePatchEstadoReq(Boolean publicada, Boolean visible) {}

@RestController
@RequestMapping({
        "/api/clases",
        "/api/clases_lms",
        "/api/clases-learning",
        "/api/clases-contenido",
        "/api/clases-modulo"
})
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
    public ClaseAprendizaje update(@PathVariable String id, @RequestBody ClaseAprendizaje in) {
        return claseService.update(resolveClaseId(id), in);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaseAprendizaje patchEstado(@PathVariable String id, @RequestBody ClasePatchEstadoReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Body requerido");
        }
        return claseService.patchEstado(resolveClaseId(id), req.publicada(), req.visible());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Long claseId = resolveClaseId(id);
        contenidoService.deleteByClaseFisico(claseId);
        claseService.deleteFisico(claseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHard(@PathVariable String id) {
        // Alias de DELETE /{id} (se mantiene por compatibilidad).
        Long claseId = resolveClaseId(id);
        contenidoService.deleteByClaseFisico(claseId);
        claseService.deleteFisico(claseId);
        return ResponseEntity.noContent().build();
    }

    // Compatibilidad legacy: algunos front envian PATCH /api/clases/{id}/hidden
    @PatchMapping("/{id}/hidden")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaseAprendizaje patchHidden(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> req) {
        Boolean hidden = null;
        if (req != null && req.containsKey("hidden")) {
            Object raw = req.get("hidden");
            if (raw instanceof Boolean b) hidden = b;
            else if (raw instanceof String s) hidden = Boolean.parseBoolean(s.trim());
        }
        // hidden=true -> visible=false ; hidden=false -> visible=true
        Boolean visible = (hidden == null) ? Boolean.FALSE : !hidden;
        return claseService.patchEstado(resolveClaseId(id), null, visible);
    }

    @GetMapping("/{id}/contenidos")
    @PreAuthorize("hasAnyRole('ADMIN','ESTUDIANTE')")
    public List<ContenidoClase> getContenidos(@PathVariable String id,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        try {
            Long claseId = resolveClaseId(id);
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            List<ContenidoClase> rows = isAdmin ? contenidoService.getByClaseForAdmin(claseId)
                    : contenidoService.getByClasePublicada(claseId);
            for (ContenidoClase row : rows) {
                row.setUrl(toAbsoluteUrl(row.getUrl(), request));
            }
            return rows;
        } catch (RuntimeException ex) {
            log.error("Error listando contenidos de clase {}: {}", id, ex.getMessage(), ex);
            return List.of();
        }
    }

    private Long resolveClaseId(String raw) {
        String in = raw == null ? "" : raw.trim();
        if (in.matches("\\d+")) {
            return Long.parseLong(in);
        }
        int dash = in.indexOf('-');
        if (dash > 0) {
            String head = in.substring(0, dash);
            if (head.matches("\\d+")) {
                return Long.parseLong(head);
            }
        }
        throw new IllegalArgumentException("id de clase invalido: " + raw);
    }

    private String toAbsoluteUrl(String rawUrl, HttpServletRequest request) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("uploads/")) {
            url = "/" + url;
        }
        if (!url.startsWith("/")) return url;
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + url;
    }
}
