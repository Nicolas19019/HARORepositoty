// src/main/java/com/Aplication/HARO/Service/ProfesorService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Repository.ProfesorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class ProfesorService {

  private final ProfesorRepository repository;

  public ProfesorService(ProfesorRepository repository) {
    this.repository = repository;
  }

  /* ==========================
     Lecturas
     ========================== */
  @Transactional(readOnly = true)
  public List<Profesor> getAllProfesores() {
    return repository.findByVisibleTrueOrderByIdAsc();
  }

  @Transactional(readOnly = true)
  public Optional<Profesor> getProfesorById(long id) {
    return repository.findByIdAndVisibleTrue(id);
  }

  /* ==========================
     Creación
     ========================== */
  public Profesor createProfesor(Profesor p) {
    // si tu dominio tiene documento/email/usuario y deben ser únicos, valida aquí:
    if (p.getCedula()!= null && existsByDocumento(p.getCedula())) {
      throw new IllegalStateException("Ya existe un profesor con ese documento: " + p.getCedula());
    }
    if (p.getEmail() != null && existsByEmailIgnoreCase(p.getEmail())) {
      throw new IllegalStateException("El correo ya existe: " + p.getEmail());
    }
    if (p.getUsuario() != null && existsByUsuarioIgnoreCase(p.getUsuario())) {
      throw new IllegalStateException("El usuario ya existe: " + p.getUsuario());
    }
    p.setCategoria(normalizarCategoria(p.getCategoria()));
    // En API, todo registro nuevo nace visible.
    p.setVisible(true);
    // setea otros defaults si aplica (activo, estado, etc.)
    return repository.save(p);
  }

  /* ==========================
     Actualización
     ========================== */
  public Profesor updateProfesor(long id, Profesor incoming) {
    Profesor db = repository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));

    // Unicidades si cambian:
    if (incoming.getCedula() != null && !incoming.getCedula().equals(db.getCedula())) {
      if (existsByDocumento(incoming.getCedula())) {
        throw new IllegalStateException("Ya existe un profesor con ese documento: " + incoming.getCedula());
      }
      db.setCedula(incoming.getCedula());
    }
    if (incoming.getEmail() != null && !incoming.getEmail().equalsIgnoreCase(db.getEmail())) {
      if (existsByEmailIgnoreCase(incoming.getEmail())) {
        throw new IllegalStateException("El correo ya existe: " + incoming.getEmail());
      }
      db.setEmail(incoming.getEmail());
    }
    if (incoming.getUsuario() != null && !incoming.getUsuario().equalsIgnoreCase(db.getUsuario())) {
      if (existsByUsuarioIgnoreCase(incoming.getUsuario())) {
        throw new IllegalStateException("El usuario ya existe: " + incoming.getUsuario());
      }
      db.setUsuario(incoming.getUsuario());
    }

    // Otros campos simples
    if (incoming.getNombre() != null) db.setNombre(incoming.getNombre());
    if (incoming.getApellido() != null) db.setApellido(incoming.getApellido());
    if (incoming.getTelefono() != null) db.setTelefono(incoming.getTelefono());
    if (incoming.getEspecialidad() != null) db.setEspecialidad(incoming.getEspecialidad());
    if (incoming.getCategoria() != null) db.setCategoria(normalizarCategoria(incoming.getCategoria()));
    if (incoming.getVisible() != null) db.setVisible(incoming.getVisible());
   

    return repository.save(db);
  }

  /* ==========================
     Eliminación
     ========================== */
  public void deleteProfesor(long id) {
    Profesor p = repository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Profesor no encontrado: " + id));
    p.setVisible(false);
    repository.save(p);
  }

  /* ==========================
     Helpers de unicidad (declara en tu Repository)
     ========================== */
  private boolean existsByDocumento(String documento) {
    try {
      // define en el repo: boolean existsByDocumento(String documento);
      return repository.existsByCedula(documento);
    } catch (Exception e) {
      return false; // si no está implementado, no rompe; pero es mejor crearlo.
    }
  }

  private boolean existsByEmailIgnoreCase(String email) {
    try {
      // define en el repo: boolean existsByEmailIgnoreCase(String email);
      return repository.existsByEmailIgnoreCase(email);
    } catch (Exception e) {
      return false;
    }
  }

  private boolean existsByUsuarioIgnoreCase(String usuario) {
    try {
      // define en el repo: boolean existsByUsuarioIgnoreCase(String usuario);
      return repository.existsByUsuarioIgnoreCase(usuario);
    } catch (Exception e) {
      return false;
    }
  }

  private String normalizarCategoria(String raw) {
    String c = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (c.isBlank()) {
      throw new IllegalArgumentException("La categoria del instructor es obligatoria (carro o moto).");
    }
    if (!"carro".equals(c) && !"moto".equals(c)) {
      throw new IllegalArgumentException("La categoria del instructor solo permite: carro o moto.");
    }
    return c;
  }

}
