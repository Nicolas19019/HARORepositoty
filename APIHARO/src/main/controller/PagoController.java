package com.Carritos.AcademiaCarros.Controller.MySQL1;

import com.Carritos.AcademiaCarros.Model.MySQL1.Pago;
import com.Carritos.AcademiaCarros.Service.Mysql.PagoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(origins = "*")
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Pago> getAll() {
        return service.getAllPagos();
    }

    @GetMapping("/{id}")
    public Pago getById(@PathVariable int id) {
        return service.getPagoById(id)
                .orElseThrow(() -> new NoSuchElementException("Pago no encontrado: " + id));
    }

    @PostMapping
    public ResponseEntity<Pago> create(@RequestBody Pago p) {
        Pago created = service.createPago(p);
        return ResponseEntity.created(URI.create("/api/pagos/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Pago update(@PathVariable int id, @RequestBody Pago p) {
        p.setId(id);
        return service.updatePago(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.deletePago(id);
        return ResponseEntity.noContent().build();
    }
}
