package com.Aplication.HARO.Controller;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Security.AdminSedeGuard;
import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Service.EstudianteService;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import com.Aplication.HARO.Service.VerificationService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;

/**
 * Controlador REST para estudiante.
 */
@RestController
@RequestMapping("/api/estudiantes")
@CrossOrigin(origins = "*")
public class EstudianteController {

	private final EstudianteService service;
	private final VerificationService verificationService;
	private final EstudianteModuloAccesoService estudianteModuloAccesoService;
	private final AdminSedeGuard adminSedeGuard;

	/**
	 * Inyecta las dependencias necesarias del controlador.
	 */
	public EstudianteController(EstudianteService service,
			VerificationService verificationService,
			EstudianteModuloAccesoService estudianteModuloAccesoService,
			AdminSedeGuard adminSedeGuard) {
		this.service = service;
		this.verificationService = verificationService;
		this.estudianteModuloAccesoService = estudianteModuloAccesoService;
		this.adminSedeGuard = adminSedeGuard;
	}

	/**
	 * DTO de entrada para registro modulo.
	 */
	public record RegistroModuloRequest(String email, String code, String password, String usuario, String origen) {}

/**
 * Lista los registros de estudiante.
 */
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<Estudiante> getAll(Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		List<Estudiante> out = service.getAllEstudiantes();
		if (!adminCtx.superAdmin()) {
			out = out.stream()
					.filter(e -> adminSedeGuard.canAccess(adminCtx, e.getSede()))
					.toList();
		}
		return out;
	}

/**
 * Obtiene un registro de estudiante por su identificador.
 */
	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Estudiante getById(@PathVariable long id, Authentication authentication) { // <-- long
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Estudiante e = service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, e.getSede());
		return e;
	}

/**
 * Crea un nuevo registro de estudiante.
 */
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Estudiante> create(@RequestBody Estudiante e, Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		e.setSede(adminSedeGuard.enforceRequestSede(adminCtx, e.getSede()));
		e.setId(null); // <-- CLAVE: garantiza INSERT
		Estudiante created = service.createEstudiante(e);
		return ResponseEntity.created(URI.create("/api/estudiantes/" + created.getId())).body(created);
	}

/**
 * Registra el acceso del estudiante al modulo.
 */
	@PostMapping("/registro-modulo")
	public ResponseEntity<?> registroModulo(@RequestBody RegistroModuloRequest req) {
		String email = req == null ? null : req.email();
		String code = req == null ? null : req.code();
		if (!verificationService.verifyEmailOtp(email, code)) {
			return ResponseEntity.badRequest().body("Codigo invalido o vencido");
		}

		Estudiante updated = service.activarCuentaModulo(
				email,
				req.password(),
				req.usuario());

		EstudianteModuloAccesoService.RegistroResponse acceso = estudianteModuloAccesoService.registrarModulo(
				new EstudianteModuloAccesoService.RegistroRequest(
						updated.getId(),
						updated.getEmail(),
						updated.getUsuario(),
						updated.getNumeroDocumento(),
						req.origen()
				)
		);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", true);
		body.put("message", "Cuenta del modulo activada correctamente");
		body.put("idEstudiante", updated.getId());
		body.put("email", updated.getEmail());
		body.put("usuario", updated.getUsuario());
		body.put("registroModulo", acceso);
		return ResponseEntity.ok(body);
	}

/**
 * Actualiza un registro existente de estudiante.
 */
	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Estudiante update(@PathVariable long id, @RequestBody Estudiante e, Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Estudiante current = service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
		if (e.getSede() != null) {
			e.setSede(adminSedeGuard.enforceRequestSede(adminCtx, e.getSede()));
		}
		return service.updateEstudiante(id, e);
	}

	/**
	 * Actualiza la foto de perfil de un estudiante.
	 */
	@PatchMapping("/{id}/foto-perfil")
	@PreAuthorize("hasRole('ADMIN')")
	public Estudiante updateFotoPerfil(@PathVariable long id,
									  @RequestBody Map<String, String> body,
									  Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Estudiante current = service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
		if (body == null || !body.containsKey("fotoPerfil")) {
			throw new IllegalArgumentException("Debes enviar fotoPerfil");
		}
		Estudiante patch = new Estudiante();
		patch.setFotoPerfil(body.get("fotoPerfil"));
		return service.updateEstudiante(id, patch);
	}

	/**
	 * Actualiza la marca de examen teorico aprobado del estudiante.
	 */
	@PatchMapping("/{id}/aprobo-examen-teorico")
	@PreAuthorize("hasRole('ADMIN')")
	public Estudiante updateAproboExamenTeorico(@PathVariable long id,
											   @RequestBody Map<String, Boolean> body,
											   Authentication authentication) {
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Estudiante current = service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
		if (body == null || !body.containsKey("aproboExamenTeorico")) {
			throw new IllegalArgumentException("Debes enviar aproboExamenTeorico");
		}
		Estudiante patch = new Estudiante();
		patch.setAproboExamenTeorico(body.get("aproboExamenTeorico"));
		return service.updateEstudiante(id, patch);
	}

