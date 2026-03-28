package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.SoporteTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface SoporteTicketRepository extends JpaRepository<SoporteTicket, Long> {

    long countByEstadoIn(Collection<String> estados);
}
