// com/Aplication/HARO/Model/Administrador.java
package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "administrador",
       uniqueConstraints = {
         @UniqueConstraint(name = "uk_admin_correo", columnNames = {"correo"}),
         @UniqueConstraint(name = "uk_admin_usuario", columnNames = {"usuario"})
       })
public class Administrador {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Puedes usar uno u otro para iniciar sesión (correo o usuario)
  @Column(length = 120)
  private String correo;

  @Column(length = 60)
  private String usuario;
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) 
  // Guardar SIEMPRE hash (BCrypt ≈ 60 chars)
  @Column(name = "contrasena_hash", nullable = false, length = 100)
  private String contrasenaHash;

  @Column(length = 100)
  private String nombre;

  @Column(nullable = false)
  private Boolean activo = true;

  @Column(nullable = false, updatable = false)
  private Instant creadoEn = Instant.now();

  @Column(nullable = false)
  private Instant actualizadoEn = Instant.now();

  @PreUpdate
  public void touch() { this.actualizadoEn = Instant.now(); }

  public Long getId() {
	return id;
  }

  public void setId(Long id) {
	this.id = id;
  }

  public String getCorreo() {
	return correo;
  }

  public void setCorreo(String correo) {
	this.correo = correo;
  }

  public String getUsuario() {
	return usuario;
  }

  public void setUsuario(String usuario) {
	this.usuario = usuario;
  }

  public String getContrasenaHash() {
	return contrasenaHash;
  }

  public void setContrasenaHash(String contrasenaHash) {
	this.contrasenaHash = contrasenaHash;
  }

  public String getNombre() {
	return nombre;
  }

  public void setNombre(String nombre) {
	this.nombre = nombre;
  }

  public Boolean getActivo() {
	return activo;
  }

  public void setActivo(Boolean activo) {
	this.activo = activo;
  }

  public Instant getCreadoEn() {
	return creadoEn;
  }

  public void setCreadoEn(Instant creadoEn) {
	this.creadoEn = creadoEn;
  }

  public Instant getActualizadoEn() {
	return actualizadoEn;
  }

  public void setActualizadoEn(Instant actualizadoEn) {
	this.actualizadoEn = actualizadoEn;
  }

  
  
}
