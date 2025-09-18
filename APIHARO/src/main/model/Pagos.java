package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pago")
public class Pagos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_estado", nullable = false,
                foreignKey = @ForeignKey(name = "fk_pago_estado"))
    private EstadoCuenta estadoCuenta;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    private MetodoPago metodo; // ENUM

    // getters/setters
    // ...
}
