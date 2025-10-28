// src/main/java/com/Aplication/HARO/Controller/AutenticacionController.java
package com.Aplication.HARO.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Security.ServicioJwt;
import com.Aplication.HARO.Security.ServicioUsuariosCombinado;

import io.jsonwebtoken.Claims;

record PeticionInicioSesion(String login, String password) {}
record RespuestaTokens(String tokenAcceso, String tokenRefresco, String rol, Long uid) {}
record PeticionRefresco(String tokenRefresco) {}

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AutenticacionController {

  private final AuthenticationManager authManager;
  private final ServicioUsuariosCombinado usuarios;
  private final ServicioJwt jwt;

  public AutenticacionController(AuthenticationManager authManager,
                                 ServicioUsuariosCombinado usuarios,
                                 ServicioJwt jwt) {
    this.authManager = authManager;
    this.usuarios = usuarios;
    this.jwt = jwt;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody PeticionInicioSesion req) {
    try {
      var auth = new UsernamePasswordAuthenticationToken(req.login(), req.password());
      authManager.authenticate(auth);

      var ud = (DetallesUsuarioAplicacion) usuarios.loadUserByUsername(req.login());
      String at = jwt.emitirTokenAcceso(ud.getUsername(), ud.getRol(), ud.getId(), null);
      String rt = jwt.emitirTokenRefresco(ud.getUsername());
      return ResponseEntity.ok(new RespuestaTokens(at, rt, ud.getRol(), ud.getId()));
    } catch (AuthenticationException e) {
      return ResponseEntity.status(401).body("Credenciales inválidas");
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody PeticionRefresco req) {
    try {
      Claims claims = jwt.claims(req.tokenRefresco());
      Object typ = claims.get("typ");
      if (!(typ instanceof String) || !"refresh".equals(typ)) {
        return ResponseEntity.badRequest().body("El token no es de refresco");
      }
      var login = claims.getSubject();
      var ud = (DetallesUsuarioAplicacion) usuarios.loadUserByUsername(login);

      String nuevoAcceso = jwt.emitirTokenAcceso(ud.getUsername(), ud.getRol(), ud.getId(), null);
      return ResponseEntity.ok(new RespuestaTokens(nuevoAcceso, req.tokenRefresco(), ud.getRol(), ud.getId()));
    } catch (Exception e) {
      return ResponseEntity.status(401).body("Token de refresco inválido o expirado");
    }
  }
}
