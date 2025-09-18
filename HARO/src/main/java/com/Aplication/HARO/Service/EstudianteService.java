package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;

@Service
public class EstudianteService {

    @Autowired
    private EstudianteRepository repository;

    public List<Estudiante> getAllEstudiantes() {
        return repository.findAll();
    }

    public Optional<Estudiante> getEstudianteById(long id) {
        return repository.findById( id);
    }

    public Estudiante createEstudiante(Estudiante e) {
        return repository.save(e);
    }

    public Estudiante updateEstudiante(Estudiante e) {
        return repository.save(e);
    }

    public void deleteEstudiante(int id) {
        repository.deleteById((long) id);
    }
}
