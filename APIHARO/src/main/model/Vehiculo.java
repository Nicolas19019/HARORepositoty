package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "vehiculo")
public class Vehiculo {

    @Id
    @Column(name = "placa", length = 20)
    private String placa; // PK y UNIQUE

    @Enumerated(EnumType.STRING)
    private TipoVehiculo tipo; // ENUM(Teórico, Práctico)

    private String marca;
    private String modelo;

    @Column(name = "anio")
    private Integer anio; // usar Integer en lugar de java.time.Year por compatibilidad JPA

    private String estado;

    @OneToMany(mappedBy = "vehiculo", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Clase> clases = new ArrayList<>();

    // getters/setters
    // ...
}
