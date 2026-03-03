package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Repository.ClaseRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;
import com.Aplication.HARO.Repository.VehiculoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class ChatbotProcesoService {

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final EstudianteRepository estudianteRepository;
    private final EstudianteService estudianteService;
    private final ClaseRepository claseRepository;
    private final ProfesorRepository profesorRepository;
    private final VehiculoRepository vehiculoRepository;

    @Value("${chatbot.payment.link:https://epayco-link.com}")
    private String defaultPaymentLink;

    @Value("${chatbot.booking.duration.minutes:120}")
    private int bookingDurationMinutes;

    public record StudentAccessData(String documento, String email, String emailMasked) {}

    public ChatbotProcesoService(ChatbotMatriculaProcesoRepository procesoRepository,
                                 EstudianteRepository estudianteRepository,
                                 EstudianteService estudianteService,
                                 ClaseRepository claseRepository,
                                 ProfesorRepository profesorRepository,
                                 VehiculoRepository vehiculoRepository) {
        this.procesoRepository = procesoRepository;
        this.estudianteRepository = estudianteRepository;
        this.estudianteService = estudianteService;
        this.claseRepository = claseRepository;
        this.profesorRepository = profesorRepository;
        this.vehiculoRepository = vehiculoRepository;
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
        p.setFlowStatus("PENDING_PAYMENT");
        if (p.getPaymentLink() == null || p.getPaymentLink().isBlank()) {
            p.setPaymentLink(defaultPaymentLink);
        }
        return procesoRepository.save(p);
    }

    public ChatbotMatriculaProceso markPaymentApproved(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("APPROVED");
        p.setFlowStatus("PAYMENT_APPROVED");
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

    public Clase bookPracticalClass(String documento, LocalDate fecha, LocalTime horaInicio) {
        if (fecha == null || horaInicio == null) {
            throw new IllegalArgumentException("Fecha y hora son requeridas");
        }
        if (!fecha.isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("La fecha debe ser hoy o futura");
        }

        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));

        LocalTime horaFin = horaInicio.plusMinutes(Math.max(30, bookingDurationMinutes));

        if (claseRepository.existsStudentOverlap(estudiante.getId(), fecha, horaInicio, horaFin)) {
            throw new IllegalStateException("El estudiante ya tiene una clase en ese horario");
        }

        Profesor profesor = pickAvailableProfesor(fecha, horaInicio, horaFin)
                .orElseThrow(() -> new IllegalStateException("No hay profesor disponible para ese horario"));

        Vehiculo vehiculo = pickAvailableVehiculo()
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

        return claseRepository.save(clase);
    }

    private Optional<Profesor> pickAvailableProfesor(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        List<Profesor> activos = profesorRepository.findByVisibleTrueOrderByIdAsc();
        return activos.stream()
                .filter(p -> !claseRepository.existsProfesorOverlap(p.getId(), fecha, horaInicio, horaFin))
                .min(Comparator.comparing(Profesor::getId));
    }

    private Optional<Vehiculo> pickAvailableVehiculo() {
        List<Vehiculo> disponibles = vehiculoRepository.findByVisibleTrueAndEstadoIgnoreCaseOrderByPlacaAsc("Disponible");
        if (!disponibles.isEmpty()) {
            return Optional.of(disponibles.get(0));
        }

        List<Vehiculo> activos = vehiculoRepository.findByVisibleTrueOrderByPlacaAsc();
        return activos.isEmpty() ? Optional.empty() : Optional.of(activos.get(0));
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
