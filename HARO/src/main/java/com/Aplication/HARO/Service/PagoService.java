package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Repository.PagoRepository;

@Service
public class PagoService {

    @Autowired
    private PagoRepository repository;

    public List<Pagos> getAllPagos() {
        return repository.findAll();
    }

    public Optional<Pagos> getPagoById(int id) {
        return repository.findById((long) id);
    }

    public Pagos createPago(Pagos p) {
        return repository.save(p);
    }

    public Pagos updatePago(Pagos p) {
        return repository.save(p);
    }

    public void deletePago(int id) {
        repository.deleteById((long) id);
    }
}
