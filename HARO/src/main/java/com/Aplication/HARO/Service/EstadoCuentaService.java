package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Repository.EstadoCuentaRepository;

@Service
public class EstadoCuentaService {

    @Autowired
    private EstadoCuentaRepository repository;

    public List<EstadoCuenta> getAllEstadosCuenta() {
        return repository.findAll();
    }

    public Optional<EstadoCuenta> getEstadoCuentaById(int id) {
        return repository.findById((long) id);
    }

    public EstadoCuenta createEstadoCuenta(EstadoCuenta e) {
        return repository.save(e);
    }

    public EstadoCuenta updateEstadoCuenta(EstadoCuenta e) {
        return repository.save(e);
    }

    public void deleteEstadoCuenta(int id) {
        repository.deleteById((long) id);
    }
}
