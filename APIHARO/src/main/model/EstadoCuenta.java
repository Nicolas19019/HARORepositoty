package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "estado_cuenta")
public class EstadoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false,
                foreignKey = @ForeignKey(name = "fk_estado_estudiante"))
    private Estudiante estudiante;

    @Column(name = "monto_total", precision = 14, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", precision = 14, scale = 2)
    private BigDecimal montoPagado;

    @Enumerated(EnumType.STRING)
    private EstadoDeuda estado; // ENUM(Pendiente, Parcial, PazYSalvo)

    @OneToMany(mappedBy = "estadoCuenta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pago> pagos = new ArrayList<>();

}
