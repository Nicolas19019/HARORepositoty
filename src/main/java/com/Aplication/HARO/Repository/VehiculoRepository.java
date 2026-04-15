package com.Aplication.HARO.Repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.Vehiculo;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a vehiculos por placa.
 *
 * Consulta vehiculos visibles y disponibles por estado para la asignacion de
 * clases practicas.
 */
@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, String> { // PK = placa
    
    List<Vehiculo> findByEstado(String estado);
    Optional<Vehiculo> findByPlacaAndVisibleTrue(String placa);
    Optional<Vehiculo> findFirstByVisibleTrueAndEstadoIgnoreCaseOrderByPlacaAsc(String estado);
    Optional<Vehiculo> findFirstByVisibleTrueOrderByPlacaAsc();
    List<Vehiculo> findByVisibleTrueAndEstadoIgnoreCaseOrderByPlacaAsc(String estado);
    List<Vehiculo> findByVisibleTrueOrderByPlacaAsc();
}