/**
 * Elimina un registro de estudiante.
 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) { // <-- long
		AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
		Estudiante current = service.getEstudianteById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));
		adminSedeGuard.assertCanAccess(adminCtx, current.getSede());
		service.deleteEstudiante(id);
		return ResponseEntity.noContent().build();
	}

/**
 * Consulta un estudiante a partir de su numero de documento.
 */
	@GetMapping("/por-documento/{numeroDocumento}")
	public ResponseEntity<?> getPorDocumento(@PathVariable String numeroDocumento) {
		return service.buscarPorNumeroDocumento(numeroDocumento).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

/**
 * Verifica si ya existe un estudiante con el documento indicado.
 */
	@RequestMapping(value = "/por-documento/{numeroDocumento}/existe", method = RequestMethod.HEAD)
	public ResponseEntity<Void> existePorDocumento(@PathVariable String numeroDocumento) {
		return service.existePorNumeroDocumento(numeroDocumento) ? ResponseEntity.ok().build()
				: ResponseEntity.notFound().build();
	}

/**
 * Verifica si ya existe un estudiante con el correo indicado.
 */
	@RequestMapping(value = "/por-correo/{email}/existe", method = RequestMethod.HEAD)
	public ResponseEntity<Void> existePorCorreo(@PathVariable String email) {
		return service.existePorCorreo(email) ? ResponseEntity.ok().build()
				: ResponseEntity.notFound().build();
	}

/**
 * Consulta un estudiante a partir de su correo.
 */
	@GetMapping("/por-correo/{email}")
	public ResponseEntity<?> getPorCorreo(@PathVariable String email) {
		return service.buscarPorCorreo(email).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

/**
 * Obtiene el perfil del estudiante autenticado.
 */
	@GetMapping("/me")
	public ResponseEntity<?> getPerfilPropio(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autenticado");
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof DetallesUsuarioAplicacion ud)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token invÃ¡lido");
		}
		if (!"ESTUDIANTE".equalsIgnoreCase(ud.getRol())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Este endpoint solo aplica para estudiantes");
		}
		return service.getEstudianteById(ud.getId()).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Estudiante no encontrado"));
	}

/**
 * Expone los campos requeridos para el registro de estudiantes.
 */
	@GetMapping("/campos-requeridos")
	public Map<String, Object> camposRequeridos() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("tabla", "estudiante");
		out.put("requeridos", List.of(
				"nombre", "apellido", "tipoDocumento", "numeroDocumento",
				"categoria", "sede", "telefono", "email",
				"direccion", "usuario"));
		out.put("opcionales", List.of(
				"tipoEstudiante", "horas", "tipoPase",
				"aproboExamenTeorico", "estado", "visible", "fotoPerfil", "fechaMatricula", "origenMatricula", "contrasena", "consecutivo"));
		out.put("soloLectura", List.of("id", "fechaCreacion"));

		Map<String, String> reglas = new LinkedHashMap<>();
		reglas.put("numeroDocumento", "Debe ser Ãºnico");
		reglas.put("email", "Debe ser Ãºnico");
		reglas.put("usuario", "Debe ser Ãºnico");
		reglas.put("contrasena", "Opcional. Si se envia en texto plano, el backend la hashea. Si no se envia al crear, usa un valor por defecto.");
		reglas.put("tipoPase", "Solo permite: carro, moto, carro,moto");
		reglas.put("horas", "No puede ser negativo");
		reglas.put("consecutivo", "Si se envia en PUT /api/estudiantes/{id}, debe ser unico y mayor que cero.");
		reglas.put("fechaMatricula", "Si el estudiante queda matriculado y no se envia, el backend la asigna automaticamente.");
		reglas.put("origenMatricula", "Solo permite CHATBOT o PRESENCIAL. Si el estudiante queda matriculado y no se envia, el backend asigna PRESENCIAL en el flujo generico.");
		out.put("reglas", reglas);
		return out;
	}

}
