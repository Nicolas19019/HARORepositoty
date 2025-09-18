package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.Profesor;
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryProfesor;

@Service
public class ProfesorService {

    @Autowired
    private RepositoryProfesor repository;

    public List<Profesor> getAllProfesores() {
        return repository.findAll();
    }

    public Optional<Profesor> getProfesorById(int id) {
        return repository.findById(id);
    }

    public Profesor createProfesor(Profesor p) {
        return repository.save(p);
    }

    public Profesor updateProfesor(Profesor p) {
        return repository.save(p);
    }

    public void deleteProfesor(int id) {
        repository.deleteById(id);
    }
}
