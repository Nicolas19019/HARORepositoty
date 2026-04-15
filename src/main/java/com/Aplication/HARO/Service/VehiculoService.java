package com.Aplication.HARO.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.VehiculoRepository;

/**
 * Servicio de vehiculos.
 *
 * Gestiona consulta, creacion, actualizacion y eliminacion logica de vehiculos
 * disponibles para clases practicas.
 */
@Service
@Transactional
public class VehiculoService {

    private final VehiculoRepository repository;

    public VehiculoService(VehiculoRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<Vehiculo> getAllVehiculos() {
        return repository.findByVisibleTrueOrderByPlacaAsc();
    }

    /**
     * Obtiene el registro solicitado por identificador o criterio de busqueda.
     */
    @Transactional(readOnly = true)
    public Optional<Vehiculo> getVehiculoByPlaca(String placa) {
        return repository.findByPlacaAndVisibleTrue(placa);
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public Vehiculo createVehiculo(Vehiculo v) {
        // En API, todo registro nuevo nace visible.
        v.setVisible(true);
        return repository.save(v);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public Vehiculo updateVehiculo(Vehiculo v) {
        Vehiculo db = repository.findById(v.getPlaca())
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + v.getPlaca()));

        if (v.getMarca() != null) db.setMarca(v.getMarca());
        if (v.getModelo() != null) db.setModelo(v.getModelo());
        if (v.getAnio() != null) db.setAnio(v.getAnio());
        if (v.getSede() != null) db.setSede(v.getSede());
        if (v.getEstado() != null) db.setEstado(v.getEstado());
        if (v.getVisible() != null) db.setVisible(v.getVisible());

        return repository.save(db);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteVehiculo(String placa) {
        Vehiculo db = repository.findById(placa)
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + placa));
        db.setVisible(false);
        repository.save(db);
    }
}
