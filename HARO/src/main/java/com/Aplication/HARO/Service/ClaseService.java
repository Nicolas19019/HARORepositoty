package com.Aplication.HARO.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Repository.ClaseRepository;


@Service
public class ClaseService {

    @Autowired
    private ClaseRepository repository;

    public List<Clase> getAllClases() {
        return repository.findAll();
    }

    public  Optional<Clase> getClaseById(long id) {
        return repository.findById(id);
    }

    public Clase createClase(Clase c) {
        return repository.save(c);
    }

    public  Clase updateClase(Clase c) {
        return repository.save(c);
    }

    public void deleteClase(int id) {
        repository.deleteById((long) id);
    }


}
