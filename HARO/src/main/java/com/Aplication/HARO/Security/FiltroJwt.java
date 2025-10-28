// src/main/java/com/Aplication/HARO/Security/FiltroJwt.java
package com.Aplication.HARO.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class FiltroJwt extends OncePerRequestFilter {

  private final ServicioJwt servicioJwt;
  private final ServicioUsuariosCombinado usuarios;

  public FiltroJwt(ServicioJwt servicioJwt, ServicioUsuariosCombinado usuarios) {
    this.servicioJwt = servicioJwt;
    this.usuarios = usuarios;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    String auth = request.getHeader("Authorization");

    // Si no hay Bearer, deja pasar (para que funcione Basic y permitAll)
    if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = auth.substring(7).trim();
    try {
      String login = servicioJwt.extraerLogin(token);
      if (login != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        var userDetails = usuarios.loadUserByUsername(login);
        if (servicioJwt.tokenValido(token, userDetails)) {
          var authToken = new UsernamePasswordAuthenticationToken(
              userDetails, null, userDetails.getAuthorities());
          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      }
    } catch (Exception ignored) { }

    filterChain.doFilter(request, response);
  }
}
