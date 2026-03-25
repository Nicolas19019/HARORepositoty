package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Security.ServicioJwt;
import com.Aplication.HARO.Security.ServicioUsuariosCombinado;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.jsonwebtoken.Claims;
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

  public AutenticacionController(AuthenticationManager authManager,
                                 ServicioUsuariosCombinado usuarios,
                                 ServicioJwt jwt,
                                 EstudianteModuloAccesoService estudianteModuloAccesoService) {
    this.authManager = authManager;
    this.usuarios = usuarios;
    this.jwt = jwt;
    this.estudianteModuloAccesoService = estudianteModuloAccesoService;
  }

  private boolean rolPermitido(String rol) {
    return "ESTUDIANTE".equalsIgnoreCase(rol) || "ADMIN".equalsIgnoreCase(rol);
  }

  private String normalizarCorreo(String correo) {
    return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
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
}
