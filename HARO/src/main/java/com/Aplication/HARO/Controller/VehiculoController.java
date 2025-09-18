package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Service.VehiculoService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/vehiculos")
@CrossOrigin(origins = "*")
public class VehiculoController {

    private final VehiculoService service;

    public VehiculoController(VehiculoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Vehiculo> getAll() {
        return service.getAllVehiculos();
    }

    @GetMapping("/{placa}")
    public Vehiculo getByPlaca(@PathVariable String placa) {
        return service.getVehiculoByPlaca(placa)
                .orElseThrow(() -> new NoSuchElementException("Vehículo no encontrado: " + placa));
    }

    @PostMapping
    public ResponseEntity<Vehiculo> create(@RequestBody Vehiculo v) {
        Vehiculo created = service.createVehiculo(v);
        return ResponseEntity.created(URI.create("/api/vehiculos/" + created.getPlaca())).body(created);
    }

    @PutMapping("/{placa}")
    public Vehiculo update(@PathVariable String placa, @RequestBody Vehiculo v) {
        v.setPlaca(placa);
        return service.updateVehiculo(v);
    }

    @DeleteMapping("/{placa}")
    public ResponseEntity<Void> delete(@PathVariable String placa) {
        service.deleteVehiculo(placa);
        return ResponseEntity.noContent().build();
    }
}
