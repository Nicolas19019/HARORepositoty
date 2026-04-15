package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.EstudianteModuloAcceso;
import com.Aplication.HARO.Repository.EstudianteModuloAccesoRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Servicio de acceso al modulo de aprendizaje.
 *
 * Registra altas e ingresos de estudiantes, calcula indicadores administrativos
 * y construye filas de seguimiento para el panel.
 */
@Service
@Transactional
public class EstudianteModuloAccesoService {

    /**
     * DTO de entrada para registro.
     */
    public record RegistroRequest(
            Long idEstudiante,
            String email,
            String usuario,
            String documento,
            String origen
    ) {}

    /**
     * DTO de salida para registro.
     */
    public record RegistroResponse(
            boolean ok,
            Long idEstudiante,
            boolean nuevoRegistro,
            Instant registradoEn,
            Instant primerIngresoEn,
            Instant ultimoIngresoEn,
            int totalIngresos,
            String origenRegistro
    ) {}

    /**
     * Resumen de indicadores administrativos del módulo de acceso.
     */
    public record ResumenAdmin(
            long estudiantesRegistrados,
            long estudiantesQueHanIngresado,
            long estudiantesPendientesPrimerIngreso
    ) {}

    /**
     * Registro de salida para estudiante modulo admin.
     */
    public record EstudianteModuloAdminRow(
            Long idEstudiante,
            String nombre,
            String email,
            String documento,
            String categoria,
            String estado,
            Boolean visible,
            Instant registradoEn,
            Instant primerIngresoEn,
            Instant ultimoIngresoEn,
            int totalIngresos,
            String origenRegistro
    ) {}

    private final EstudianteModuloAccesoRepository accesoRepository;
    private final EstudianteRepository estudianteRepository;

