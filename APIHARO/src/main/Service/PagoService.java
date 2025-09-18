package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.Pago;
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryPago;

@Service
public class PagoService {

    @Autowired
    private RepositoryPago repository;

    public List<Pago> getAllPagos() {
        return repository.findAll();
    }

    public Optional<Pago> getPagoById(int id) {
        return repository.findById(id);
    }

    public Pago createPago(Pago p) {
        return repository.save(p);
    }

    public Pago updatePago(Pago p) {
        return repository.save(p);
    }

    public void deletePago(int id) {
        repository.deleteById(id);
    }
}
