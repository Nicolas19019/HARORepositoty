package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.SoporteTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

/**
 * Acceso a tickets de soporte.
 *
 * Provee persistencia de solicitudes y conteos por estado para paneles de
 * seguimiento.
 */
public interface SoporteTicketRepository extends JpaRepository<SoporteTicket, Long> {

    long countByEstadoIn(Collection<String> estados);
}
