// src/main/java/com/Aplication/HARO/Service/AdministradorService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Repository.AdministradorRepository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Servicio de negocio para administradores.
 *
 * Centraliza listado, creacion, actualizacion, activacion y cambios de
 * contrasena de cuentas administrativas.
 */
@Service
@Transactional
public class AdministradorService {

  private final AdministradorRepository repo;
  private final PasswordEncoder encoder;

  public AdministradorService(AdministradorRepository repo,
          @Qualifier("passwordEncoder") PasswordEncoder encoder) {
this.repo = repo;
this.encoder = encoder;
}
  /**
   * Lista los registros solicitados segun los filtros recibidos.
   */
  @Transactional(readOnly = true)
  public List<Administrador> listar() { return repo.findAll(); }

  /**
   * Obtiene el registro solicitado por identificador o criterio de busqueda.
   */
  @Transactional(readOnly = true)
  public Administrador obtenerPorId(Long id) {
    return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado: " + id));
  }

  /**
   * Crea o registra la informacion recibida aplicando las validaciones del servicio.
   */
  public Administrador crear(Administrador in) {
    if (in.getUsuario() != null && repo.existsByUsuarioIgnoreCase(in.getUsuario()))
      throw new IllegalStateException("El usuario ya existe: " + in.getUsuario());
    if (in.getCorreo() != null && repo.existsByCorreoIgnoreCase(in.getCorreo()))
      throw new IllegalStateException("El correo ya existe: " + in.getCorreo());

    String rawOrHash = in.getContrasenaHash();
    if (rawOrHash == null || rawOrHash.isBlank()) rawOrHash = "Cambiar123*";
    if (!esBcrypt(rawOrHash)) in.setContrasenaHash(encoder.encode(rawOrHash));
    if (in.getActivo() == null) in.setActivo(true);
    in.setSede(normalizarSede(in.getSede()));

    return repo.save(in);
  }

  /**
   * Actualiza el registro existente con los datos permitidos.
   */
  public Administrador actualizar(Long id, Administrador in) {
    var actual = obtenerPorId(id);
    if (in.getUsuario() != null && !in.getUsuario().equalsIgnoreCase(actual.getUsuario())) {
      if (repo.existsByUsuarioIgnoreCase(in.getUsuario()))
        throw new IllegalStateException("El usuario ya existe: " + in.getUsuario());
      actual.setUsuario(in.getUsuario());
    }
    if (in.getCorreo() != null && !in.getCorreo().equalsIgnoreCase(actual.getCorreo())) {
      if (repo.existsByCorreoIgnoreCase(in.getCorreo()))
        throw new IllegalStateException("El correo ya existe: " + in.getCorreo());
      actual.setCorreo(in.getCorreo());
    }
    if (in.getNombre() != null) actual.setNombre(in.getNombre());
    if (in.getSede() != null) actual.setSede(normalizarSede(in.getSede()));
    if (in.getActivo() != null) actual.setActivo(in.getActivo());

    if (in.getContrasenaHash() != null && !in.getContrasenaHash().isBlank()) {
      actual.setContrasenaHash(esBcrypt(in.getContrasenaHash())
        ? in.getContrasenaHash()
        : encoder.encode(in.getContrasenaHash()));
    }
    return repo.save(actual);
  }

  /**
   * Actualiza la contrasena del usuario aplicando el encoder configurado.
   */
  public void cambiarContrasena(Long id, String nueva) {
    if (nueva == null) nueva = "";
    nueva = nueva.trim();
    if (nueva.startsWith("\"") && nueva.endsWith("\"") && nueva.length() >= 2)
      nueva = nueva.substring(1, nueva.length() - 1);
    if (nueva.isBlank()) throw new IllegalArgumentException("La nueva contraseña no puede estar vacía");

    var a = obtenerPorId(id);
    a.setContrasenaHash(encoder.encode(nueva));
    repo.save(a);
  }

  /**
   * Valida que la contrasena actual coincida con el hash guardado.
   */
  @Transactional(readOnly = true)
  public boolean validarContrasenaActual(Long id, String actual) {
    if (actual == null || actual.isBlank()) return false;
    Administrador admin = obtenerPorId(id);
    String hash = admin.getContrasenaHash();
    return hash != null && encoder.matches(actual, hash);
  }

  /**
   * Actualiza la contrasena del usuario aplicando el encoder configurado.
   */
  public void cambiarContrasenaAutenticado(Long id, String nueva) {
    if (nueva == null) nueva = "";
    nueva = nueva.trim();
    if (nueva.isBlank()) throw new IllegalArgumentException("La nueva contrasena no puede estar vacia");

    Administrador admin = obtenerPorId(id);
    admin.setContrasenaHash(encoder.encode(nueva));
    repo.save(admin);
  }

  /**
   * Activa el registro o la cuenta indicada.
   */
  public void activar(Long id) { var a = obtenerPorId(id); a.setActivo(true); repo.save(a); }
  /**
   * Desactiva el registro o la cuenta indicada.
   */
  public void desactivar(Long id) { var a = obtenerPorId(id); a.setActivo(false); repo.save(a); }
  /**
   * Elimina o desactiva el registro segun la regla del servicio.
   */
  public void eliminar(Long id) {
    try {
      repo.deleteById(id);
    } catch (EmptyResultDataAccessException ex) {
      throw new IllegalArgumentException("No existe");
    }
  }

  /**
   * Normaliza el valor recibido para usarlo de forma consistente.
   */
  private String normalizarSede(String raw) {
    if (raw == null) return null;
    String sede = raw.trim().replaceAll("\\s+", " ");
    return sede.isBlank() ? null : sede;
  }

  private boolean esBcrypt(String s) { return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$")); }
}
