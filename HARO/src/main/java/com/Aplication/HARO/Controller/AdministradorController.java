// com/Aplication/HARO/Controller/AdministradorController.java
package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Service.AdministradorService;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/administradores")
@CrossOrigin(origins = "*")
public class AdministradorController {

  private final AdministradorService service;

  public AdministradorController(AdministradorService service) {
    this.service = service;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public List<Administrador> listar() {
    return service.listar();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{id}")
  public Administrador obtener(@PathVariable Long id) {
    return service.obtenerPorId(id);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<Administrador> crear(@RequestBody Administrador in) {
    var creado = service.crear(in);
    return ResponseEntity.created(URI.create("/api/administradores/" + creado.getId())).body(creado);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public Administrador actualizar(@PathVariable Long id, @RequestBody Administrador in) {
    return service.actualizar(id, in);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}/password")
  public ResponseEntity<Void> cambiarPassword(@PathVariable Long id, @RequestBody String nueva) {
    service.cambiarContrasena(id, nueva);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}/activar")
  public ResponseEntity<Void> activar(@PathVariable Long id) {
    service.activar(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}/desactivar")
  public ResponseEntity<Void> desactivar(@PathVariable Long id) {
    service.desactivar(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable Long id) {
    service.eliminar(id);
    return ResponseEntity.noContent().build();
  }
}
