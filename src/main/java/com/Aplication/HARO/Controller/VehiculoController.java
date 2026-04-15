package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Security.AdminSedeGuard;
import com.Aplication.HARO.Service.VehiculoService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controlador REST para vehiculo.
 */
@RestController
@RequestMapping("/api/vehiculos")
@CrossOrigin(origins = "*")
public class VehiculoController {

    private final VehiculoService service;
    private final AdminSedeGuard adminSedeGuard;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public VehiculoController(VehiculoService service, AdminSedeGuard adminSedeGuard) {
        this.service = service;
        this.adminSedeGuard = adminSedeGuard;
    }

/**
 * Lista los registros de vehiculo.
 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Vehiculo> getAll(Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        List<Vehiculo> out = service.getAllVehiculos();
        if (!adminCtx.superAdmin()) {
            out = out.stream()
                    .filter(v -> adminSedeGuard.canAccess(adminCtx, v.getSede()))
                    .toList();
        }
        return out;
    }

/**
 * Consulta un vehiculo por placa respetando las restricciones de sede.
 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{placa}")
    public Vehiculo getByPlaca(@PathVariable String placa, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        return service.getVehiculoByPlaca(placa)
                .map(v -> {
                    adminSedeGuard.assertCanAccess(adminCtx, v.getSede());
                    return v;
                })
                .orElseThrow(() -> new NoSuchElementException("VehÃ­culo no encontrado: " + placa));
    }

/**
 * Crea un nuevo registro de vehiculo.
 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Vehiculo> create(@RequestBody Vehiculo v, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        v.setSede(adminSedeGuard.enforceRequestSede(adminCtx, v.getSede()));
        Vehiculo created = service.createVehiculo(v);
        return ResponseEntity.created(URI.create("/api/vehiculos/" + created.getPlaca())).body(created);
    }

/**
 * Actualiza un registro existente de vehiculo.
 */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{placa}")
    public Vehiculo update(@PathVariable String placa, @RequestBody Vehiculo v, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Vehiculo current = service.getVehiculoByPlaca(placa)
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + placa));
        adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
        if (v.getSede() != null) {
            v.setSede(adminSedeGuard.enforceRequestSede(adminCtx, v.getSede()));
        }
        v.setPlaca(placa);
        return service.updateVehiculo(v);
    }

/**
 * Elimina un registro de vehiculo.
 */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{placa}")
    public ResponseEntity<Void> delete(@PathVariable String placa, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        Vehiculo current = service.getVehiculoByPlaca(placa)
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + placa));
        adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
        service.deleteVehiculo(placa);
        return ResponseEntity.noContent().build();
    }
}
