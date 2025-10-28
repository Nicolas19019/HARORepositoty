// src/main/java/com/Aplication/HARO/Service/AdministradorService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Repository.AdministradorRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

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
  @Transactional(readOnly = true)
  public List<Administrador> listar() { return repo.findAll(); }

  @Transactional(readOnly = true)
  public Administrador obtenerPorId(Long id) {
    return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado: " + id));
  }

  public Administrador crear(Administrador in) {
    if (in.getUsuario() != null && repo.existsByUsuarioIgnoreCase(in.getUsuario()))
      throw new IllegalStateException("El usuario ya existe: " + in.getUsuario());
    if (in.getCorreo() != null && repo.existsByCorreoIgnoreCase(in.getCorreo()))
      throw new IllegalStateException("El correo ya existe: " + in.getCorreo());

    String rawOrHash = in.getContrasenaHash();
    if (rawOrHash == null || rawOrHash.isBlank()) rawOrHash = "Cambiar123*";
    if (!esBcrypt(rawOrHash)) in.setContrasenaHash(encoder.encode(rawOrHash));
    if (in.getActivo() == null) in.setActivo(true);

    return repo.save(in);
  }

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
    if (in.getActivo() != null) actual.setActivo(in.getActivo());

    if (in.getContrasenaHash() != null && !in.getContrasenaHash().isBlank()) {
      actual.setContrasenaHash(esBcrypt(in.getContrasenaHash())
        ? in.getContrasenaHash()
        : encoder.encode(in.getContrasenaHash()));
    }
    return repo.save(actual);
  }

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

  public void activar(Long id) { var a = obtenerPorId(id); a.setActivo(true); repo.save(a); }
  public void desactivar(Long id) { var a = obtenerPorId(id); a.setActivo(false); repo.save(a); }
  public void eliminar(Long id) { if (!repo.existsById(id)) throw new IllegalArgumentException("No existe"); repo.deleteById(id); }

  private boolean esBcrypt(String s) { return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$")); }
}
