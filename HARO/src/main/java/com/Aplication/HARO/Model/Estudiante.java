package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "estudiante", indexes = {
		@Index(name = "idx_estudiante_numdoc", columnList = "numero_documento", unique = true) })
public class Estudiante {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY) // <-- evita que el cliente setee
	private Long id;
	@Column(name = "nombre")
	private String nombre;
	@Column(name = "apellido")
	private String apellido;
	@Column(name = "tipo_estudiante")
	private String tipoEstudiante;
	@Column(name = "tipo_documento")
	private String tipoDocumento;

	@Column(name = "numero_documento", nullable = false, unique = true)
	private String numeroDocumento;
	@Column(name = "categoria")
	private String categoria;
	@Column(name = "telefono")
	private String telefono;
	@Column(name = "email")
	private String email;
	@Column(name = "direccion")
	private String direccion;
	@Column(name = "estado")
	private String estado;
	@Column(name = "usuario", nullable = false, unique = true)
	private String usuario;

	@Column(name = "contrasena", nullable = false)
	private String contrasena;


	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellido() {
		return apellido;
	}

	public void setApellido(String apellido) {
		this.apellido = apellido;
	}

	public String getTipoDocumento() {
		return tipoDocumento;
	}

	public void setTipoDocumento(String tipoDocumento) {
		this.tipoDocumento = tipoDocumento;
	}

	public String getNumeroDocumento() {
		return numeroDocumento;
	}

	public void setNumeroDocumento(String numeroDocumento) {
		this.numeroDocumento = numeroDocumento;
	}

	public String getTelefono() {
		return telefono;
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getUsuario() {
		return usuario;
	}

	public void setUsuario(String usuario) {
		this.usuario = usuario;
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

	public String getTipoEstudiante() {
		return tipoEstudiante;
	}

	public void setTipoEstudiante(String tipoEstudiante) {
		this.tipoEstudiante = tipoEstudiante;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	
	

}
