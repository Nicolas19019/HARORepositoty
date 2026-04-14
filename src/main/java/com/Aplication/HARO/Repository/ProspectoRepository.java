package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Prospecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a prospectos capturados por telefono.
 *
 * Permite ubicar la ficha vigente y consultar historicos recientes para
 * seguimiento comercial.
 */
public interface ProspectoRepository extends JpaRepository<Prospecto, Long> {

    Optional<Prospecto> findByTelefono(String telefono);

    java.util.List<Prospecto> findByTelefonoOrderByActualizadoEnDesc(String telefono);

    java.util.List<Prospecto> findByTelefonoInOrderByActualizadoEnDesc(java.util.Collection<String> telefonos);
}
