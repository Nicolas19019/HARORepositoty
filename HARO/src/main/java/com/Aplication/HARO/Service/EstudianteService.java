// src/main/java/com/Aplication/HARO/Service/EstudianteService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class EstudianteService {

  private final EstudianteRepository repo;
  private final PasswordEncoder encoder;

  public EstudianteService(EstudianteRepository repo,
                           @Qualifier("passwordEncoder") PasswordEncoder encoder) {
    this.repo = repo;
    this.encoder = encoder;
  }

  /* ==========================
     Lecturas
     ========================== */
  @Transactional(readOnly = true)
  public List<Estudiante> getAllEstudiantes() {
    return repo.findAll();
  }

  @Transactional(readOnly = true)
  public Optional<Estudiante> getEstudianteById(long id) {
    return repo.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<Estudiante> buscarPorNumeroDocumento(String numeroDocumento) {
    return repo.findByNumeroDocumento(numeroDocumento);
  }

  @Transactional(readOnly = true)
  public boolean existePorNumeroDocumento(String numeroDocumento) {
    return repo.existsByNumeroDocumento(numeroDocumento);
  }

  /* ==========================
     Creación
     ========================== */
  public Estudiante createEstudiante(Estudiante in) {
    // por si llega con id desde el front
    in.setId(null);

    // Validaciones de unicidad si vienen set
    if (in.getNumeroDocumento() != null && repo.existsByNumeroDocumento(in.getNumeroDocumento())) {
      throw new IllegalStateException("Ya existe un estudiante con ese número de documento: " + in.getNumeroDocumento());
    }
    if (in.getUsuario() != null && repo.existsByUsuarioIgnoreCase(in.getUsuario())) {
      throw new IllegalStateException("El usuario ya existe: " + in.getUsuario());
    }
    if (in.getEmail() != null && repo.existsByEmailIgnoreCase(in.getEmail())) {
      throw new IllegalStateException("El correo ya existe: " + in.getEmail());
    }

    // Tipo de estudiante por defecto si viene vacío (prospecto)
    if (in.getTipoEstudiante() == null || in.getTipoEstudiante().isBlank()) {
      in.setTipoEstudiante("prospecto");
    }

    // Estado por defecto (si manejas estado); opcional
    if (in.getEstado() == null || in.getEstado().isBlank()) {
      in.setEstado("Pendiente"); // ajusta si tu dominio requiere otro valor
    }

    // Contraseña:
    // El front podría enviar en claro o ya hasheada. Si NO es bcrypt, se hashea aquí.
    String rawOrHash = in.getContrasena();
    if (rawOrHash == null || rawOrHash.isBlank()) {
      rawOrHash = "Cambiar123*"; // fallback seguro mínimo
    }
    if (!esBcrypt(rawOrHash)) {
      in.setContrasena(encoder.encode(rawOrHash));
    } else {
      in.setContrasena(rawOrHash);
    }

    return repo.save(in);
  }

  /* ==========================
     Actualización
     ========================== */
  public Estudiante updateEstudiante(long id, Estudiante incoming) {
    Estudiante db = repo.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));

    // Usuario (validar cambio con unicidad)
    if (incoming.getUsuario() != null && !incoming.getUsuario().equalsIgnoreCase(db.getUsuario())) {
      if (repo.existsByUsuarioIgnoreCase(incoming.getUsuario())) {
        throw new IllegalStateException("El usuario ya existe: " + incoming.getUsuario());
      }
      db.setUsuario(incoming.getUsuario());
    }

    // Email (validar cambio con unicidad)
    if (incoming.getEmail() != null && !incoming.getEmail().equalsIgnoreCase(db.getEmail())) {
      if (repo.existsByEmailIgnoreCase(incoming.getEmail())) {
        throw new IllegalStateException("El correo ya existe: " + incoming.getEmail());
      }
      db.setEmail(incoming.getEmail());
    }

    // Documento (si lo permites editar)
    if (incoming.getNumeroDocumento() != null
        && !incoming.getNumeroDocumento().equals(db.getNumeroDocumento())) {
      if (repo.existsByNumeroDocumento(incoming.getNumeroDocumento())) {
        throw new IllegalStateException("Ya existe un estudiante con ese número de documento: "
            + incoming.getNumeroDocumento());
      }
      db.setNumeroDocumento(incoming.getNumeroDocumento());
    }

    // Otros campos simples (solo si vienen)
    if (incoming.getNombre() != null) db.setNombre(incoming.getNombre());
    if (incoming.getApellido() != null) db.setApellido(incoming.getApellido());
    if (incoming.getTipoDocumento() != null) db.setTipoDocumento(incoming.getTipoDocumento());
    if (incoming.getTelefono() != null) db.setTelefono(incoming.getTelefono());
    if (incoming.getDireccion() != null) db.setDireccion(incoming.getDireccion());
    if (incoming.getCategoria() != null) db.setCategoria(incoming.getCategoria());
    if (incoming.getTipoEstudiante() != null) db.setTipoEstudiante(incoming.getTipoEstudiante());
    if (incoming.getEstado() != null) db.setEstado(incoming.getEstado());

    // Contraseña: si llega algo, se procesa como en crear()
    if (incoming.getContrasena() != null && !incoming.getContrasena().isBlank()) {
      db.setContrasena(esBcrypt(incoming.getContrasena())
          ? incoming.getContrasena()
          : encoder.encode(incoming.getContrasena()));
    }

    return repo.save(db);
  }

  /* ==========================
     Eliminación
     ========================== */
  public void deleteEstudiante(long id) {
    if (!repo.existsById(id)) {
      throw new NoSuchElementException("Estudiante no encontrado: " + id);
    }
    repo.deleteById(id);
  }

  /* ==========================
     Helpers
     ========================== */
  private boolean esBcrypt(String s) {
    return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
  }
}
