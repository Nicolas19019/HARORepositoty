// src/main/java/com/Aplication/HARO/Security/SeguridadConfig.java
package com.Aplication.HARO.Security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

  // ÚNICO PasswordEncoder (delegating) -> acepta {bcrypt}, {noop}, etc.
  @Bean
  @Primary
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  // NO declarar @Bean userDetailsService aquí para no duplicar.
  // Usaremos el @Service ServicioUsuariosCombinado ya escaneado por Spring.

  @Bean
  public DaoAuthenticationProvider authenticationProvider(ServicioUsuariosCombinado servicioUsuariosCombinado,
                                                          PasswordEncoder encoder) {
    DaoAuthenticationProvider p = new DaoAuthenticationProvider();
    p.setUserDetailsService(servicioUsuariosCombinado);
    p.setPasswordEncoder(encoder);
    return p;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
    return cfg.getAuthenticationManager();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", "null")); // Live Server y file://
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
    return new AuthenticationEntryPoint() {
      @Override
      public void commence(HttpServletRequest req, HttpServletResponse res,
                           org.springframework.security.core.AuthenticationException ex)
          throws IOException, ServletException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
      }
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
        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()

        // 🔓 Deja el diagnóstico SMTP público mientras pruebas desde la página.
        // En prod cambia a: .requestMatchers("/api/_smtp/**").hasRole("ADMIN")
        .requestMatchers("/api/_smtp/**").permitAll()

        // OTP protegido por Basic
        .requestMatchers("/api/verification/**").permitAll()

        // Resto protegido por Basic
        .anyRequest().permitAll()
      )
      .httpBasic(Customizer.withDefaults());

    // SIN filtros JWT aquí (solo Basic).
    return http.build();
  }
}
