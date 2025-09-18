package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.Estudiante;
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryEstudiante;

@Service
public class EstudianteService {

    @Autowired
    private RepositoryEstudiante repository;

    public List<Estudiante> getAllEstudiantes() {
        return repository.findAll();
    }

    public Optional<Estudiante> getEstudianteById(int id) {
        return repository.findById(id);
    }

    public Estudiante createEstudiante(Estudiante e) {
        return repository.save(e);
    }

    public Estudiante updateEstudiante(Estudiante e) {
        return repository.save(e);
    }

    public void deleteEstudiante(int id) {
        repository.deleteById(id);
    }
}
