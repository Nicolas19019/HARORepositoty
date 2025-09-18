package com.Carritos.AcademiaCarros.Service.Mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Carritos.AcademiaCarros.Model.MySQL1.Vehiculo;
import com.Carritos.AcademiaCarros.Repository.MySQL1.RepositoryVehiculo;

@Service
public class VehiculoService {

    @Autowired
    private RepositoryVehiculo repository;

    public List<Vehiculo> getAllVehiculos() {
        return repository.findAll();
    }

    public Optional<Vehiculo> getVehiculoByPlaca(String placa) {
        return repository.findById(placa);
    }

    public Vehiculo createVehiculo(Vehiculo v) {
        return repository.save(v);
    }

    public Vehiculo updateVehiculo(Vehiculo v) {
        return repository.save(v);
    }

    public void deleteVehiculo(String placa) {
        repository.deleteById(placa);
    }
}
