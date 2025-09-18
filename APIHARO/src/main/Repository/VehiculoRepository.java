package com.carritos.academiacarros.repository;

import com.carritos.academiacarros.model.Vehiculo;
import com.carritos.academiacarros.model.TipoVehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, String> { // PK = placa
    List<Vehiculo> findByTipo(TipoVehiculo tipo);
    List<Vehiculo> findByEstado(String estado);
}
