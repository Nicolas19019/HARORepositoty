package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.EstadoCuenta;
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryEstadoCuenta;

@Service
public class EstadoCuentaService {

    @Autowired
    private RepositoryEstadoCuenta repository;

    public List<EstadoCuenta> getAllEstadosCuenta() {
        return repository.findAll();
    }

    public Optional<EstadoCuenta> getEstadoCuentaById(int id) {
        return repository.findById(id);
    }

    public EstadoCuenta createEstadoCuenta(EstadoCuenta e) {
        return repository.save(e);
    }

    public EstadoCuenta updateEstadoCuenta(EstadoCuenta e) {
        return repository.save(e);
    }

    public void deleteEstadoCuenta(int id) {
        repository.deleteById(id);
    }
}
