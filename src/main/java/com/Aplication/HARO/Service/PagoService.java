package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Repository.PagoRepository;

@Service
@Transactional
public class PagoService {

    private final PagoRepository repository;

    public PagoService(PagoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Pagos> getAllPagos() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
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
