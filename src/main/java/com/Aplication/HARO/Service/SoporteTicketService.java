package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.SoporteTicketSchemaInitializer;
import com.Aplication.HARO.Model.SoporteTicket;
import com.Aplication.HARO.Repository.SoporteTicketRepository;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Servicio de tickets de soporte.
 *
 * Crea solicitudes, lista tickets para administracion, actualiza estado y genera
 * resumenes de seguimiento.
 */
@Service
@Transactional
public class SoporteTicketService {

    /**
     * DTO de entrada para create.
     */
    public record CreateRequest(
            String tipo,
            String mensaje,
            String adjuntoNombre,
            String adjuntoUrl,
            String emailEstudiante,
            String nombreEstudiante
    ) {}

    /**
     * DTO de entrada para admin update.
     */
    public record AdminUpdateRequest(
            String estado,
            String notaInterna,
            String responsable
    ) {}

    /**
     * Resumen de apoyo para ticket.
     */
    public record TicketResumen(
            long total,
            long abiertos,
            long pendientes,
            long enProgreso,
            long resueltos,
            long archivados
    ) {}

    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Bogota");
    private static final Set<String> TIPOS_VALIDOS = Set.of("academico", "tecnico", "acceso", "otro");
    private static final Set<String> ESTADOS_VALIDOS = Set.of("pendiente", "en_progreso", "resuelto", "archivado");

    private final SoporteTicketRepository repository;
    private final SoporteTicketSchemaInitializer schemaInitializer;

    @Autowired
    public SoporteTicketService(SoporteTicketRepository repository,
                                SoporteTicketSchemaInitializer schemaInitializer) {
        this.repository = repository;
        this.schemaInitializer = schemaInitializer;
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public SoporteTicket crear(CreateRequest request) {
        ensureSchema();
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del ticket es obligatoria");
        }

        SoporteTicket ticket = new SoporteTicket();
        ticket.setTipo(normalizeTipo(request.tipo()));
        ticket.setMensaje(normalizeRequiredText(request.mensaje(), "mensaje", 8000));
        ticket.setAdjuntoNombre(normalizeOptionalText(request.adjuntoNombre(), 255));
        ticket.setAdjuntoUrl(normalizeOptionalText(request.adjuntoUrl(), 4000));
        ticket.setEmailEstudiante(normalizeEmail(request.emailEstudiante()));
        ticket.setNombreEstudiante(normalizeRequiredText(request.nombreEstudiante(), "nombreEstudiante", 180));
        ticket.setEstado("pendiente");
        ticket.setNotaInterna(null);
        ticket.setResponsable(null);
        return repository.save(ticket);
    }

