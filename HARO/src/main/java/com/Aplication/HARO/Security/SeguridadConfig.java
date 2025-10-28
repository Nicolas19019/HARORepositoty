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

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

  // === Helper estáticos (no crean archivos nuevos) ===
  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    if ((len & 1) != 0) throw new IllegalArgumentException("HEX inválido");
    byte[] out = new byte[len/2];
    for (int i=0;i<len;i+=2) {
      out[i/2] = (byte) ((Character.digit(hex.charAt(i),16) << 4)
                       +  Character.digit(hex.charAt(i+1),16));
    }
    return out;
  }

  private static String sha256Base64(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(data);
      return Base64.getEncoder().encodeToString(dig);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // 1) 🔒 Encoder: BCrypt( SHA256( client_digest + PEPPER ) )
  //    El cliente envía password = SHA-256(hex) de la contraseña.
  @Bean @Primary
  public PasswordEncoder passwordEncoder(@Value("${app.auth.pepper}") String pepper) {
    final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    final byte[] pep = pepper.getBytes(StandardCharsets.UTF_8);

    return new PasswordEncoder() {
      private String prehash(CharSequence rawFromClient) {
        // rawFromClient viene como HEX (sha256 del cliente).
        byte[] clientDigest = hexToBytes(rawFromClient.toString().toLowerCase());
        byte[] combo = new byte[clientDigest.length + pep.length];
        System.arraycopy(clientDigest, 0, combo, 0, clientDigest.length);
        System.arraycopy(pep, 0, combo, clientDigest.length, pep.length);
        // Mandamos a BCrypt una cadena Base64 del sha256(combo) para estandarizar longitud
        return sha256Base64(combo);
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

  @Bean
  public DaoAuthenticationProvider authenticationProvider(ServicioUsuariosCombinado servicioUsuariosCombinado,
                                                          PasswordEncoder encoder) {
    DaoAuthenticationProvider p = new DaoAuthenticationProvider();
    p.setUserDetailsService(servicioUsuariosCombinado);
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
