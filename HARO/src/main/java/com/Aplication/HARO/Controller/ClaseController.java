package com.Aplication.HARO.Controller;


import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
// ✅
import com.Aplication.HARO.Model.Clase;

import com.Aplication.HARO.Service.ClaseService;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/clases-practicas")
@CrossOrigin(origins = "*")
public class ClaseController {

	private final ClaseService service;

	public ClaseController(ClaseService service) {
		this.service = service;
	}

	@GetMapping
	public List<Clase> getAll() {
		return service.getAllClases();
	}

	@GetMapping("/{id}")
	public Clase getById(@PathVariable int id) {
		return service.getClaseById(id).orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
	}

	// ClaseController.java (solo el método POST)
	@PostMapping
	public ResponseEntity<Clase> create(@RequestBody Clase c) {
	  c.setId(null);
	  var created = service.createClase(c);
	  return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}


	
	@PutMapping("/{id}")
	public Clase update(@PathVariable long id, @RequestBody Clase e) {
		e.setId(id);
		return service.updateClase(e);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable int id) {
		service.deleteClase(id);
		return ResponseEntity.noContent().build();
	}

	   @GetMapping("/fecha/{fecha}")
	    public List<Clase> listarPorFecha(
	            @PathVariable
	            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
	        return service.listarPorFecha(fecha);
	    }
}
