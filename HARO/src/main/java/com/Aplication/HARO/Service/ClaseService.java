package com.Aplication.HARO.Service;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Repository.ClaseRepository;


@Service
@Transactional
public class ClaseService {

    private final ClaseRepository repository;

    public ClaseService(ClaseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Clase> listarPorFecha(LocalDate fecha) {
	        return repository.findByFecha(fecha);
	    }

    @Transactional(readOnly = true)
    public List<Clase> listarPorRango(LocalDate desde, LocalDate hasta) {
	        return repository.findByFechaBetween(desde, hasta);
	    }

    @Transactional(readOnly = true)
    public List<Clase> getAllClases() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Clase> getClaseById(long id) {
        return repository.findById(id);
    }

    public Clase createClase(Clase c) {
        return repository.save(c);
    }

    public Clase updateClase(Clase c) {
        return repository.save(c);
    }

    public void deleteClase(int id) {
        repository.deleteById((long) id);
    }
}
