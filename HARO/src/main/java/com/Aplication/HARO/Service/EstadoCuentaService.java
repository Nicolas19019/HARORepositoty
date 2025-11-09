package com.Aplication.HARO.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Repository.EstadoCuentaRepository;

@Service
public class EstadoCuentaService {

    private final EstadoCuentaRepository repository;

    public EstadoCuentaService(EstadoCuentaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EstadoCuenta> getAllEstadosCuenta() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<EstadoCuenta> getEstadoCuentaById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public EstadoCuenta createEstadoCuenta(EstadoCuenta e) {
        // Normaliza id nulo para INSERT
        e.setId(null);

        // Validación defensiva adicional (además del @PrePersist en la entidad)
        if (e.getMontoPagado() != null && e.getMontoTotal() != null
                && e.getMontoPagado().compareTo(e.getMontoTotal()) > 0) {
            throw new IllegalArgumentException("El monto_pagado no puede superar el monto_total.");
        }

        try {
            return repository.save(e);
        } catch (DataIntegrityViolationException ex) {
            // Mensaje más claro cuando falla por constraints (FK, NOT NULL, CHECK)
            throw new DataIntegrityViolationException(
                "No se pudo crear el estado de cuenta. Verifica idEstudiante y los montos.", ex
            );
        }
    }

    @Transactional
    public EstadoCuenta updateEstadoCuenta(Long id, EstadoCuenta e) {
        EstadoCuenta actual = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Estado de cuenta no encontrado: " + id));

        // Aplica cambios permitidos
        if (e.getIdEstudiante() != null) {
            actual.setIdEstudiante(e.getIdEstudiante());
        }
        if (e.getMontoTotal() != null) {
            actual.setMontoTotal(e.getMontoTotal());
        }
        if (e.getMontoPagado() != null) {
            actual.setMontoPagado(e.getMontoPagado());
        }
        // 'estado' es calculado en la entidad; ignoramos lo que venga del cliente

        // Validación defensiva
        if (actual.getMontoPagado() != null && actual.getMontoTotal() != null
                && actual.getMontoPagado().compareTo(actual.getMontoTotal()) > 0) {
            throw new IllegalArgumentException("El monto_pagado no puede superar el monto_total.");
        }

        try {
            return repository.save(actual);
        } catch (DataIntegrityViolationException ex) {
            throw new DataIntegrityViolationException(
                "No se pudo actualizar el estado de cuenta. Verifica idEstudiante y los montos.", ex
            );
        }
    }

    @Transactional
    public void deleteEstadoCuenta(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Estado de cuenta no encontrado: " + id);
        }
        repository.deleteById(id);
    }
}
