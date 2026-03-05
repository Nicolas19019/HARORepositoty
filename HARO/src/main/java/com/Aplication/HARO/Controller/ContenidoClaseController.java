package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Service.ContenidoClaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "*")
public class ContenidoClaseController {

    private final ContenidoClaseService service;

    public ContenidoClaseController(ContenidoClaseService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/clases/{id}/contenidos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContenidoClase> create(@PathVariable("id") Long claseId,
                                                 @RequestParam String titulo,
                                                 @RequestParam String tipo,
                                                 @RequestParam(required = false) String descripcion,
                                                 @RequestParam(required = false) String url,
                                                 @RequestParam(defaultValue = "1") Integer orden,
                                                 @RequestParam(defaultValue = "true") Boolean visible,
                                                 @RequestPart(name = "archivo", required = false) MultipartFile archivo,
                                                 HttpServletRequest request) {
        ContenidoClase created = service.create(
                claseId,
                new ContenidoClaseService.ContentInput(titulo, tipo, descripcion, url, orden, visible),
                archivo
        );
        created.setUrl(toAbsoluteUrl(created.getUrl(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/api/contenidos/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ContenidoClase update(@PathVariable String id,
                                 @RequestParam String titulo,
                                 @RequestParam String tipo,
                                 @RequestParam(required = false) String descripcion,
                                 @RequestParam(required = false) String url,
                                 @RequestParam(defaultValue = "1") Integer orden,
                                 @RequestParam(defaultValue = "true") Boolean visible,
                                 @RequestPart(name = "archivo", required = false) MultipartFile archivo,
                                 HttpServletRequest request) {
        ContenidoClase out = service.update(
                resolveContenidoId(id),
                new ContenidoClaseService.ContentInput(titulo, tipo, descripcion, url, orden, visible),
                archivo
        );
        out.setUrl(toAbsoluteUrl(out.getUrl(), request));
        return out;
    }

    @DeleteMapping("/api/contenidos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteLogico(resolveContenidoId(id));
        return ResponseEntity.noContent().build();
    }

    private String toAbsoluteUrl(String rawUrl, HttpServletRequest request) {
        String v = rawUrl == null ? "" : rawUrl.trim();
        if (v.isBlank()) return v;
        if (v.startsWith("http://") || v.startsWith("https://")) return v;
        if (v.startsWith("uploads/")) {
            v = "/" + v;
        }
        if (!v.startsWith("/")) return v;
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + v;
    }

    private Long resolveContenidoId(String raw) {
        String in = raw == null ? "" : raw.trim();
        if (in.matches("\\d+")) {
            return Long.parseLong(in);
        }
        // Soporta ids front tipo "<claseId>-CNT-<contenidoId>"
        String[] parts = in.split("-");
        if (parts.length >= 3) {
            String tail = parts[parts.length - 1];
            if (tail.matches("\\d+")) {
                return Long.parseLong(tail);
            }
        }
        throw new IllegalArgumentException("id de contenido invalido: " + raw);
    }
}