    /**
     * Lista los registros solicitados segun los filtros recibidos.
     */
    @Transactional(readOnly = true)
    public List<SoporteTicket> listarAdmin(String estado,
                                           String tipo,
                                           LocalDate from,
                                           LocalDate to,
                                           String email,
                                           String q) {
        ensureSchema();
        validateDateRange(from, to);
        String normalizedEstado = normalizeFilterState(estado);
        String normalizedTipo = normalizeFilterTipo(tipo);
        String normalizedEmail = trim(email).toLowerCase(Locale.ROOT);
        String normalizedQuery = trim(q).toLowerCase(Locale.ROOT);

        Instant fromInstant = from == null ? null : from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(ticket -> normalizedEstado.isBlank() || normalizedEstado.equals(ticket.getEstado()))
                .filter(ticket -> normalizedTipo.isBlank() || normalizedTipo.equals(ticket.getTipo()))
                .filter(ticket -> normalizedEmail.isBlank() || trim(ticket.getEmailEstudiante()).toLowerCase(Locale.ROOT).contains(normalizedEmail))
                .filter(ticket -> fromInstant == null || !safeCreatedAt(ticket).isBefore(fromInstant))
                .filter(ticket -> toInstant == null || safeCreatedAt(ticket).isBefore(toInstant))
                .filter(ticket -> matchesQuery(ticket, normalizedQuery))
                .toList();
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public SoporteTicket actualizarAdmin(Long id, AdminUpdateRequest request) {
        ensureSchema();
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id de ticket invalido");
        }
        if (request == null) {
            throw new IllegalArgumentException("La actualizacion del ticket es obligatoria");
        }

        SoporteTicket ticket = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Ticket de soporte no encontrado: " + id));

        if (!trim(request.estado()).isBlank()) {
            ticket.setEstado(normalizeEstado(request.estado()));
        }
        if (request.notaInterna() != null) {
            ticket.setNotaInterna(normalizeOptionalText(request.notaInterna(), 8000));
        }
        if (request.responsable() != null) {
            ticket.setResponsable(normalizeOptionalText(request.responsable(), 180));
        }
        return repository.save(ticket);
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public TicketResumen getResumenAdmin() {
        ensureSchema();
        long total = repository.count();
        long pendientes = repository.countByEstadoIn(List.of("pendiente"));
        long enProgreso = repository.countByEstadoIn(List.of("en_progreso"));
        long resueltos = repository.countByEstadoIn(List.of("resuelto"));
        long archivados = repository.countByEstadoIn(List.of("archivado"));
        long abiertos = repository.countByEstadoIn(List.of("pendiente", "en_progreso"));
        return new TicketResumen(total, abiertos, pendientes, enProgreso, resueltos, archivados);
    }

    /**
     * Cuenta registros para el indicador solicitado.
     */
    @Transactional(readOnly = true)
    public long countAbiertos() {
        ensureSchema();
        return repository.countByEstadoIn(List.of("pendiente", "en_progreso"));
    }

    /**
     * Garantiza que exista la informacion necesaria antes de continuar.
     */
    private void ensureSchema() {
        if (schemaInitializer != null) {
            schemaInitializer.ensureSchema();
        }
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private boolean matchesQuery(SoporteTicket ticket, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }
        return contains(ticket.getNombreEstudiante(), normalizedQuery)
                || contains(ticket.getEmailEstudiante(), normalizedQuery)
                || contains(ticket.getTipo(), normalizedQuery)
                || contains(ticket.getEstado(), normalizedQuery)
                || contains(ticket.getMensaje(), normalizedQuery)
                || contains(ticket.getNotaInterna(), normalizedQuery)
                || contains(ticket.getResponsable(), normalizedQuery);
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private boolean contains(String value, String q) {
        return trim(value).toLowerCase(Locale.ROOT).contains(q);
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private Instant safeCreatedAt(SoporteTicket ticket) {
        return ticket.getCreatedAt() == null ? Instant.EPOCH : ticket.getCreatedAt();
    }

    /**
     * Valida la informacion recibida antes de continuar el proceso.
     */
    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to no puede ser menor que from");
        }
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeEmail(String value) {
        String out = trim(value).toLowerCase(Locale.ROOT);
        if (out.isBlank()) {
            throw new IllegalArgumentException("emailEstudiante es obligatorio");
        }
        if (!out.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("emailEstudiante no tiene un formato valido");
        }
        return out.length() > 180 ? out.substring(0, 180) : out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeTipo(String value) {
        String out = trim(value).toLowerCase(Locale.ROOT);
        if (out.isBlank()) {
            throw new IllegalArgumentException("tipo es obligatorio");
        }
        if (!TIPOS_VALIDOS.contains(out)) {
            throw new IllegalArgumentException("tipo invalido. Usa academico, tecnico, acceso u otro");
        }
        return out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeEstado(String value) {
        String out = trim(value).toLowerCase(Locale.ROOT);
        if (out.isBlank()) {
            throw new IllegalArgumentException("estado es obligatorio");
        }
        if (!ESTADOS_VALIDOS.contains(out)) {
            throw new IllegalArgumentException("estado invalido. Usa pendiente, en_progreso, resuelto o archivado");
        }
        return out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeFilterState(String value) {
        String out = trim(value).toLowerCase(Locale.ROOT);
        if (out.isBlank()) return "";
        return normalizeEstado(out);
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeFilterTipo(String value) {
        String out = trim(value).toLowerCase(Locale.ROOT);
        if (out.isBlank()) return "";
        return normalizeTipo(out);
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeRequiredText(String value, String field, int maxLength) {
        String out = trim(value).replaceAll("\\s+", " ");
        if (out.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return out.length() > maxLength ? out.substring(0, maxLength) : out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeOptionalText(String value, int maxLength) {
        String out = trim(value).replaceAll("\\s+", " ");
        if (out.isBlank()) {
            return null;
        }
        return out.length() > maxLength ? out.substring(0, maxLength) : out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
