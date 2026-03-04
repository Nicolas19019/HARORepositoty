package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Repository.ClaseRepository;
import com.Aplication.HARO.Repository.EstadoCuentaRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;
import com.Aplication.HARO.Repository.VehiculoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ArrayList;

@Service
@Transactional
public class ChatbotProcesoService {

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final EstudianteRepository estudianteRepository;
    private final EstudianteService estudianteService;
    private final EstadoCuentaRepository estadoCuentaRepository;
    private final ClaseRepository claseRepository;
    private final ProfesorRepository profesorRepository;
    private final VehiculoRepository vehiculoRepository;
    private final GoogleCalendarService googleCalendarService;

    @Value("${chatbot.payment.link:https://epayco-link.com}")
    private String defaultPaymentLink;

    @Value("${chatbot.booking.duration.minutes:120}")
    private int bookingDurationMinutes;

    @Value("${chatbot.booking.calendar.id:}")
    private String bookingCalendarId;

    @Value("${chatbot.booking.calendar.timezone:America/Bogota}")
    private String bookingCalendarTimezone;

    @Value("${chatbot.booking.cancel.min-hours:48}")
    private long bookingCancelMinHours;

    @Value("${chatbot.booking.cancel.late-fee:0}")
    private BigDecimal bookingCancelLateFee;

    @Value("${chatbot.pricing.a2:0}")
    private BigDecimal pricingA2;

    @Value("${chatbot.pricing.b1:0}")
    private BigDecimal pricingB1;

    @Value("${chatbot.pricing.c1:0}")
    private BigDecimal pricingC1;

    @Value("${chatbot.pricing.a2b1:0}")
    private BigDecimal pricingA2B1;

    @Value("${chatbot.pricing.a2b1c1:0}")
    private BigDecimal pricingA2B1C1;

    public record StudentAccessData(String documento, String email, String emailMasked) {}
    public record BookingResult(Clase clase, GoogleCalendarService.ReunionCreada reunionCalendario) {}
    public record SlotAvailability(int opcion, String hora, boolean disponible) {}
    public record CancellationResult(Clase clase,
                                     boolean multaAplicada,
                                     BigDecimal valorMulta,
                                     long horasRestantes,
                                     BigDecimal multasAcumuladas) {}

    public ChatbotProcesoService(ChatbotMatriculaProcesoRepository procesoRepository,
                                 EstudianteRepository estudianteRepository,
                                 EstudianteService estudianteService,
                                 EstadoCuentaRepository estadoCuentaRepository,
                                 ClaseRepository claseRepository,
                                 ProfesorRepository profesorRepository,
                                 VehiculoRepository vehiculoRepository,
                                 GoogleCalendarService googleCalendarService) {
        this.procesoRepository = procesoRepository;
        this.estudianteRepository = estudianteRepository;
        this.estudianteService = estudianteService;
        this.estadoCuentaRepository = estadoCuentaRepository;
        this.claseRepository = claseRepository;
        this.profesorRepository = profesorRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.googleCalendarService = googleCalendarService;
    }

    public ChatbotMatriculaProceso upsertDraft(String phone,
                                               String nombreCompleto,
                                               String documento,
                                               String categoria,
                                               String email,
                                               String telefono) {
        String doc = normalizeDoc(documento);
        String mail = normalizeEmail(email);
        String cat = normalizeCategoria(categoria);

        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumento(doc)
                .orElseGet(ChatbotMatriculaProceso::new);

        proceso.setPhone(trim(phone));
        proceso.setNombreCompleto(collapseSpaces(nombreCompleto));
        proceso.setNumeroDocumento(doc);
        proceso.setCategoria(cat);
        proceso.setEmail(mail);
        proceso.setTelefono(normalizePhone(telefono));
        proceso.setExpectedAmount(resolveExpectedAmountByCategory(cat));
        proceso.setFlowStatus("DRAFT");

        if (proceso.getPaymentStatus() == null || proceso.getPaymentStatus().isBlank()) {
            proceso.setPaymentStatus("PENDING");
        }
        if (proceso.getContractStatus() == null || proceso.getContractStatus().isBlank()) {
            proceso.setContractStatus("PENDING_SIGNATURE");
        }

        return procesoRepository.save(proceso);
    }

