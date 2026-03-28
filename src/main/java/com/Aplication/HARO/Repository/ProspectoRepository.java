package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Prospecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProspectoRepository extends JpaRepository<Prospecto, Long> {

    Optional<Prospecto> findByTelefono(String telefono);

    java.util.List<Prospecto> findByTelefonoOrderByActualizadoEnDesc(String telefono);

    java.util.List<Prospecto> findByTelefonoInOrderByActualizadoEnDesc(java.util.Collection<String> telefonos);
}
