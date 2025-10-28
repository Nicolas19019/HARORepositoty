// src/main/java/com/Aplication/HARO/Security/DetallesUsuarioAplicacion.java
package com.Aplication.HARO.Security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class DetallesUsuarioAplicacion implements UserDetails {

  private final Long id;
  private final String username;
  private final String passwordHash;
  private final String rol; // ADMIN / PROFESOR / ESTUDIANTE
  private final boolean activo;

  public DetallesUsuarioAplicacion(Long id, String username, String passwordHash, String rol, boolean activo) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.rol = rol;
    this.activo = activo;
  }

  public Long getId() { return id; }
  public String getRol() { return rol; }

  @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(() -> "ROLE_" + rol); }
  @Override public String getPassword() { return passwordHash; }
  @Override public String getUsername() { return username; }
  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return activo; }
}
