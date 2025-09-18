package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.Clase; // si tu entidad se llama ClasePractica, cambia aquí
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryClase;

@Service
public class ClaseService {

    @Autowired
    private RepositoryClase repository;

    public List<Clase> getAllClases() {
        return repository.findAll();
    }

    public Optional<Clase> getClaseById(int id) {
        return repository.findById(id);
    }

    public Clase createClase(Clase c) {
        return repository.save(c);
    }

    public Clase updateClase(Clase c) {
        return repository.save(c);
    }

    public void deleteClase(int id) {
        repository.deleteById(id);
    }
}
