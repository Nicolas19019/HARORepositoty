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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Configuracion principal de Spring Security.
 *
 * Define el encoder de contrasenas, autenticacion combinada, filtro JWT, CORS,
 * respuestas 401 y la cadena de filtros HTTP de la API.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

  /* ===========================
     Helpers
     =========================== */

  // Verifica si el texto es HEX despues de normalizarlo.
  private static boolean isHexEvenLength(String s) {
    if (s == null) return false;
    String norm = s.trim();
    norm = norm.replaceFirst("(?i)^0x", "");
    norm = norm.replaceAll("\\s+", "");
    if ((norm.length() & 1) != 0) return false;
    return norm.matches("[0-9A-Fa-f]*");
  }

  // Decodifica HEX tolerante a prefijo 0x y espacios.
  private static byte[] hexToBytesStrict(String s) {
    if (s == null) throw new IllegalArgumentException("HEX nulo");
    String hex = s.trim().replaceFirst("(?i)^0x", "").replaceAll("\\s+", "");
    if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]*")) {
      throw new IllegalArgumentException("HEX invalido");
    }
    int len = hex.length();
    byte[] out = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      int hi = Character.digit(hex.charAt(i), 16);
      int lo = Character.digit(hex.charAt(i + 1), 16);
      if (hi < 0 || lo < 0) throw new IllegalArgumentException("HEX invalido");
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
     Encoder: BCrypt( Base64( SHA256( clientInput + PEPPER ) ) )
     - clientInput puede ser:
       a) HEX de SHA-256(password) -> lo decodificamos (modo legacy/cliente)
       b) password en texto plano -> le hacemos SHA-256 aqui (modo tolerante)
     - PEPPER (texto) se concatena en bytes UTF-8
     =========================================================== */

  /**
   * Crea el encoder principal: normaliza entrada, aplica pepper y guarda BCrypt.
   */
  @Bean
  @Primary
  public PasswordEncoder passwordEncoder(@Value("${app.auth.pepper:}") String pepper) {
    final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    final byte[] pep = pepper == null ? new byte[0] : pepper.getBytes(StandardCharsets.UTF_8);

    return new PasswordEncoder() {
      private byte[] normalizeClientInput(CharSequence rawFromClient) {
        if (rawFromClient == null) {
          return new byte[0];
        }
        String input = rawFromClient.toString();
        if (isHexEvenLength(input)) {
          return hexToBytesStrict(input);
        }
        return sha256Bytes(input.getBytes(StandardCharsets.UTF_8));
      }

      private String prehash(CharSequence rawFromClient) {
        byte[] clientInputBytes = normalizeClientInput(rawFromClient);

        byte[] combo = new byte[clientInputBytes.length + pep.length];
        System.arraycopy(clientInputBytes, 0, combo, 0, clientInputBytes.length);
        System.arraycopy(pep, 0, combo, clientInputBytes.length, pep.length);

        byte[] finalDigest = sha256Bytes(combo);
        return base64(finalDigest);
      }

      @Override public String encode(CharSequence rawPassword) {
        return bcrypt.encode(prehash(rawPassword));
      }

      @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
          return false;
        }

        if (bcrypt.matches(prehash(rawPassword), encodedPassword)) {
          return true;
        }

        if (rawPassword != null && bcrypt.matches(rawPassword.toString(), encodedPassword)) {
          return true;
        }

        try {
          byte[] client = normalizeClientInput(rawPassword);
          String noPepper = base64(sha256Bytes(client));
          return bcrypt.matches(noPepper, encodedPassword);
        } catch (Exception ignored) {
          return false;
        }
      }

      @Override public boolean upgradeEncoding(String encodedPassword) {
        return bcrypt.upgradeEncoding(encodedPassword);
      }
    };
  }

  /* ===========================
     Auth & Security
     =========================== */

  /**
   * Conecta el servicio de usuarios combinado con el encoder principal.
   */
  @Bean
  public DaoAuthenticationProvider authenticationProvider(ServicioUsuariosCombinado uds,
                                                          PasswordEncoder encoder) {
    DaoAuthenticationProvider p = new DaoAuthenticationProvider();
    p.setUserDetailsService(uds);
    p.setPasswordEncoder(encoder);
    p.setHideUserNotFoundExceptions(false);
    return p;
  }

  /**
   * Expone el AuthenticationManager configurado por Spring Security.
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
    return cfg.getAuthenticationManager();
  }

  /**
   * Crea el filtro JWT que se instala antes del filtro de usuario y contrasena.
   */
  @Bean
  public FiltroJwt filtroJwt(ServicioJwt servicioJwt, ServicioUsuariosCombinado usuarios) {
    return new FiltroJwt(servicioJwt, usuarios);
  }

  /**
   * Configura CORS para el sitio publico, pagos, contratos y desarrollo local.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    CorsConfiguration localCors = new CorsConfiguration();
    localCors.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://ceaharo.com",
            "https://www.ceaharo.com",
            "https://*.ceaharo.com",
            "null"
    ));
    localCors.setAllowCredentials(true);
    localCors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    localCors.setAllowedHeaders(List.of("*"));
    localCors.setMaxAge(3600L);

    CorsConfiguration siteCors = new CorsConfiguration();
    siteCors.setAllowedOrigins(List.of(
            "https://ceaharo.com",
            "https://www.ceaharo.com"
    ));
    siteCors.setAllowCredentials(false);
    siteCors.setAllowedMethods(List.of("GET", "POST", "OPTIONS", "HEAD"));
    siteCors.setAllowedHeaders(List.of("*"));
    siteCors.setMaxAge(3600L);

    source.registerCorsConfiguration("/epayco/**", siteCors);
    source.registerCorsConfiguration("/response", siteCors);
    source.registerCorsConfiguration("/confirmation", siteCors);

    // La pagina de contratos valida y completa firma contra estos endpoints.
    source.registerCorsConfiguration("/api/verification/contract/**", siteCors);

    source.registerCorsConfiguration("/**", localCors);
    return source;
  }

  /**
   * Respuesta uniforme para solicitudes no autenticadas.
   */
  @Bean
  public AuthenticationEntryPoint api401EntryPoint() {
    return (req, res, ex) -> {
      res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
    };
  }

  /**
   * Define la cadena HTTP stateless y registra el filtro JWT.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 DaoAuthenticationProvider dao,
                                                 AuthenticationEntryPoint api401EntryPoint,
                                                 FiltroJwt filtroJwt) throws Exception {
    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(eh -> eh.authenticationEntryPoint(api401EntryPoint))
      .authenticationProvider(dao)
      .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .anyRequest().permitAll()
      )
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
