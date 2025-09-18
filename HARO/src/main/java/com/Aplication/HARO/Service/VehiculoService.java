package com.Aplication.HARO.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.VehiculoRepository;



@Service
public class VehiculoService {

    @Autowired
    private VehiculoRepository repository;

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
