package com.Aplication.HARO.Controller;


import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
// ✅
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;
import com.Aplication.HARO.Repository.VehiculoRepository;
import com.Aplication.HARO.Security.AdminSedeGuard;

import com.Aplication.HARO.Service.ClaseService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clases-practicas")
@CrossOrigin(origins = "*")
public class ClaseController {

	private final ClaseService service;
	private final AdminSedeGuard adminSedeGuard;
	private final EstudianteRepository estudianteRepository;
	private final ProfesorRepository profesorRepository;
	private final VehiculoRepository vehiculoRepository;

	public ClaseController(ClaseService service,
						   AdminSedeGuard adminSedeGuard,
						   EstudianteRepository estudianteRepository,
						   ProfesorRepository profesorRepository,
						   VehiculoRepository vehiculoRepository) {
		this.service = service;
		this.adminSedeGuard = adminSedeGuard;
		this.estudianteRepository = estudianteRepository;
		this.profesorRepository = profesorRepository;
		this.vehiculoRepository = vehiculoRepository;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<Clase> getAll(Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		List<Clase> rows = service.getAllClases();
		if (adminCtx.superAdmin()) {
			return rows;
		}
		Map<Long, String> sedeByStudent = buildSedeByStudent(rows);
		return rows.stream()
				.filter(c -> adminSedeGuard.canAccess(adminCtx, sedeByStudent.get(c.getId_estudiante())))
				.toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Clase getById(@PathVariable int id, Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Clase c = service.getClaseById(id).orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, resolveSedeForClase(c));
		return c;
	}

	// ClaseController.java (solo el método POST)
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Clase> create(@RequestBody Clase c, Authentication authentication) {
	  AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
	  c.setId(null);
	  assertResourcesMatchAdminSede(adminCtx, c);
	  var created = service.createClase(c);
	  return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}


	
	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Clase update(@PathVariable long id, @RequestBody Clase e, Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Clase current = service.getClaseById(id)
				.orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, resolveSedeForClase(current));
		assertResourcesMatchAdminSede(adminCtx, e);
		e.setId(id);
		return service.updateClase(e);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> delete(@PathVariable int id, Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Clase current = service.getClaseById(id)
				.orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, resolveSedeForClase(current));
		service.deleteClase(id);
		return ResponseEntity.noContent().build();
	}

	   @GetMapping("/fecha/{fecha}")
	   @PreAuthorize("hasRole('ADMIN')")
	    public List<Clase> listarPorFecha(
	            @PathVariable
	            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
	            Authentication authentication) {
	        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
	        List<Clase> rows = service.listarPorFecha(fecha);
	        if (adminCtx.superAdmin()) {
	            return rows;
	        }
	        Map<Long, String> sedeByStudent = buildSedeByStudent(rows);
	        return rows.stream()
	                .filter(c -> adminSedeGuard.canAccess(adminCtx, sedeByStudent.get(c.getId_estudiante())))
	                .toList();
	    }

	private Map<Long, String> buildSedeByStudent(List<Clase> clases) {
		if (clases == null || clases.isEmpty() || estudianteRepository == null) {
			return Map.of();
		}
		Set<Long> ids = clases.stream()
				.map(Clase::getId_estudiante)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (ids.isEmpty()) return Map.of();

		Map<Long, String> out = new HashMap<>();
		for (Estudiante e : estudianteRepository.findAllById(ids)) {
			if (e != null && e.getId() != null) {
				out.put(e.getId(), e.getSede());
			}
		}
		return out;
	}

	private String resolveSedeForClase(Clase c) {
		if (c == null || estudianteRepository == null || c.getId_estudiante() == null) return "";
		return estudianteRepository.findById(c.getId_estudiante())
				.map(Estudiante::getSede)
				.orElse("");
	}

	private void assertResourcesMatchAdminSede(AdminSedeGuard.AdminCtx adminCtx, Clase c) {
		if (adminCtx == null || adminCtx.superAdmin() || c == null) return;

		Long idEstudiante = c.getId_estudiante();
		Long idProfesor = c.getId_profesor();
		String placaVehiculo = c.getPlaca_vehiculo();

		if (idEstudiante != null && estudianteRepository != null) {
			String sede = estudianteRepository.findById(idEstudiante)
					.map(Estudiante::getSede)
					.orElse("");
			adminSedeGuard.assertCanAccess(adminCtx, sede);
		}
		if (idProfesor != null && profesorRepository != null) {
			String sede = profesorRepository.findById(idProfesor)
					.map(Profesor::getSede)
					.orElse("");
			adminSedeGuard.assertCanAccess(adminCtx, sede);
		}
		if (placaVehiculo != null && !placaVehiculo.isBlank() && vehiculoRepository != null) {
			String sede = vehiculoRepository.findById(placaVehiculo.trim())
					.map(Vehiculo::getSede)
					.orElse("");
			adminSedeGuard.assertCanAccess(adminCtx, sede);
		}
	}
}