    public ChatbotMatriculaProceso markPaymentPending(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("PENDING");
        p.setExpectedAmount(resolveExpectedAmount(p));
        p.setFlowStatus("PENDING_PAYMENT");
        if (p.getPaymentLink() == null || p.getPaymentLink().isBlank()) {
            p.setPaymentLink(defaultPaymentLink);
        }
        return procesoRepository.save(p);
    }

    public ChatbotMatriculaProceso markPaymentApproved(String documento) {
        return markPaymentApproved(documento, null);
    }

    public ChatbotMatriculaProceso markPaymentApproved(String documento, BigDecimal amountPaid) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("APPROVED");
        p.setFlowStatus("PAYMENT_APPROVED");
        if (amountPaid != null && amountPaid.signum() >= 0) {
            p.setPaymentAmount(amountPaid);
        } else if (p.getPaymentAmount() == null) {
            p.setPaymentAmount(resolvePaidAmountForEstadoCuenta(p, resolveExpectedAmount(p)));
        }
        return procesoRepository.save(p);
    }

    public ChatbotMatriculaProceso markContractLinkSent(String documento, String contractLink) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setContractLink(trim(contractLink));
        p.setContractStatus("LINK_SENT");
        p.setFlowStatus("CONTRACT_LINK_SENT");
        return procesoRepository.save(p);
    }

    public ChatbotMatriculaProceso markContractSignedByEmail(String email) {
        String mail = normalizeEmail(email);
        ChatbotMatriculaProceso proceso = procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .orElseThrow(() -> new NoSuchElementException("No hay proceso de matrícula para " + mail));
        proceso.setContractStatus("SIGNED");
        proceso.setFlowStatus("CONTRACT_SIGNED");
        proceso.setContractSignedAt(Instant.now());
        return procesoRepository.save(proceso);
    }

    @Transactional(readOnly = true)
    public boolean isContractSigned(String documento) {
        return procesoRepository.findByNumeroDocumento(normalizeDoc(documento))
                .map(p -> "SIGNED".equalsIgnoreCase(p.getContractStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String getPaymentLink(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        if (p.getPaymentLink() == null || p.getPaymentLink().isBlank()) {
            return defaultPaymentLink;
        }
        return p.getPaymentLink();
    }

    public ChatbotMatriculaProceso setPaymentLinkIfMissing(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        if (p.getPaymentLink() == null || p.getPaymentLink().isBlank()) {
            p.setPaymentLink(defaultPaymentLink);
            return procesoRepository.save(p);
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findProcesoByDocumento(String documento) {
        return procesoRepository.findByNumeroDocumento(normalizeDoc(documento));
    }

    public Long createStudentFromSignedContract(String documento) {
        String doc = normalizeDoc(documento);
        ChatbotMatriculaProceso proceso = getByDocumentoOrThrow(doc);

        if (!"SIGNED".equalsIgnoreCase(proceso.getContractStatus())) {
            throw new IllegalStateException("El contrato aún no está firmado para el documento " + doc);
        }

        Optional<Estudiante> existing = estudianteRepository.findByNumeroDocumento(doc);
        if (existing.isPresent()) {
            Estudiante e = existing.get();
            ensureEstadoCuentaForStudent(proceso, e.getId());
            proceso.setStudentId(e.getId());
            proceso.setFlowStatus("STUDENT_CREATED");
            if (proceso.getEnrolledAt() == null) {
                proceso.setEnrolledAt(Instant.now());
            }
            procesoRepository.save(proceso);
            return e.getId();
        }

        Estudiante nuevo = new Estudiante();
        String[] nombrePartes = splitName(proceso.getNombreCompleto());
        nuevo.setNombre(nombrePartes[0]);
        nuevo.setApellido(nombrePartes[1]);
        nuevo.setNumeroDocumento(doc);
        nuevo.setCategoria(proceso.getCategoria());
        nuevo.setTipoPase(toTipoPase(proceso.getCategoria()));
        nuevo.setTipoDocumento("CC");
        nuevo.setTelefono(proceso.getTelefono());
        nuevo.setEmail(proceso.getEmail());
        nuevo.setTipoEstudiante("matriculado");
        nuevo.setEstado("Activo");
        nuevo.setVisible(true);
        nuevo.setUsuario(generateUniqueUsername(proceso.getEmail(), doc));
        // Si no llega contraseña, EstudianteService asigna default seguro
        nuevo.setContrasena(null);

        Estudiante created = estudianteService.createEstudiante(nuevo);
        ensureEstadoCuentaForStudent(proceso, created.getId());

        proceso.setStudentId(created.getId());
        proceso.setFlowStatus("STUDENT_CREATED");
        proceso.setEnrolledAt(Instant.now());
        procesoRepository.save(proceso);
        return created.getId();
    }

    public Long createStudentFromSignedContract(String documento, String sede) {
        Long studentId = createStudentFromSignedContract(documento);
        String sedeValue = trim(sede);
        if (!sedeValue.isBlank()) {
            estudianteRepository.findById(studentId).ifPresent(e -> {
                e.setSede(sedeValue);
                estudianteRepository.save(e);
            });
        }
        return studentId;
    }

    @Transactional(readOnly = true)
    public StudentAccessData requireStudentAccessData(String documento) {
        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));

        String email = trim(estudiante.getEmail()).toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new IllegalStateException("El estudiante no tiene correo registrado para verificación OTP");
        }

        return new StudentAccessData(
                estudiante.getNumeroDocumento(),
                email,
                maskEmail(email)
        );
    }

    @Transactional(readOnly = true)
    public List<Clase> listAgendaByDocumento(String documento) {
        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));
        return claseRepository.findAgendaByIdEstudiante(estudiante.getId());
    }

    @Transactional(readOnly = true)
    public List<Clase> listUpcomingClassesForCancellation(String documento) {
        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));
        ZoneId zone = resolveBookingZone();
        ZonedDateTime now = ZonedDateTime.now(zone);

        return claseRepository.findAgendaByIdEstudiante(estudiante.getId()).stream()
                .filter(c -> !isCanceledState(c.getEstado()))
                .filter(c -> ZonedDateTime.of(c.getFecha(), c.getHoraInicio(), zone).isAfter(now))
                .toList();
    }

    public BookingResult bookPracticalClass(String documento, LocalDate fecha, LocalTime horaInicio) {
        if (fecha == null || horaInicio == null) {
            throw new IllegalArgumentException("Fecha y hora son requeridas");
        }
        if (!fecha.isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("La fecha debe ser hoy o futura");
        }

        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));

        validatePracticalClassEligibility(estudiante);

        LocalTime horaFin = horaInicio.plusMinutes(Math.max(30, bookingDurationMinutes));

        if (claseRepository.existsStudentOverlap(estudiante.getId(), fecha, horaInicio, horaFin)) {
            throw new IllegalStateException("El estudiante ya tiene una clase en ese horario");
        }

        if (!hasAvailableProfesor(fecha, horaInicio, horaFin) || !hasAvailableVehiculo(fecha, horaInicio, horaFin)) {
            throw new IllegalStateException("Ese horario está ocupado. Elige otra hora.");
        }

        Profesor profesor = pickAvailableProfesor(fecha, horaInicio, horaFin)
                .orElseThrow(() -> new IllegalStateException("No hay profesor disponible para ese horario"));

        Vehiculo vehiculo = pickAvailableVehiculo(fecha, horaInicio, horaFin)
                .orElseThrow(() -> new IllegalStateException("No hay vehículo disponible para agendar la práctica"));

        Clase clase = new Clase();
        clase.setId_estudiante(estudiante.getId());
        clase.setId_profesor(profesor.getId());
        clase.setPlaca_vehiculo(vehiculo.getPlaca());
        clase.setFecha(fecha);
        clase.setHoraInicio(horaInicio);
        clase.setHoraFin(horaFin);
        clase.setEstado("Programada");
        clase.setEstadoClase("Pendiente");

        Clase saved = claseRepository.save(clase);
        GoogleCalendarService.ReunionCreada reunion = createPracticalClassCalendarEvent(estudiante, profesor, saved);
        return new BookingResult(saved, reunion);
    }

    public CancellationResult cancelPracticalClass(String documento, Long idClase) {
        if (idClase == null || idClase <= 0) {
            throw new IllegalArgumentException("ID de clase invalido");
        }

        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));

        Clase clase = claseRepository.findById(idClase)
                .orElseThrow(() -> new NoSuchElementException("No existe una clase con ID " + idClase));

        if (!Objects.equals(clase.getId_estudiante(), estudiante.getId())) {
            throw new IllegalStateException("Esa clase no pertenece al estudiante autenticado");
        }

        if (isCanceledState(clase.getEstado())) {
            throw new IllegalStateException("La clase ya estaba cancelada");
        }

        ZoneId zone = resolveBookingZone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime inicioClase = ZonedDateTime.of(clase.getFecha(), clase.getHoraInicio(), zone);

        if (!inicioClase.isAfter(now)) {
            throw new IllegalStateException("Solo puedes cancelar clases futuras");
        }

        long minutesRemaining = Duration.between(now, inicioClase).toMinutes();
        long thresholdMinutes = Math.max(1, bookingCancelMinHours) * 60;
        boolean aplicaMulta = minutesRemaining < thresholdMinutes;
        BigDecimal valorMulta = aplicaMulta ? safeAmount(bookingCancelLateFee) : BigDecimal.ZERO;
        BigDecimal multasAcumuladas = BigDecimal.ZERO;

        if (aplicaMulta) {
            EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(estudiante.getId())
                    .orElseGet(EstadoCuenta::new);
            estadoCuenta.setIdEstudiante(estudiante.getId());
            if (estadoCuenta.getMontoTotal() == null) estadoCuenta.setMontoTotal(BigDecimal.ZERO);
            if (estadoCuenta.getMontoPagado() == null) estadoCuenta.setMontoPagado(BigDecimal.ZERO);
            if (estadoCuenta.getMultas() == null) estadoCuenta.setMultas(BigDecimal.ZERO);
            estadoCuenta.setMultas(estadoCuenta.getMultas().add(valorMulta));
            multasAcumuladas = estadoCuentaRepository.save(estadoCuenta).getMultas();
        }

        clase.setEstado("Cancelada");
        clase.setEstadoClase(aplicaMulta ? "Cancelada con multa" : "Cancelada");
        Clase saved = claseRepository.save(clase);

        long horasRestantes = Math.max(0, minutesRemaining / 60);
        return new CancellationResult(saved, aplicaMulta, valorMulta, horasRestantes, multasAcumuladas);
    }

    @Transactional(readOnly = true)
    public List<SlotAvailability> listPracticalSlotAvailability(String documento, LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Fecha requerida para consultar disponibilidad");
        }
        if (fecha.isBefore(LocalDate.now())) {
            return List.of();
        }

        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));
        validatePracticalClassEligibility(estudiante);

        List<LocalTime> slots = defaultPracticalSlots();
        List<SlotAvailability> out = new ArrayList<>();
        int idx = 1;
        for (LocalTime slot : slots) {
            LocalTime fin = slot.plusMinutes(Math.max(30, bookingDurationMinutes));
            boolean disponible = isPracticalSlotAvailable(estudiante, fecha, slot, fin);
            out.add(new SlotAvailability(idx, formatHour(slot), disponible));
            idx++;
        }
        return out;
    }

    @Transactional(readOnly = true)
    public boolean isPracticalSlotAvailable(String documento, LocalDate fecha, LocalTime horaInicio) {
        if (fecha == null || horaInicio == null) return false;
        if (fecha.isBefore(LocalDate.now())) return false;

        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));
        validatePracticalClassEligibility(estudiante);

        LocalTime horaFin = horaInicio.plusMinutes(Math.max(30, bookingDurationMinutes));
        return isPracticalSlotAvailable(estudiante, fecha, horaInicio, horaFin);
    }

    private boolean isPracticalSlotAvailable(Estudiante estudiante,
                                             LocalDate fecha,
                                             LocalTime horaInicio,
                                             LocalTime horaFin) {
        if (claseRepository.existsStudentOverlap(estudiante.getId(), fecha, horaInicio, horaFin)) {
            return false;
        }
        if (!hasAvailableProfesor(fecha, horaInicio, horaFin)) {
            return false;
        }
        return hasAvailableVehiculo(fecha, horaInicio, horaFin);
    }

    private void validatePracticalClassEligibility(Estudiante estudiante) {
        if (!Boolean.TRUE.equals(estudiante.getAproboExamenTeorico())) {
            throw new IllegalStateException("Debes aprobar el examen teorico para agendar clases practicas");
        }

        EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(estudiante.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No encontramos estado de cuenta del estudiante. Para agendar, el estado debe estar en Pagado."
                ));

        if (!isPazYSalvo(estadoCuenta.getEstado())) {
            String estadoActual = safe(estadoCuenta.getEstado());
            throw new IllegalStateException(
                    "No puedes agendar clase practica porque tu estado de cuenta esta en '" + estadoActual + "'. " +
                            "Para agendar debe estar en 'Pagado'."
            );
        }
    }

    private boolean isPazYSalvo(String estado) {
        String normalized = Normalizer.normalize(trim(estado), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "");

        return "PAGADO".equals(normalized)
                || "PAZYSALVO".equals(normalized)
                || "ALDIA".equals(normalized);
    }

    private void ensureEstadoCuentaForStudent(ChatbotMatriculaProceso proceso, Long studentId) {
        if (proceso == null || studentId == null) return;

        BigDecimal expected = resolveExpectedAmount(proceso);
        BigDecimal paid = resolvePaidAmountForEstadoCuenta(proceso, expected);

        // Evita inconsistencias cuando el pago reportado supera el valor configurado.
        BigDecimal total = expected.max(paid);

        EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(studentId)
                .orElseGet(EstadoCuenta::new);
        estadoCuenta.setIdEstudiante(studentId);
        estadoCuenta.setMontoTotal(total);
        estadoCuenta.setMontoPagado(paid);
        if (estadoCuenta.getMultas() == null) {
            estadoCuenta.setMultas(BigDecimal.ZERO);
        }

        estadoCuentaRepository.save(estadoCuenta);
    }

    private BigDecimal resolveExpectedAmount(ChatbotMatriculaProceso proceso) {
        if (proceso.getExpectedAmount() != null && proceso.getExpectedAmount().signum() >= 0) {
            return proceso.getExpectedAmount();
        }
        BigDecimal byCategory = resolveExpectedAmountByCategory(proceso.getCategoria());
        proceso.setExpectedAmount(byCategory);
        return byCategory;
    }

    private BigDecimal resolveExpectedAmountByCategory(String categoria) {
        String normalized = normalizeCategoria(categoria);
        return switch (normalized) {
            case "A2" -> safeAmount(pricingA2);
            case "B1" -> safeAmount(pricingB1);
            case "C1" -> safeAmount(pricingC1);
            case "A2 y B1" -> safeAmount(pricingA2B1);
            case "A2 y C1" -> safeAmount(pricingA2).add(safeAmount(pricingC1));
            case "B1 y C1" -> safeAmount(pricingB1).add(safeAmount(pricingC1));
            case "A2, B1 y C1" -> safeAmount(pricingA2B1C1);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal resolvePaidAmountForEstadoCuenta(ChatbotMatriculaProceso proceso, BigDecimal expectedAmount) {
        if (proceso.getPaymentAmount() != null && proceso.getPaymentAmount().signum() >= 0) {
            return proceso.getPaymentAmount();
        }
        if ("APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) {
            return expectedAmount;
        }
        return BigDecimal.ZERO;
    }

    private Optional<Profesor> pickAvailableProfesor(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        List<Profesor> activos = profesorRepository.findByVisibleTrueOrderByIdAsc();
        return activos.stream()
                .filter(p -> !claseRepository.existsProfesorOverlap(p.getId(), fecha, horaInicio, horaFin))
                .min(Comparator.comparing(Profesor::getId));
    }

    private Optional<Vehiculo> pickAvailableVehiculo(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        List<Vehiculo> disponibles = vehiculoRepository.findByVisibleTrueAndEstadoIgnoreCaseOrderByPlacaAsc("Disponible");
        for (Vehiculo v : disponibles) {
            if (!claseRepository.existsVehiculoOverlap(v.getPlaca(), fecha, horaInicio, horaFin)) {
                return Optional.of(v);
            }
        }

        List<Vehiculo> activos = vehiculoRepository.findByVisibleTrueOrderByPlacaAsc();
        for (Vehiculo v : activos) {
            if (!claseRepository.existsVehiculoOverlap(v.getPlaca(), fecha, horaInicio, horaFin)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    private boolean hasAvailableProfesor(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return pickAvailableProfesor(fecha, horaInicio, horaFin).isPresent();
    }

    private boolean hasAvailableVehiculo(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return pickAvailableVehiculo(fecha, horaInicio, horaFin).isPresent();
    }

    private ZoneId resolveBookingZone() {
        String raw = trim(bookingCalendarTimezone);
        try {
            return ZoneId.of(raw.isBlank() ? "America/Bogota" : raw);
        } catch (Exception e) {
            return ZoneId.of("America/Bogota");
        }
    }

    private boolean isCanceledState(String estado) {
        String normalized = Normalizer.normalize(trim(estado), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "");
        return normalized.contains("CANCEL");
    }

    private List<LocalTime> defaultPracticalSlots() {
        return List.of(
                LocalTime.of(6, 0),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0)
        );
    }

    private String formatHour(LocalTime hour) {
        return hour == null ? "" : hour.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private GoogleCalendarService.ReunionCreada createPracticalClassCalendarEvent(Estudiante estudiante,
                                                                                   Profesor profesor,
                                                                                   Clase clase) {
        ZoneId zone = resolveBookingZone();
        ZonedDateTime inicio = ZonedDateTime.of(clase.getFecha(), clase.getHoraInicio(), zone);
        ZonedDateTime fin = ZonedDateTime.of(clase.getFecha(), clase.getHoraFin(), zone);

        String titulo = "Clase práctica HARO - " + safe(estudiante.getNombre()) + " " + safe(estudiante.getApellido());
        String descripcion =
                "Clase práctica agendada desde chatbot.\n" +
                        "Documento estudiante: " + safe(estudiante.getNumeroDocumento()) + "\n" +
                        "Profesor ID: " + profesor.getId() + "\n" +
                        "Vehículo: " + safe(clase.getPlaca_vehiculo()) + "\n" +
                        "Clase ID: " + clase.getId();

        List<String> asistentes = new ArrayList<>();
        if (!trim(estudiante.getEmail()).isBlank()) {
            asistentes.add(trim(estudiante.getEmail()).toLowerCase(Locale.ROOT));
        }
        String mailProfesor = !trim(profesor.getEmail()).isBlank() ? profesor.getEmail() : profesor.getCorreo();
        if (!trim(mailProfesor).isBlank()) {
            asistentes.add(trim(mailProfesor).toLowerCase(Locale.ROOT));
        }

        String calendarId = trim(bookingCalendarId);
        return googleCalendarService.crearReunion(
                titulo,
                descripcion,
                OffsetDateTime.from(inicio).toString(),
                OffsetDateTime.from(fin).toString(),
                zone.getId(),
                asistentes,
                calendarId.isBlank() ? null : calendarId
        );
    }

    private ChatbotMatriculaProceso getByDocumentoOrThrow(String documento) {
        String doc = normalizeDoc(documento);
        return procesoRepository.findByNumeroDocumento(doc)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matrícula para documento " + doc));
    }

    private String[] splitName(String fullName) {
        String clean = collapseSpaces(fullName);
        if (clean.isBlank()) return new String[]{"Estudiante", ""};
        String[] parts = clean.split(" ", 2);
        if (parts.length == 1) return new String[]{parts[0], ""};
        return parts;
    }

    private String generateUniqueUsername(String email, String documento) {
        String base;
        String e = trim(email);
        if (!e.isBlank() && e.contains("@")) {
            base = e.substring(0, e.indexOf('@'));
        } else {
            String doc = normalizeDoc(documento);
            base = "est" + doc.substring(Math.max(0, doc.length() - 6));
        }

        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) base = "estudiante";

        String candidate = base;
        int i = 1;
        while (estudianteRepository.existsByUsuarioIgnoreCase(candidate)) {
            candidate = base + i;
            i++;
        }
        return candidate;
    }

    private String toTipoPase(String categoria) {
        String v = normalizeCategoria(categoria);
        boolean hasA2 = v.contains("A2");
        boolean hasB1 = v.contains("B1");
        boolean hasC1 = v.contains("C1");
        boolean hasCarro = hasB1 || hasC1;

        if (hasA2 && hasCarro) return "carro,moto";
        if (hasA2) return "moto";
        return "carro";
    }

    private String normalizeCategoria(String categoria) {
        String c = trim(categoria).toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("+", "Y")
                .replace("/", "Y")
                .replace(",", "Y");

        while (c.contains("YY")) c = c.replace("YY", "Y");

        if ("A2".equals(c)) return "A2";
        if ("B1".equals(c)) return "B1";
        if ("C1".equals(c)) return "C1";

        boolean hasA2 = c.contains("A2");
        boolean hasB1 = c.contains("B1");
        boolean hasC1 = c.contains("C1");

        if (hasA2 && hasB1 && hasC1) return "A2, B1 y C1";
        if (hasA2 && hasB1) return "A2 y B1";
        if (hasA2 && hasC1) return "A2 y C1";
        if (hasB1 && hasC1) return "B1 y C1";
        if (hasA2) return "A2";
        if (hasB1) return "B1";
        if (hasC1) return "C1";
        return "A2 y B1";
    }

    private String normalizeDoc(String doc) {
        String out = trim(doc).replaceAll("\\D+", "");
        if (out.length() < 5) {
            throw new IllegalArgumentException("Documento inválido");
        }
        return out;
    }

    private String normalizeEmail(String email) {
        String out = trim(email).toLowerCase(Locale.ROOT);
        if (out.isBlank() || !out.contains("@")) {
            throw new IllegalArgumentException("Email inválido");
        }
        return out;
    }

    private String normalizePhone(String phone) {
        String out = trim(phone).replaceAll("[^0-9+]", "");
        if (out.isBlank()) {
            throw new IllegalArgumentException("Teléfono requerido");
        }
        return out;
    }

    private String collapseSpaces(String v) {
        return trim(v).replaceAll("\\s+", " ");
    }

    private BigDecimal safeAmount(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO;
        return value;
    }

    private String safe(String v) {
        String out = trim(v);
        return out.isBlank() ? "N/A" : out;
    }

    private String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private String maskEmail(String email) {
        String e = trim(email).toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        String user = e.substring(0, at);
        String domain = e.substring(at);
        return user.charAt(0) + "***" + domain;
    }
}
