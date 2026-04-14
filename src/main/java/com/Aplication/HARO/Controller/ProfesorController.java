package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Security.AdminSedeGuard;
import com.Aplication.HARO.Service.ProfesorService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controlador REST para profesor.
 */
@RestController
@RequestMapping("/api/profesores")
@CrossOrigin(origins = "*")
public class ProfesorController {

    private final ProfesorService service;
    private final AdminSedeGuard adminSedeGuard;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public ProfesorController(ProfesorService service, AdminSedeGuard adminSedeGuard) {
        this.service = service;
        this.adminSedeGuard = adminSedeGuard;
    }

/**
 * Lista los registros de profesor.
 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Profesor> getAll(Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        List<Profesor> out = service.getAllProfesores();
        if (!adminCtx.superAdmin()) {
            out = out.stream()
                    .filter(p -> adminSedeGuard.canAccess(adminCtx, p.getSede()))
                    .toList();
        }
        return out;
    }

/**
 * Obtiene un registro de profesor por su identificador.
 */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Profesor getById(@PathVariable int id, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Profesor p = service.getProfesorById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
        adminSedeGuard.assertCanAccess(adminCtx, p.getSede());
        return p;
    }

/**
 * Crea un nuevo registro de profesor.
 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Profesor> create(@RequestBody Profesor p, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        p.setSede(adminSedeGuard.enforceRequestSede(adminCtx, p.getSede()));
        Profesor created = service.createProfesor(p);
        return ResponseEntity.created(URI.create("/api/profesores/" + created.getId())).body(created);
    }

/**
 * Actualiza un registro existente de profesor.
 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Profesor update(@PathVariable long id, @RequestBody Profesor p, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Profesor current = service.getProfesorById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
        adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
        if (p.getSede() != null) {
            p.setSede(adminSedeGuard.enforceRequestSede(adminCtx, p.getSede()));
        }
        p.setId(id);
        return service.updateProfesor(id, p);
    }

/**
 * Elimina un registro de profesor.
 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable int id, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Profesor current = service.getProfesorById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
        adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
        service.deleteProfesor(id);
        return ResponseEntity.noContent().build();
    }
}
