package com.Aplication.HARO.Controller;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Service.PagoService;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/Pagoss")
@CrossOrigin(origins = "*")
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Pagos> getAll() {
        return service.getAllPagos();
    }

    @GetMapping("/{id}")
    public Pagos getById(@PathVariable int id) {
        return service.getPagoById(id)
                .orElseThrow(() -> new NoSuchElementException("Pagos no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<Pagos> create(@RequestBody Pagos p) {
        Pagos created = service.createPago(p);
        return ResponseEntity.created(URI.create("/api/Pagoss/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Pagos update(@PathVariable long id, @RequestBody Pagos p) {
        p.setId(id);
        return service.updatePago(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deletePago(id);
        return ResponseEntity.noContent().build();
    }
}
