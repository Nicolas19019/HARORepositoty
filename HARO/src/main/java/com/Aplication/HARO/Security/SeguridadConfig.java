// src/main/java/com/Aplication/HARO/Security/SeguridadConfig.java
package com.Aplication.HARO.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

  /* ===========================
     Helpers
     =========================== */

  // ¿Es HEX (solo [0-9A-Fa-f]) después de normalizar?
  private static boolean isHexEvenLength(String s) {
    if (s == null) return false;
    String norm = s.trim();
    // quita prefijo 0x y espacios internos
    norm = norm.replaceFirst("(?i)^0x", "");
    norm = norm.replaceAll("\\s+", "");
    if ((norm.length() & 1) != 0) return false;
    return norm.matches("[0-9A-Fa-f]*");
  }

  // Decodifica HEX tolerante (0x, espacios). Lanza IllegalArgumentException si no es HEX válido.
  private static byte[] hexToBytesStrict(String s) {
    if (s == null) throw new IllegalArgumentException("HEX nulo");
    String hex = s.trim().replaceFirst("(?i)^0x", "").replaceAll("\\s+", "");
    if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]*")) {
      throw new IllegalArgumentException("HEX inválido");
    }
    int len = hex.length();
    byte[] out = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      int hi = Character.digit(hex.charAt(i), 16);
      int lo = Character.digit(hex.charAt(i + 1), 16);
      if (hi < 0 || lo < 0) throw new IllegalArgumentException("HEX inválido");
      out[i / 2] = (byte) ((hi << 4) + lo);
    }
    return out;
  }

  private static byte[] sha256Bytes(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String base64(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }

  /* ===========================================================
     Encoder: BCrypt(  Base64( SHA256( clientInput + PEPPER ) )  )
     - clientInput puede ser:
       a) HEX de SHA-256(password)  -> lo decodificamos (modo “legacy/cliente”)
       b) password en texto plano   -> le hacemos SHA-256 aquí (modo “tolerante”)
     - PEPPER (texto) se concatena en bytes UTF-8
     =========================================================== */

  @Bean
  @Primary
  public PasswordEncoder passwordEncoder(@Value("${app.auth.pepper:}") String pepper) {
    final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    final byte[] pep = pepper == null ? new byte[0] : pepper.getBytes(StandardCharsets.UTF_8);

    return new PasswordEncoder() {
      private String prehash(CharSequence rawFromClient) {
        if (rawFromClient == null) {
          // caso extremo: vacío
          return base64(sha256Bytes(pep));
        }
        String input = rawFromClient.toString();

        byte[] clientInputBytes;
        if (isHexEvenLength(input)) {
          // El cliente envió SHA-256(password) en HEX
          clientInputBytes = hexToBytesStrict(input);
        } else {
          // El cliente envió la contraseña en texto plano
          clientInputBytes = input.getBytes(StandardCharsets.UTF_8);
          clientInputBytes = sha256Bytes(clientInputBytes); // ahora tenemos SHA-256(password)
        }

        // concatena digest + pepper
        byte[] combo = new byte[clientInputBytes.length + pep.length];
        System.arraycopy(clientInputBytes, 0, combo, 0, clientInputBytes.length);
        System.arraycopy(pep, 0, combo, clientInputBytes.length, pep.length);

        // estandariza longitud para BCrypt: Base64(SHA256(combo))
        byte[] finalDigest = sha256Bytes(combo);
        return base64(finalDigest);
      }

      @Override public String encode(CharSequence rawPassword) {
        return bcrypt.encode(prehash(rawPassword));
      }

      @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bcrypt.matches(prehash(rawPassword), encodedPassword);
      }

      @Override public boolean upgradeEncoding(String encodedPassword) {
        return bcrypt.upgradeEncoding(encodedPassword);
      }
    };
  }

  /* ===========================
     Auth & Security
     =========================== */

  @Bean
  public DaoAuthenticationProvider authenticationProvider(ServicioUsuariosCombinado uds,
                                                          PasswordEncoder encoder) {
    DaoAuthenticationProvider p = new DaoAuthenticationProvider();
    p.setUserDetailsService(uds);
    p.setPasswordEncoder(encoder);
    p.setHideUserNotFoundExceptions(false);
    return p;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
    return cfg.getAuthenticationManager();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", "null"));
    cfg.setAllowCredentials(true);
    cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","Origin","X-Requested-With"));
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  @Bean
  public AuthenticationEntryPoint api401EntryPoint() {
    return (req, res, ex) -> {
      res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 DaoAuthenticationProvider dao,
                                                 AuthenticationEntryPoint api401EntryPoint) throws Exception {
    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(eh -> eh.authenticationEntryPoint(api401EntryPoint))
      .authenticationProvider(dao)
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .anyRequest().permitAll()
      )
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
