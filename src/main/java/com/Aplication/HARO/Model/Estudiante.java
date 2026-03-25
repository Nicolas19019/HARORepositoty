package com.Aplication.HARO.Model;

import jakarta.persistence.*;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.ZoneId;

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
	@Column(name = "sede", length = 120)
	private String sede;
	@Column(name = "horas")
	private Integer horas;
	@Column(name = "tipo_pase", length = 20)
	private String tipoPase; // valores permitidos: carro, moto, carro,moto
	@Column(name = "aprobo_examen_teorico")
	private Boolean aproboExamenTeorico = false;
	@Column(name = "telefono")
	private String telefono;
	@Column(name = "email")
	private String email;
	@Column(name = "direccion")
	private String direccion;
	@Column(name = "foto_perfil", columnDefinition = "TEXT")
	private String fotoPerfil;
	@Column(name = "estado")
	private String estado;
	@Column(name = "visible", columnDefinition = "boolean default true")
	private Boolean visible = true;
	@Column(name = "usuario", nullable = false, unique = true)
	private String usuario;
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) 
	@Column(name = "contrasena", nullable = false)
	private String contrasena;

	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(name = "fecha_creacion", updatable = false)
	private LocalDate fechaCreacion;

	@Column(name = "fecha_matricula")
	private LocalDate fechaMatricula;

	@Column(name = "origen_matricula", length = 20)
	private String origenMatricula;


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

	public String getFotoPerfil() {
		return fotoPerfil;
	}

	public void setFotoPerfil(String fotoPerfil) {
		this.fotoPerfil = fotoPerfil;
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

	public Boolean getVisible() {
		return visible;
	}

	public void setVisible(Boolean visible) {
		this.visible = visible;
	}

	public String getContrasena() {
		return contrasena;
	}

	public void setContrasena(String contrasena) {
		this.contrasena = contrasena;
	}

	public LocalDate getFechaCreacion() {
		return fechaCreacion;
	}

	public void setFechaCreacion(LocalDate fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

	public LocalDate getFechaMatricula() {
		return fechaMatricula;
	}

	public void setFechaMatricula(LocalDate fechaMatricula) {
		this.fechaMatricula = fechaMatricula;
	}

	public String getOrigenMatricula() {
		return origenMatricula;
	}

	public void setOrigenMatricula(String origenMatricula) {
		this.origenMatricula = origenMatricula;
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

	public String getSede() {
		return sede;
	}

	public void setSede(String sede) {
		this.sede = sede;
	}

	public Integer getHoras() {
		return horas;
	}

	public void setHoras(Integer horas) {
		this.horas = horas;
	}

	public String getTipoPase() {
		return tipoPase;
	}

	public void setTipoPase(String tipoPase) {
		this.tipoPase = tipoPase;
	}

	public Boolean getAproboExamenTeorico() {
		return aproboExamenTeorico;
	}

	public void setAproboExamenTeorico(Boolean aproboExamenTeorico) {
		this.aproboExamenTeorico = aproboExamenTeorico;
	}

	@PrePersist
	public void onCreate() {
		if (visible == null) visible = true;
		if (fechaCreacion == null) {
			fechaCreacion = LocalDate.now(ZoneId.of("America/Bogota"));
		}
	}

	@PreUpdate
	public void onUpdate() {
		if (visible == null) visible = true;
	}

}
