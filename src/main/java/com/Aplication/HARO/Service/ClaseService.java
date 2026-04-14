package com.Aplication.HARO.Service;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Repository.ClaseRepository;


/**
 * Servicio de clases practicas.
 *
 * Expone operaciones basicas de agenda por fecha, rango e identificador para
 * crear, actualizar y eliminar clases.
 */
@Service
@Transactional
public class ClaseService {

    private final ClaseRepository repository;

    public ClaseService(ClaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista los registros solicitados segun los filtros recibidos.
     */
    @Transactional(readOnly = true)
    public List<Clase> listarPorFecha(LocalDate fecha) {
	        return repository.findByFecha(fecha);
	    }

    /**
     * Lista los registros solicitados segun los filtros recibidos.
     */
    @Transactional(readOnly = true)
    public List<Clase> listarPorRango(LocalDate desde, LocalDate hasta) {
	        return repository.findByFechaBetween(desde, hasta);
	    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<Clase> getAllClases() {
        return repository.findAll();
    }

    /**
     * Obtiene el registro solicitado por identificador o criterio de busqueda.
     */
    @Transactional(readOnly = true)
    public Optional<Clase> getClaseById(long id) {
        return repository.findById(id);
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public Clase createClase(Clase c) {
        return repository.save(c);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public Clase updateClase(Clase c) {
        return repository.save(c);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteClase(int id) {
        repository.deleteById((long) id);
    }
}
