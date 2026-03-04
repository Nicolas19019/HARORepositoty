package com.Aplication.HARO.Controller;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Service.EstudianteService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;

@RestController
@RequestMapping("/api/estudiantes")
@CrossOrigin(origins = "*")
public class EstudianteController {

	private final EstudianteService service;

	public EstudianteController(EstudianteService service) {
		this.service = service;
	}

	@GetMapping
	public List<Estudiante> getAll() {
		return service.getAllEstudiantes();
	}

	@GetMapping("/{id}")
	public Estudiante getById(@PathVariable long id) { // <-- long
		return service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
	}

	@PostMapping
	public ResponseEntity<Estudiante> create(@RequestBody Estudiante e) {
		e.setId(null); // <-- CLAVE: garantiza INSERT
		Estudiante created = service.createEstudiante(e);
		return ResponseEntity.created(URI.create("/api/estudiantes/" + created.getId())).body(created);
	}

	@PutMapping("/{id}")
	public Estudiante update(@PathVariable long id, @RequestBody Estudiante e) {
		// Mejor flujo: leer y aplicar cambios (ver service abajo)
		return service.updateEstudiante(id, e);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) { // <-- long
		service.deleteEstudiante(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/por-documento/{numeroDocumento}")
	public ResponseEntity<?> getPorDocumento(@PathVariable String numeroDocumento) {
		return service.buscarPorNumeroDocumento(numeroDocumento).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@RequestMapping(value = "/por-documento/{numeroDocumento}/existe", method = RequestMethod.HEAD)
	public ResponseEntity<Void> existePorDocumento(@PathVariable String numeroDocumento) {
		return service.existePorNumeroDocumento(numeroDocumento) ? ResponseEntity.ok().build()
				: ResponseEntity.notFound().build();
	}

	@RequestMapping(value = "/por-correo/{email}/existe", method = RequestMethod.HEAD)
	public ResponseEntity<Void> existePorCorreo(@PathVariable String email) {
		return service.existePorCorreo(email) ? ResponseEntity.ok().build()
				: ResponseEntity.notFound().build();
	}

	@GetMapping("/me")
	public ResponseEntity<?> getPerfilPropio(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autenticado");
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof DetallesUsuarioAplicacion ud)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido");
		}
		if (!"ESTUDIANTE".equalsIgnoreCase(ud.getRol())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Este endpoint solo aplica para estudiantes");
		}
		return service.getEstudianteById(ud.getId()).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Estudiante no encontrado"));
	}

	@GetMapping("/campos-requeridos")
	public Map<String, Object> camposRequeridos() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("tabla", "estudiante");
		out.put("requeridos", List.of(
				"nombre", "apellido", "tipoDocumento", "numeroDocumento",
				"categoria", "sede", "telefono", "email",
				"direccion", "usuario", "contrasena"));
		out.put("opcionales", List.of(
				"tipoEstudiante", "horas", "tipoPase",
				"aproboExamenTeorico", "estado", "visible"));

		Map<String, String> reglas = new LinkedHashMap<>();
		reglas.put("numeroDocumento", "Debe ser único");
		reglas.put("email", "Debe ser único");
		reglas.put("usuario", "Debe ser único");
		reglas.put("tipoPase", "Solo permite: carro, moto, carro,moto");
		reglas.put("horas", "No puede ser negativo");
		out.put("reglas", reglas);
		return out;
	}

}
