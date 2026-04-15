package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Security.ServicioJwt;
import com.Aplication.HARO.Security.ServicioUsuariosCombinado;
import com.Aplication.HARO.Service.AdminPasswordRecoveryService;
import com.Aplication.HARO.Service.AdministradorService;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import com.Aplication.HARO.Service.EstudianteService;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/* === Usa records exactamente como en tu clase original, sin anadir mas archivos === */
record PeticionInicioSesion(
    @JsonAlias({"correo", "email", "usuario"}) String login,
    @JsonAlias({"contrasena", "password"}) String password
) {}
record PeticionRefresco(String tokenRefresco) {}
record RespuestaTokens(String tokenAcceso, String tokenRefresco, String rol, Long uid,
                       long accesoExpiraEnSeg, long refrescoExpiraEnSeg) {}
record PeticionForgotPassword(
    @JsonAlias({"correo", "email"}) String correo
) {}
record PeticionForgotPasswordVerify(
    @JsonAlias({"correo", "email"}) String correo,
    @JsonAlias({"codigo", "code"}) String code
) {}
record PeticionResetPassword(
    @JsonAlias({"correo", "email"}) String correo,
    @JsonAlias({"codigo", "code"}) String code,
    @JsonAlias({"nuevaContrasena", "newPassword"}) String nuevaContrasena
) {}
record PeticionCambioPassword(
    @JsonAlias({"currentPassword", "contrasenaActual"}) String currentPassword,
    @JsonAlias({"newPassword", "nuevaContrasena"}) String newPassword,
    @JsonAlias({"confirmPassword", "confirmarContrasena"}) String confirmPassword
) {}

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AutenticacionController {
  private static final Logger log = LoggerFactory.getLogger(AutenticacionController.class);
  private static final Pattern EMAIL_REGEX =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final AuthenticationManager authManager;
  private final ServicioUsuariosCombinado usuarios;
  private final ServicioJwt jwt;
  private final EstudianteModuloAccesoService estudianteModuloAccesoService;
  private final AdminPasswordRecoveryService adminPasswordRecoveryService;
  private final AdministradorService administradorService;
  private final EstudianteService estudianteService;

  public AutenticacionController(AuthenticationManager authManager,
                                 ServicioUsuariosCombinado usuarios,
                                 ServicioJwt jwt,
                                 EstudianteModuloAccesoService estudianteModuloAccesoService,
                                 AdminPasswordRecoveryService adminPasswordRecoveryService,
                                 AdministradorService administradorService,
                                 EstudianteService estudianteService) {
    this.authManager = authManager;
    this.usuarios = usuarios;
    this.jwt = jwt;
    this.estudianteModuloAccesoService = estudianteModuloAccesoService;
    this.adminPasswordRecoveryService = adminPasswordRecoveryService;
    this.administradorService = administradorService;
    this.estudianteService = estudianteService;
  }

  private boolean rolPermitido(String rol) {
    return "ESTUDIANTE".equalsIgnoreCase(rol) || "ADMIN".equalsIgnoreCase(rol);
  }

  private String normalizarCorreo(String correo) {
    return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizePassword(String raw, String fieldName) {
    String password = raw == null ? "" : raw.trim();
    if (password.isBlank()) {
      throw new IllegalArgumentException(fieldName + " es requerida");
    }
    return password;
  }

  private void validatePasswordPolicy(String password) {
    if (password.length() < 8) {
      throw new IllegalArgumentException("La nueva contrasena debe tener al menos 8 caracteres");
    }
    if (!password.matches(".*\\d.*")) {
      throw new IllegalArgumentException("La nueva contrasena debe incluir al menos un numero");
    }
    if (!password.matches(".*[^A-Za-z0-9].*")) {
      throw new IllegalArgumentException("La nueva contrasena debe incluir al menos un simbolo");
    }
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody(required = false) PeticionInicioSesion req) {
    try {
      String login = normalizarCorreo(req == null ? null : req.login());
      String password = req == null ? null : req.password();

      if (login.isBlank() || password == null || password.isBlank()) {
        return ResponseEntity.badRequest().body("Debes enviar login y password");
      }
      if (!EMAIL_REGEX.matcher(login).matches()) {
        return ResponseEntity.badRequest().body("Debes iniciar sesion con correo y contrasena");
      }

      var auth = new UsernamePasswordAuthenticationToken(login, password);
      authManager.authenticate(auth);

      var ud = (DetallesUsuarioAplicacion) usuarios.loadUserByUsername(login);
      if (!rolPermitido(ud.getRol())) {
        return ResponseEntity.status(403).body("Solo esta habilitado el acceso para estudiantes y administrativos");
      }

      if ("ESTUDIANTE".equalsIgnoreCase(ud.getRol()) && ud.getId() != null) {
        try {
          estudianteModuloAccesoService.registrarIngresoPorEstudiante(ud.getId());
        } catch (Exception ex) {
          log.warn("No se pudo registrar ingreso al modulo para estudiante {}: {}", ud.getId(), ex.getMessage());
        }
      }

      String at = jwt.emitirTokenAcceso(ud.getUsername(), ud.getRol(), ud.getId(), null);
      String rt = jwt.emitirTokenRefresco(ud.getUsername());

      return ResponseEntity.ok(
          new RespuestaTokens(
              at, rt, ud.getRol(), ud.getId(),
              jwt.getAccessTtlSeconds(), jwt.getRefreshTtlSeconds()));
    } catch (DisabledException e) {
      return ResponseEntity.status(403).body("Usuario inactivo o bloqueado");
    } catch (BadCredentialsException e) {
      return ResponseEntity.status(401).body("Credenciales invalidas");
    } catch (AuthenticationException e) {
      return ResponseEntity.status(401).body("Credenciales invalidas");
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody(required = false) PeticionRefresco req) {
    try {
      if (req == null || req.tokenRefresco() == null || req.tokenRefresco().isBlank()) {
        return ResponseEntity.badRequest().body("Debes enviar tokenRefresco");
      }

      Claims claims = jwt.claims(req.tokenRefresco());
      Object typ = claims.get("typ");
      if (!(typ instanceof String) || !"refresh".equals(typ)) {
        return ResponseEntity.badRequest().body("El token no es de refresco");
      }

      var login = claims.getSubject();
      var ud = (DetallesUsuarioAplicacion) usuarios.loadUserByUsername(login);
      if (!rolPermitido(ud.getRol())) {
        return ResponseEntity.status(403).body("Solo esta habilitado el acceso para estudiantes y administrativos");
      }

      String nuevoAcceso = jwt.emitirTokenAcceso(ud.getUsername(), ud.getRol(), ud.getId(), null);

      return ResponseEntity.ok(
          new RespuestaTokens(
              nuevoAcceso, req.tokenRefresco(), ud.getRol(), ud.getId(),
              jwt.getAccessTtlSeconds(), jwt.getRefreshTtlSeconds()));
    } catch (Exception e) {
      return ResponseEntity.status(401).body("Token de refresco invalido o expirado");
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(
      @RequestHeader(name = "Authorization", required = false) String authz) {
    try {
      if (authz == null || !authz.startsWith("Bearer ")) {
        return ResponseEntity.badRequest().body("Falta Authorization Bearer");
      }

      String accessToken = authz.substring(7);
      Claims c = jwt.claims(accessToken);
      Object typ = c.get("typ");
      if (!(typ instanceof String) || !"access".equals(typ)) {
        return ResponseEntity.badRequest().body("El token no es de acceso");
      }

      // Sin estado: no hay blacklist aqui; solo limpieza de cookie si la usas.
      ResponseCookie clearCookie = ResponseCookie.from("access_token", "")
          .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build();

      return ResponseEntity.noContent()
          .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
          .build();
    } catch (Exception e) {
      return ResponseEntity.status(401).body("Token invalido");
    }
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody(required = false) PeticionForgotPassword req) {
    return forgotPasswordInternal(req);
  }

  @PostMapping("/harogestion/forgot-password")
  public ResponseEntity<?> forgotPasswordHaroGestion(@RequestBody(required = false) PeticionForgotPassword req) {
    return forgotPasswordInternal(req);
  }

  private ResponseEntity<?> forgotPasswordInternal(PeticionForgotPassword req) {
    try {
      String correo = req == null ? null : req.correo();
      adminPasswordRecoveryService.requestRecovery(correo);
      return ResponseEntity.ok(Map.of(
          "ok", true,
          "message", "Se envio un codigo de recuperacion al correo"
      ));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of(
          "ok", false,
          "message", ex.getMessage()
      ));
    }
  }

  @PostMapping("/forgot-password/verify")
  public ResponseEntity<?> verifyForgotPassword(@RequestBody(required = false) PeticionForgotPasswordVerify req) {
    return verifyForgotPasswordInternal(req);
  }

  @PostMapping("/harogestion/forgot-password/verify")
  public ResponseEntity<?> verifyForgotPasswordHaroGestion(@RequestBody(required = false) PeticionForgotPasswordVerify req) {
    return verifyForgotPasswordInternal(req);
  }

  private ResponseEntity<?> verifyForgotPasswordInternal(PeticionForgotPasswordVerify req) {
    try {
      var out = adminPasswordRecoveryService.verifyCode(
          req == null ? null : req.correo(),
          req == null ? null : req.code()
      );
      if (!out.ok()) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", out.message()));
      }
      return ResponseEntity.ok(Map.of("ok", true, "message", out.message()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of(
          "ok", false,
          "message", ex.getMessage()
      ));
    }
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody(required = false) PeticionResetPassword req) {
    return resetPasswordInternal(req);
  }

  @PostMapping("/harogestion/reset-password")
  public ResponseEntity<?> resetPasswordHaroGestion(@RequestBody(required = false) PeticionResetPassword req) {
    return resetPasswordInternal(req);
  }

  private ResponseEntity<?> resetPasswordInternal(PeticionResetPassword req) {
    try {
      var out = adminPasswordRecoveryService.resetPassword(
          req == null ? null : req.correo(),
          req == null ? null : req.code(),
          req == null ? null : req.nuevaContrasena()
      );
      if (!out.ok()) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", out.message()));
      }
      return ResponseEntity.ok(Map.of("ok", true, "message", out.message()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of(
          "ok", false,
          "message", ex.getMessage()
      ));
    }
  }

  @PostMapping("/password/change")
  public ResponseEntity<?> changePassword(@RequestBody(required = false) PeticionCambioPassword req,
                                          Authentication authentication) {
    try {
      if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "No autenticado"));
      }
      Object principal = authentication.getPrincipal();
      if (!(principal instanceof DetallesUsuarioAplicacion ud)) {
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "Token invalido"));
      }
      if (ud.getId() == null) {
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "Usuario no identificado"));
      }

      String currentPassword = normalizePassword(req == null ? null : req.currentPassword(), "currentPassword");
      String newPassword = normalizePassword(req == null ? null : req.newPassword(), "newPassword");
      String confirmPassword = normalizePassword(req == null ? null : req.confirmPassword(), "confirmPassword");

      if (!newPassword.equals(confirmPassword)) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "La confirmacion no coincide con la nueva contrasena"));
      }
      if (currentPassword.equals(newPassword)) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "La nueva contrasena debe ser diferente a la actual"));
      }
      validatePasswordPolicy(newPassword);

      boolean currentValid;
      if ("ADMIN".equalsIgnoreCase(ud.getRol())) {
        currentValid = administradorService.validarContrasenaActual(ud.getId(), currentPassword);
        if (!currentValid) {
          return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "La contrasena actual es incorrecta"));
        }
        administradorService.cambiarContrasenaAutenticado(ud.getId(), newPassword);
      } else if ("ESTUDIANTE".equalsIgnoreCase(ud.getRol())) {
        currentValid = estudianteService.validarContrasenaActual(ud.getId(), currentPassword);
        if (!currentValid) {
          return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "La contrasena actual es incorrecta"));
        }
        estudianteService.cambiarContrasenaAutenticado(ud.getId(), newPassword);
      } else {
        return ResponseEntity.status(403).body(Map.of("ok", false, "message", "Rol no habilitado para cambio de contrasena"));
      }

      return ResponseEntity.ok(Map.of("ok", true, "message", "Contrasena actualizada correctamente"));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
    }
  }
}
