// src/main/java/com/Aplication/HARO/Service/EstudianteService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

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
    return repo.findByVisibleTrueOrderByIdAsc();
  }

  @Transactional(readOnly = true)
  public Optional<Estudiante> getEstudianteById(long id) {
    return repo.findByIdAndVisibleTrue(id);
  }

  @Transactional(readOnly = true)
  public Optional<Estudiante> buscarPorNumeroDocumento(String numeroDocumento) {
    return repo.findByNumeroDocumentoAndVisibleTrue(numeroDocumento);
  }

  @Transactional(readOnly = true)
  public Optional<Estudiante> buscarPorCorreo(String email) {
    String normalizado = email == null ? "" : email.trim();
    return repo.findByEmailNormalizadoVisible(normalizado);
  }

  @Transactional(readOnly = true)
  public boolean existePorNumeroDocumento(String numeroDocumento) {
    return repo.existsByNumeroDocumento(numeroDocumento);
  }

  @Transactional(readOnly = true)
  public boolean existePorCorreo(String email) {
    String normalizado = email == null ? "" : email.trim();
    if (normalizado.isBlank()) return false;
    return repo.findByEmailNormalizadoVisible(normalizado).isPresent();
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
    // Horas del curso
    if (in.getHoras() != null && in.getHoras() < 0) {
      throw new IllegalArgumentException("Las horas no pueden ser negativas.");
    }
    if (in.getHoras() == null) {
      in.setHoras(0);
    }

    // Tipo de pase: carro, moto o carro,moto
    in.setTipoPase(normalizarTipoPase(in.getTipoPase()));

    // Estado del examen teorico (false por defecto)
    if (in.getAproboExamenTeorico() == null) {
      in.setAproboExamenTeorico(false);
    }

    // En API, todo registro nuevo nace visible.
    in.setVisible(true);
    in.setFotoPerfil(normalizarFotoPerfil(in.getFotoPerfil()));

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
    if (incoming.getFotoPerfil() != null) db.setFotoPerfil(normalizarFotoPerfil(incoming.getFotoPerfil()));
    if (incoming.getCategoria() != null) db.setCategoria(incoming.getCategoria());
    if (incoming.getTipoEstudiante() != null) db.setTipoEstudiante(incoming.getTipoEstudiante());
    if (incoming.getEstado() != null) db.setEstado(incoming.getEstado());
    if (incoming.getSede() != null) db.setSede(incoming.getSede());
    if (incoming.getHoras() != null) {
      if (incoming.getHoras() < 0) {
        throw new IllegalArgumentException("Las horas no pueden ser negativas.");
      }
      db.setHoras(incoming.getHoras());
    }
    if (incoming.getTipoPase() != null) {
      db.setTipoPase(normalizarTipoPase(incoming.getTipoPase()));
    }
    if (incoming.getAproboExamenTeorico() != null) {
      db.setAproboExamenTeorico(incoming.getAproboExamenTeorico());
    }
    if (incoming.getVisible() != null) {
      db.setVisible(incoming.getVisible());
    }

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
    Estudiante e = repo.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
    e.setVisible(false);
    repo.save(e);
  }

  /* ==========================
     Helpers
     ========================== */
  private boolean esBcrypt(String s) {
    return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
  }

  private String normalizarTipoPase(String raw) {
    if (raw == null) return null;
    String input = raw.trim().toLowerCase(Locale.ROOT);
    if (input.isBlank()) return null;

    Set<String> tipos = new LinkedHashSet<>();
    for (String token : input.split("[,;/|\\s]+")) {
      if (token.isBlank()) continue;
      if (!"carro".equals(token) && !"moto".equals(token)) {
        throw new IllegalArgumentException("tipoPase solo permite: carro, moto o carro,moto");
      }
      tipos.add(token);
    }

    if (tipos.isEmpty()) return null;
    boolean carro = tipos.contains("carro");
    boolean moto = tipos.contains("moto");
    if (carro && moto) return "carro,moto";
    return carro ? "carro" : "moto";
  }

  private String normalizarFotoPerfil(String raw) {
    if (raw == null) return null;
    String v = raw.trim();
    if (v.isBlank()) return null;
    if (!v.startsWith("data:image/")) {
      throw new IllegalArgumentException("fotoPerfil debe ser una imagen en formato data URL");
    }
    if (v.length() > 2_000_000) {
      throw new IllegalArgumentException("fotoPerfil excede el tamaño permitido");
    }
    return v;
  }
}
