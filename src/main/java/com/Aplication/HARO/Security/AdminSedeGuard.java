package com.Aplication.HARO.Security;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Repository.AdministradorRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Validador de alcance por sede para administradores.
 *
 * Resuelve el administrador autenticado y permite validar si puede consultar o
 * modificar datos de una sede especifica. Una sede vacia se interpreta como
 * administrador global.
 */
@Component
public class AdminSedeGuard {

  /**
   * Contexto resumido del administrador autenticado y su sede.
   */
  public record AdminCtx(Long adminId, String sede, String sedeKey, boolean superAdmin) {}

  private final AdministradorRepository adminRepo;

  public AdminSedeGuard(AdministradorRepository adminRepo) {
    this.adminRepo = adminRepo;
  }

  /**
   * Convierte la autenticacion actual en un contexto de administrador.
   */
  public AdminCtx resolve(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("UNAUTHORIZED");
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof DetallesUsuarioAplicacion ud)) {
      throw new AccessDeniedException("UNAUTHORIZED");
    }
    if (!"ADMIN".equalsIgnoreCase(ud.getRol())) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    if (ud.getId() == null) {
      throw new AccessDeniedException("FORBIDDEN");
    }

    Administrador admin = adminRepo
        .findById(ud.getId())
        .orElseThrow(() -> new NoSuchElementException("Administrador no encontrado: " + ud.getId()));

    String sede = trim(admin.getSede());
    String sedeKey = sedeKey(sede);
    boolean superAdmin = sedeKey.isBlank();
    return new AdminCtx(admin.getId(), sede, sedeKey, superAdmin);
  }

  /**
   * Indica si el contexto de administrador puede operar sobre la sede destino.
   */
  public boolean canAccess(AdminCtx ctx, String targetSede) {
    if (ctx == null || ctx.superAdmin) return true;
    String targetKey = sedeKey(targetSede);
    return !targetKey.isBlank() && ctx.sedeKey.equals(targetKey);
  }

  /**
   * Lanza AccessDeniedException cuando la sede solicitada no pertenece al admin.
   */
  public void assertCanAccess(AdminCtx ctx, String targetSede) {
    if (!canAccess(ctx, targetSede)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
  }

  /**
   * Si el administrador tiene sede asignada, fuerza esa sede en la solicitud.
   * Si es administrador global, conserva la sede solicitada.
   */
  public String enforceRequestSede(AdminCtx ctx, String requestedSede) {
    String req = trim(requestedSede);
    if (ctx == null) return req;

    if (ctx.superAdmin) {
      return req;
    }

    if (req.isBlank()) {
      return ctx.sede;
    }

    if (!ctx.sedeKey.equals(sedeKey(req))) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    return ctx.sede;
  }

  /**
   * Normaliza nombres de sede y aplica alias conocidos para comparaciones seguras.
   */
  public static String sedeKey(String rawSede) {
    String v = trim(rawSede).toLowerCase(Locale.ROOT);
    if (v.isBlank()) return "";

    v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    v = v.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");

    // Alias simples para sedes actuales, tolerantes a variaciones de nombre.
    if (v.contains("eden")) return "eden";
    if (v.contains("kennedy")) return "kennedy";

    return v;
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }
}
