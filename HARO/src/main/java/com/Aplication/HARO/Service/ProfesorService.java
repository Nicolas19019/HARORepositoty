package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Repository.ProfesorRepository;

@Service
public class ProfesorService {

    @Autowired
    private ProfesorRepository repository;

    public List<Profesor> getAllProfesores() {
        return repository.findAll();
    }

    public Optional<Profesor> getProfesorById(int id) {
        return repository.findById((long) id);
    }

    public Profesor createProfesor(Profesor p) {
        return repository.save(p);
    }

    public Profesor updateProfesor(Profesor p) {
        return repository.save(p);
    }

    public void deleteProfesor(int id) {
        repository.deleteById((long) id);
    }
}
