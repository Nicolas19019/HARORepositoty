package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Service.ContenidoClaseService;
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
                                                 @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        ContenidoClase created = service.create(
                claseId,
                new ContenidoClaseService.ContentInput(titulo, tipo, descripcion, url, orden, visible),
                archivo
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/api/contenidos/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ContenidoClase update(@PathVariable Long id,
                                 @RequestParam String titulo,
                                 @RequestParam String tipo,
                                 @RequestParam(required = false) String descripcion,
                                 @RequestParam(required = false) String url,
                                 @RequestParam(defaultValue = "1") Integer orden,
                                 @RequestParam(defaultValue = "true") Boolean visible,
                                 @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return service.update(
                id,
                new ContenidoClaseService.ContentInput(titulo, tipo, descripcion, url, orden, visible),
                archivo
        );
    }

    @DeleteMapping("/api/contenidos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteLogico(id);
        return ResponseEntity.noContent().build();
    }
}

