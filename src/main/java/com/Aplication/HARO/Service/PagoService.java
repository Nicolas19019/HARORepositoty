package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Repository.PagoRepository;

/**
 * Servicio de pagos manuales.
 *
 * Expone operaciones basicas para crear, consultar, actualizar y eliminar pagos
 * asociados a estados de cuenta.
 */
@Service
@Transactional
public class PagoService {

    private final PagoRepository repository;

    public PagoService(PagoRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<Pagos> getAllPagos() {
        return repository.findAll();
    }

    /**
     * Obtiene el registro solicitado por identificador o criterio de busqueda.
     */
    @Transactional(readOnly = true)
    public Optional<Pagos> getPagoById(int id) {
        return repository.findById((long) id);
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public Pagos createPago(Pagos p) {
        return repository.save(p);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public Pagos updatePago(Pagos p) {
        return repository.save(p);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deletePago(int id) {
        repository.deleteById((long) id);
    }
}
