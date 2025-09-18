package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "estudiante",
       indexes = {@Index(name = "idx_estudiante_numdoc", columnList = "numero_documento", unique = true)})
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante")
    private Long id;

    private String nombre;
    private String apellido;

    @Column(name = "tipo_documento")
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, unique = true)
    private String numeroDocumento;
|   @Column(name = "telefono")
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

    // Relaciones
    @OneToMany(mappedBy = "estudiante", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Clase> clases = new ArrayList<>();

    @OneToOne(mappedBy = "estudiante", cascade = CascadeType.ALL, optional = true)
    private EstadoCuenta estadoCuenta;

    // getters/setters
    // ...
}
