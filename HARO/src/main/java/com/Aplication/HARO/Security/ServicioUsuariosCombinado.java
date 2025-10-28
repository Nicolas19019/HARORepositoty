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

  @Override
  @Transactional
  public DetallesUsuarioAplicacion loadUserByUsername(String login) throws UsernameNotFoundException {
    // ADMIN
    Optional<Administrador> admin = adminRepo.findByUsuarioIgnoreCase(login);
    if (admin.isEmpty()) admin = adminRepo.findByCorreoIgnoreCase(login);
    if (admin.isPresent()) {
      var a = admin.get();
      return new DetallesUsuarioAplicacion(a.getId(), login, a.getContrasenaHash(), "ADMIN", a.getActivo());
    }

    // PROFESOR
    Optional<Profesor> prof = profRepo.findByUsuarioIgnoreCase(login);
    if (prof.isEmpty()) prof = profRepo.findByEmailIgnoreCase(login);
    if (prof.isEmpty()) prof = profRepo.findByCorreoIgnoreCase(login);
    if (prof.isEmpty()) prof = profRepo.findByCedula(login);
    if (prof.isPresent()) {
      var p = prof.get();
      String hash = p.getContrasena();
      if (hash != null && !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
        hash = encoder.encode(hash);
        p.setContrasena(hash);
        profRepo.save(p);
      }
      if (hash == null) hash = encoder.encode("cambiar123*");
      boolean activo = true;
      return new DetallesUsuarioAplicacion(p.getId(), login, hash, "PROFESOR", activo);
    }

    // ESTUDIANTE
    Optional<Estudiante> est = estRepo.findByUsuarioIgnoreCase(login);
    if (est.isEmpty()) est = estRepo.findByEmailIgnoreCase(login);
    if (est.isEmpty()) est = estRepo.findByNumeroDocumento(login);
    if (est.isPresent()) {
      var e = est.get();
      String hash = e.getContrasena();
      if (hash != null && !hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
        hash = encoder.encode(hash);
        e.setContrasena(hash);
        estRepo.save(e);
      }
      if (hash == null) hash = encoder.encode("cambiar123*");
      boolean activo = !"INACTIVO".equalsIgnoreCase(e.getEstado());
      return new DetallesUsuarioAplicacion(e.getId(), login, hash, "ESTUDIANTE", activo);
    }

    throw new UsernameNotFoundException("Usuario no encontrado: " + login);
  }
}
