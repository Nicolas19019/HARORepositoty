package com.Aplication.HARO.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.VehiculoRepository;

@Service
@Transactional
public class VehiculoService {

    private final VehiculoRepository repository;

    public VehiculoService(VehiculoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Vehiculo> getAllVehiculos() {
        return repository.findByVisibleTrueOrderByPlacaAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Vehiculo> getVehiculoByPlaca(String placa) {
        return repository.findByPlacaAndVisibleTrue(placa);
    }

    public Vehiculo createVehiculo(Vehiculo v) {
        // En API, todo registro nuevo nace visible.
        v.setVisible(true);
        return repository.save(v);
    }

    public Vehiculo updateVehiculo(Vehiculo v) {
        Vehiculo db = repository.findById(v.getPlaca())
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + v.getPlaca()));

        if (v.getMarca() != null) db.setMarca(v.getMarca());
        if (v.getModelo() != null) db.setModelo(v.getModelo());
        if (v.getAnio() != null) db.setAnio(v.getAnio());
        if (v.getSede() != null) db.setSede(v.getSede());
        if (v.getEstado() != null) db.setEstado(v.getEstado());
        if (v.getVisible() != null) db.setVisible(v.getVisible());

        return repository.save(db);
    }

    public void deleteVehiculo(String placa) {
        Vehiculo db = repository.findById(placa)
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado: " + placa));
        db.setVisible(false);
        repository.save(db);
    }
}
