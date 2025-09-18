package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "profesor")
public class Profesor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profesor")
    private Long id;

    private String nombre;
    private String apellido;

    @Enumerated(EnumType.STRING)
    private EspecialidadProfesor especialidad; // ENUM(Teórico, Práctico)

    private String telefono;
    private String email;

    @OneToMany(mappedBy = "profesor", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Clase> clases = new ArrayList<>();

  
}
