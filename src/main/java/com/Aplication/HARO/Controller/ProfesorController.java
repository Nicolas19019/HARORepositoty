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

@RestController
@RequestMapping("/api/profesores")
@CrossOrigin(origins = "*")
public class ProfesorController {

    private final ProfesorService service;
    private final AdminSedeGuard adminSedeGuard;

    public ProfesorController(ProfesorService service, AdminSedeGuard adminSedeGuard) {
        this.service = service;
        this.adminSedeGuard = adminSedeGuard;
    }

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

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Profesor getById(@PathVariable int id, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Profesor p = service.getProfesorById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
        adminSedeGuard.assertCanAccess(adminCtx, p.getSede());
        return p;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Profesor> create(@RequestBody Profesor p, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        p.setSede(adminSedeGuard.enforceRequestSede(adminCtx, p.getSede()));
        Profesor created = service.createProfesor(p);
        return ResponseEntity.created(URI.create("/api/profesores/" + created.getId())).body(created);
    }

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
