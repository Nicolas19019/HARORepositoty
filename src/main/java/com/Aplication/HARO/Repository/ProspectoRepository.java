package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Prospecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProspectoRepository extends JpaRepository<Prospecto, Long> {

    Optional<Prospecto> findByTelefonoAndServicio(String telefono, String servicio);
}

