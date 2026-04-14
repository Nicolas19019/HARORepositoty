// src/main/java/com/Aplication/HARO/Security/ServicioUsuariosCombinado.java
package com.Aplication.HARO.Security;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Repository.AdministradorRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Servicio de carga de usuarios para Spring Security.
 *
 * Resuelve un mismo login contra administradores, profesores y estudiantes, y
 * normaliza contrasenas heredadas guardandolas como BCrypt cuando corresponde.
 */
@Service
public class ServicioUsuariosCombinado implements UserDetailsService {

  private final EstudianteRepository estRepo;
  private final ProfesorRepository profRepo;
  private final AdministradorRepository adminRepo;
  private final PasswordEncoder encoder;


public ServicioUsuariosCombinado(EstudianteRepository estRepo,
                                 ProfesorRepository profRepo,
                                 AdministradorRepository adminRepo,
                                 @Qualifier("passwordEncoder") PasswordEncoder encoder) {
  this.estRepo = estRepo;
  this.profRepo = profRepo;
  this.adminRepo = adminRepo;
  this.encoder = encoder;
}

  /**
   * Busca el usuario autenticable por login y lo adapta a UserDetails.
   */
  @Override
  @Transactional
  public DetallesUsuarioAplicacion loadUserByUsername(String login) throws UsernameNotFoundException {
    String raw = login == null ? "" : login.trim();

    // ADMIN
    Optional<Administrador> admin = adminRepo.findByCorreoNormalizado(raw);
    if (admin.isEmpty()) admin = adminRepo.findByUsuarioIgnoreCase(raw);
    if (admin.isEmpty()) admin = adminRepo.findByCorreoIgnoreCase(raw);
    if (admin.isPresent()) {
      var a = admin.get();
      return new DetallesUsuarioAplicacion(a.getId(), raw, a.getContrasenaHash(), "ADMIN", a.getActivo());
    }

    // PROFESOR
    Optional<Profesor> prof = profRepo.findByUsuarioIgnoreCase(raw);
    if (prof.isEmpty()) prof = profRepo.findByEmailIgnoreCase(raw);
    if (prof.isEmpty()) prof = profRepo.findByCorreoIgnoreCase(raw);
    if (prof.isEmpty()) prof = profRepo.findByCedula(raw);
    if (prof.isPresent()) {
      var p = prof.get();
      String hash = p.getContrasena();
      if (hash != null && !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
        hash = encoder.encode(hash);
        p.setContrasena(hash);
        profRepo.save(p);
      }
      if (hash == null) hash = encoder.encode("cambiar123*");
      boolean activo = !Boolean.FALSE.equals(p.getVisible());
      return new DetallesUsuarioAplicacion(p.getId(), raw, hash, "PROFESOR", activo);
    }

    // ESTUDIANTE
    Optional<Estudiante> est = estRepo.findByEmailNormalizado(raw);
    if (est.isEmpty()) est = estRepo.findByUsuarioIgnoreCase(raw);
    if (est.isEmpty()) est = estRepo.findByEmailIgnoreCase(raw);
    if (est.isEmpty()) est = estRepo.findByNumeroDocumento(raw);
    if (est.isPresent()) {
      var e = est.get();
      String hash = e.getContrasena();
      if (hash != null && !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
        hash = encoder.encode(hash);
        e.setContrasena(hash);
        estRepo.save(e);
      }
      if (hash == null) hash = encoder.encode("cambiar123*");
      boolean activo = !Boolean.FALSE.equals(e.getVisible()) && !"INACTIVO".equalsIgnoreCase(e.getEstado());
      return new DetallesUsuarioAplicacion(e.getId(), raw, hash, "ESTUDIANTE", activo);
    }

    throw new UsernameNotFoundException("Usuario no encontrado: " + raw);
  }
}