    public EstudianteModuloAccesoService(EstudianteModuloAccesoRepository accesoRepository,
                                         EstudianteRepository estudianteRepository) {
        this.accesoRepository = accesoRepository;
        this.estudianteRepository = estudianteRepository;
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public RegistroResponse registrarModulo(RegistroRequest request) {
        Estudiante estudiante = resolveEstudiante(request);
        EstudianteModuloAcceso acceso = accesoRepository.findByIdEstudiante(estudiante.getId())
                .orElseGet(EstudianteModuloAcceso::new);

        boolean nuevo = acceso.getId() == null;
        if (nuevo) {
            acceso.setIdEstudiante(estudiante.getId());
            acceso.setRegistradoEn(Instant.now());
        }
        if (trim(acceso.getOrigenRegistro()).isBlank()) {
            acceso.setOrigenRegistro(normalizeOrigen(request == null ? null : request.origen(), "FRONT_REGISTRO"));
        }

        EstudianteModuloAcceso saved = accesoRepository.save(acceso);
        return toRegistroResponse(saved, nuevo);
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public RegistroResponse registrarIngresoPorEstudiante(Long idEstudiante) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        Estudiante estudiante = estudianteRepository.findByIdAndVisibleTrue(idEstudiante)
                .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado o no visible: " + idEstudiante));

        EstudianteModuloAcceso acceso = accesoRepository.findByIdEstudiante(estudiante.getId())
                .orElseGet(EstudianteModuloAcceso::new);

        boolean nuevo = acceso.getId() == null;
        Instant now = Instant.now();
        if (nuevo) {
            acceso.setIdEstudiante(estudiante.getId());
            acceso.setRegistradoEn(now);
            acceso.setOrigenRegistro("AUTH_LOGIN_FALLBACK");
        }
        if (acceso.getPrimerIngresoEn() == null) {
            acceso.setPrimerIngresoEn(now);
        }
        acceso.setUltimoIngresoEn(now);
        acceso.setTotalIngresos(Math.max(0, safeInt(acceso.getTotalIngresos())) + 1);

        EstudianteModuloAcceso saved = accesoRepository.save(acceso);
        return toRegistroResponse(saved, nuevo);
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public ResumenAdmin getResumenAdmin() {
        long registrados = accesoRepository.count();
        long conIngreso = accesoRepository.countByPrimerIngresoEnIsNotNull();
        long pendientes = Math.max(0L, registrados - conIngreso);
        return new ResumenAdmin(registrados, conIngreso, pendientes);
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<EstudianteModuloAdminRow> getEstudiantesRegistradosModulo() {
        List<EstudianteModuloAdminRow> out = new ArrayList<>();
        for (EstudianteModuloAcceso acceso : accesoRepository.findAll()) {
            Long idEstudiante = acceso.getIdEstudiante();
            if (idEstudiante == null || idEstudiante <= 0) {
                continue;
            }

            Optional<Estudiante> estudianteOpt = estudianteRepository.findById(idEstudiante);
            if (estudianteOpt.isEmpty()) {
                continue;
            }

            Estudiante estudiante = estudianteOpt.get();
            out.add(new EstudianteModuloAdminRow(
                    idEstudiante,
                    nombreCompleto(estudiante),
                    trim(estudiante.getEmail()),
                    trim(estudiante.getNumeroDocumento()),
                    trim(estudiante.getCategoria()),
                    trim(estudiante.getEstado()),
                    estudiante.getVisible(),
                    acceso.getRegistradoEn(),
                    acceso.getPrimerIngresoEn(),
                    acceso.getUltimoIngresoEn(),
                    safeInt(acceso.getTotalIngresos()),
                    trim(acceso.getOrigenRegistro())
            ));
        }

        out.sort(
                Comparator
                        .comparing(EstudianteModuloAdminRow::ultimoIngresoEn,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EstudianteModuloAdminRow::registradoEn,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EstudianteModuloAdminRow::idEstudiante,
                                Comparator.nullsLast(Comparator.naturalOrder()))
        );
        return out;
    }

    /**
     * Obtiene el registro solicitado por identificador o criterio de busqueda.
     */
    @Transactional(readOnly = true)
    public Optional<EstudianteModuloAdminRow> getRegistroByEstudiante(Long idEstudiante) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        return getEstudiantesRegistradosModulo().stream()
                .filter(row -> idEstudiante.equals(row.idEstudiante()))
                .findFirst();
    }

    /**
     * Convierte la informacion del dominio al formato de salida requerido.
     */
    private RegistroResponse toRegistroResponse(EstudianteModuloAcceso acceso, boolean nuevo) {
        return new RegistroResponse(
                true,
                acceso.getIdEstudiante(),
                nuevo,
                acceso.getRegistradoEn(),
                acceso.getPrimerIngresoEn(),
                acceso.getUltimoIngresoEn(),
                safeInt(acceso.getTotalIngresos()),
                trim(acceso.getOrigenRegistro())
        );
    }

    /**
     * Resuelve el valor que debe usarse segun el contexto recibido.
     */
    private Estudiante resolveEstudiante(RegistroRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de registro es obligatoria");
        }

        Long idEstudiante = request.idEstudiante();
        if (idEstudiante != null && idEstudiante > 0) {
            return estudianteRepository.findByIdAndVisibleTrue(idEstudiante)
                    .orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado o no visible: " + idEstudiante));
        }

        String email = trim(request.email()).toLowerCase();
        if (!email.isBlank()) {
            return estudianteRepository.findByEmailNormalizadoVisible(email)
                    .orElseThrow(() -> new NoSuchElementException("No existe estudiante visible con el correo: " + email));
        }

        String usuario = trim(request.usuario());
        if (!usuario.isBlank()) {
            Estudiante estudiante = estudianteRepository.findByUsuarioIgnoreCase(usuario)
                    .orElseThrow(() -> new NoSuchElementException("No existe estudiante con el usuario: " + usuario));
            if (!Boolean.TRUE.equals(estudiante.getVisible())) {
                throw new NoSuchElementException("El estudiante asociado al usuario no esta visible: " + usuario);
            }
            return estudiante;
        }

        String documento = trim(request.documento());
        if (!documento.isBlank()) {
            return estudianteRepository.findByNumeroDocumentoAndVisibleTrue(documento)
                    .orElseThrow(() -> new NoSuchElementException("No existe estudiante visible con el documento: " + documento));
        }

        throw new IllegalArgumentException("Debes enviar idEstudiante, email, usuario o documento");
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String nombreCompleto(Estudiante estudiante) {
        String nombre = trim(estudiante == null ? null : estudiante.getNombre());
        String apellido = trim(estudiante == null ? null : estudiante.getApellido());
        String full = (nombre + " " + apellido).trim();
        if (!full.isBlank()) return full;
        if (!nombre.isBlank()) return nombre;
        if (!apellido.isBlank()) return apellido;
        return estudiante == null || estudiante.getId() == null ? "" : "Estudiante " + estudiante.getId();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeOrigen(String origen, String fallback) {
        String value = trim(origen);
        if (value.isBlank()) return fallback;
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private int safeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
