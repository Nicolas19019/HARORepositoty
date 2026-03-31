package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotContractCategoryProgress;
import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Model.Vehiculo;
import com.Aplication.HARO.Repository.ChatbotContractCategoryProgressRepository;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Repository.ClaseRepository;
import com.Aplication.HARO.Repository.EstadoCuentaRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.PagoRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;
import com.Aplication.HARO.Repository.VehiculoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ChatbotProcesoService {
    private static final Logger log = LoggerFactory.getLogger(ChatbotProcesoService.class);

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final ChatbotContractCategoryProgressRepository contractCategoryProgressRepository;
    private final EstudianteRepository estudianteRepository;
    private final EstudianteService estudianteService;
    private final EstadoCuentaRepository estadoCuentaRepository;
    private final PagoRepository pagoRepository;
    private final ClaseRepository claseRepository;
    private final ProfesorRepository profesorRepository;
    private final VehiculoRepository vehiculoRepository;
    private final GoogleCalendarService googleCalendarService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${chatbot.payment.link:https://epayco-link.com}")
    private String defaultPaymentLink;

    @Value("${chatbot.payment.link.half:}")
    private String defaultPaymentLinkHalf;

    @Value("${chatbot.payment.link.a2:${chatbot.payment.link}}")
    private String paymentLinkA2;

    @Value("${chatbot.payment.link.a2.half:${chatbot.payment.link.half}}")
    private String paymentLinkA2Half;

    @Value("${chatbot.payment.link.b1:${chatbot.payment.link}}")
    private String paymentLinkB1;

    @Value("${chatbot.payment.link.b1.half:${chatbot.payment.link.half}}")
    private String paymentLinkB1Half;

    @Value("${chatbot.payment.link.c1:${chatbot.payment.link}}")
    private String paymentLinkC1;

    @Value("${chatbot.payment.link.c1.half:${chatbot.payment.link.half}}")
    private String paymentLinkC1Half;

    @Value("${chatbot.payment.link.a2b1:${chatbot.payment.link}}")
    private String paymentLinkA2B1;

    @Value("${chatbot.payment.link.a2b1.half:${chatbot.payment.link.half}}")
    private String paymentLinkA2B1Half;

    @Value("${chatbot.payment.link.a2c1:${chatbot.payment.link}}")
    private String paymentLinkA2C1;

    @Value("${chatbot.payment.link.a2c1.half:${chatbot.payment.link.half}}")
    private String paymentLinkA2C1Half;

    @Value("${chatbot.payment.link.b1c1:${chatbot.payment.link}}")
    private String paymentLinkB1C1;

    @Value("${chatbot.payment.link.b1c1.half:${chatbot.payment.link.half}}")
    private String paymentLinkB1C1Half;

    @Value("${chatbot.payment.link.a2b1c1:${chatbot.payment.link}}")
    private String paymentLinkA2B1C1;

    @Value("${chatbot.payment.link.a2b1c1.half:${chatbot.payment.link.half}}")
    private String paymentLinkA2B1C1Half;

    @Value("${chatbot.payment.confirmation-url:https://harorepositoty2-590358146556.europe-west1.run.app/confirmation}")
private String paymentConfirmationUrl;

    @Value("${chatbot.payment.return-url:https://ceaharo.com/respuesta.html}")
    private String paymentReturnUrl;

    @Value("${chatbot.payment.param.document:x_extra1}")
    private String paymentDocumentParam;

    @Value("${chatbot.payment.param.email:customer_email}")
    private String paymentEmailParam;

    @Value("${chatbot.payment.param.flow-id:x_extra2}")
    private String paymentFlowIdParam;

    @Value("${chatbot.payment.param.invoice:x_id_invoice}")
    private String paymentInvoiceParam;

    @Value("${chatbot.payment.param.amount:x_amount}")
    private String paymentAmountParam;

    @Value("${chatbot.payment.param.confirmation:confirmation}")
    private String paymentConfirmationParam;

    @Value("${chatbot.payment.param.return:response}")
    private String paymentReturnParam;

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

    public record StudentAccessData(String documento,
                                    String email,
                                    String emailMasked,
                                    String nombreCompleto,
                                    Long studentId,
                                    String categoria,
                                    String tipoPase,
                                    String sede) {}
    public record BookingResult(Clase clase, GoogleCalendarService.ReunionCreada reunionCalendario) {}
    public record SlotAvailability(int opcion, String hora, boolean disponible) {}
    public record CancellationResult(Clase clase,
                                     boolean multaAplicada,
                                     BigDecimal valorMulta,
                                     long horasRestantes,
                                     BigDecimal multasAcumuladas) {}
    public record StudentDuplicateCheckResult(boolean exists, String reason) {}
    public record ContractCategoryFlowSnapshot(
            boolean allCompleted,
            String currentCategoryCode,
            String currentCategoryLabel,
            int currentCategoryIndex,
            int currentCategoryPosition,
            int totalCategories,
            int completedCategories,
            int remainingCategories,
            int currentContractIndex,
            Integer currentContractPosition,
            Integer expectedContractNumber,
            String expectedContractFile,
            String nextCategoryCode,
            String nextCategoryLabel,
            List<String> contractCategoriesRequired,
            List<Map<String, Object>> categories
    ) {}
    public static class ContractUploadValidationException extends RuntimeException {
        private final HttpStatus status;

        public ContractUploadValidationException(HttpStatus status, String message) {
            super(message);
            this.status = status == null ? HttpStatus.UNPROCESSABLE_ENTITY : status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
    private record ContractStudentProfile(String nombre,
                                          String apellido,
                                          String tipoDocumento,
                                          String telefono,
                                          String email,
                                          String direccion,
                                          String categoria,
                                          String sede) {}

    public ChatbotProcesoService(ChatbotMatriculaProcesoRepository procesoRepository,
                                 ChatbotContractCategoryProgressRepository contractCategoryProgressRepository,
                                 EstudianteRepository estudianteRepository,
                                 EstudianteService estudianteService,
                                 EstadoCuentaRepository estadoCuentaRepository,
                                 PagoRepository pagoRepository,
                                 ClaseRepository claseRepository,
                                 ProfesorRepository profesorRepository,
                                 VehiculoRepository vehiculoRepository,
                                 GoogleCalendarService googleCalendarService,
                                 @Qualifier("passwordEncoder") PasswordEncoder passwordEncoder) {
        this.procesoRepository = procesoRepository;
        this.contractCategoryProgressRepository = contractCategoryProgressRepository;
        this.estudianteRepository = estudianteRepository;
        this.estudianteService = estudianteService;
        this.estadoCuentaRepository = estadoCuentaRepository;
        this.pagoRepository = pagoRepository;
        this.claseRepository = claseRepository;
        this.profesorRepository = profesorRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.googleCalendarService = googleCalendarService;
        this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public ChatbotMatriculaProceso upsertDraft(String phone,
                                               String nombreCompleto,
                                               String documento,
                                               String categoria,
                                               String email,
                                               String telefono) {
        return upsertDraft(phone, nombreCompleto, documento, categoria, email, telefono, "", "", null);
    }
    @Transactional
    public ChatbotMatriculaProceso upsertDraft(String phone,
                                               String nombreCompleto,
                                               String documento,
                                               String categoria,
                                               String email,
                                               String telefono,
                                               String direccion) {
        return upsertDraft(phone, nombreCompleto, documento, categoria, email, telefono, direccion, "", null);
    }

    @Transactional
    public ChatbotMatriculaProceso upsertDraft(String phone,
                                               String nombreCompleto,
                                               String documento,
                                               String categoria,
                                               String email,
                                               String telefono,
                                               String direccion,
                                               String sede) {
        return upsertDraft(phone, nombreCompleto, documento, categoria, email, telefono, direccion, sede, null);
    }

    @Transactional
    public ChatbotMatriculaProceso upsertDraft(String phone,
                                               String nombreCompleto,
                                               String documento,
                                               String categoria,
                                               String email,
                                               String telefono,
                                               String direccion,
                                               String sede,
                                               Integer edad) {
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
        if (edad != null && edad.intValue() > 0) {
            proceso.setEdad(edad);
        }
        proceso.setDireccion(collapseSpaces(direccion));
        String sedeValue = collapseSpaces(sede);
        if (!sedeValue.isBlank()) {
            proceso.setSede(sedeValue);
        }
        proceso.setExpectedAmount(resolveExpectedAmountByCategory(cat));
        proceso.setFlowStatus("DRAFT");
        if (trim(proceso.getOrigenRegistro()).isBlank()) {
            proceso.setOrigenRegistro("CHATBOT");
        }

        if (proceso.getPaymentStatus() == null || proceso.getPaymentStatus().isBlank()) {
            proceso.setPaymentStatus("PENDING");
        }
        if (proceso.getContractStatus() == null || proceso.getContractStatus().isBlank()) {
            proceso.setContractStatus("PENDING_SIGNATURE");
        }

        ChatbotMatriculaProceso saved = procesoRepository.save(proceso);
        ensureContractCategoryProgress(saved);
        return saved;
    }

    @Transactional
    public ChatbotMatriculaProceso markCashPaymentPending(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setMetodoPago("EFECTIVO");
        p.setPaymentStatus("PENDING");
        p.setFlowStatus("PENDING_CASH_VALIDATION");
        // No hay link de pago cuando el pago es en efectivo.
        p.setPaymentLink(null);
        // Conserva el total esperado para reportes.
        if (p.getExpectedAmount() == null) {
            p.setExpectedAmount(resolveExpectedAmount(p));
        }
        return procesoRepository.save(p);
    }

    @Transactional
    public ChatbotMatriculaProceso setMetodoPago(String documento, String metodoPago) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        String normalized = trim(metodoPago).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            p.setMetodoPago(null);
            return procesoRepository.save(p);
        }
        if (!"EPAYCO".equals(normalized) && !"EFECTIVO".equals(normalized)) {
            throw new IllegalArgumentException("metodoPago solo permite: EPAYCO o EFECTIVO");
        }
        p.setMetodoPago(normalized);
        return procesoRepository.save(p);
    }

    @Transactional
    public ChatbotMatriculaProceso setOrigenRegistro(String documento, String origenRegistro) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        String normalized = trim(origenRegistro).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            p.setOrigenRegistro(null);
            return procesoRepository.save(p);
        }
        if (!"CHATBOT".equals(normalized) && !"HAROGESTION".equals(normalized)) {
            throw new IllegalArgumentException("origenRegistro solo permite: CHATBOT o HAROGESTION");
        }
        p.setOrigenRegistro(normalized);
        return procesoRepository.save(p);
    }

    @Transactional(readOnly = true)
    public StudentDuplicateCheckResult validateStudentUniquenessForEnrollment(String documento, String email) {
        String doc = normalizeDoc(documento);
        String mail = normalizeEmail(email);

        boolean byDocumento = !doc.isBlank() && estudianteRepository.existsByNumeroDocumento(doc);
        boolean byEmail = !mail.isBlank() && estudianteRepository.findByEmailNormalizado(mail).isPresent();

        if (byDocumento) {
            return new StudentDuplicateCheckResult(true, "DOCUMENTO");
        }
        if (byEmail) {
            return new StudentDuplicateCheckResult(true, "EMAIL");
        }
        return new StudentDuplicateCheckResult(false, null);
    }
    @Transactional
    public ChatbotMatriculaProceso markPaymentPending(String documento) {
        return markPaymentPending(documento, "FULL");
    }

    @Transactional
    public ChatbotMatriculaProceso markPaymentPending(String documento, String paymentPlan) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);

        String normalizedPlan = normalizePaymentPlan(paymentPlan);
        if ("HALF".equals(normalizedPlan) && !canGenerateHalfPaymentLink(p)) {
            throw new IllegalStateException("Pago por mitad no disponible para la categoria " + safe(p.getCategoria()));
        }

        p.setPaymentPlan(normalizedPlan);
        p.setPaymentStatus("PENDING");
        // expectedAmount siempre representa el total esperado del curso (no el abono).
        p.setExpectedAmount(resolveExpectedAmount(p));
        p.setFlowStatus("PENDING_PAYMENT");

        String link = buildPaymentLink(p);
        if (link.isBlank()) {
            throw new IllegalStateException("No se pudo generar el link de pago.");
        }
        p.setPaymentLink(link);

        ChatbotMatriculaProceso saved = procesoRepository.save(p);

        log.info("Pago pendiente doc={} plan={} link={}",
                saved.getNumeroDocumento(),
                safe(saved.getPaymentPlan()),
                saved.getPaymentLink());

        return saved;
    }
    @Transactional
    public ChatbotMatriculaProceso markPaymentRejected(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("REJECTED");
        p.setFlowStatus("PAYMENT_REJECTED");
        return procesoRepository.save(p);
    }
    @Transactional
    public ChatbotMatriculaProceso markPaymentCancelled(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("CANCELLED");
        p.setFlowStatus("PAYMENT_CANCELLED");
        return procesoRepository.save(p);
    }
    @Transactional
    public ChatbotMatriculaProceso markPaymentApproved(String documento, BigDecimal amountPaid) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setPaymentStatus("APPROVED");
        p.setFlowStatus("PAYMENT_APPROVED");
        if (amountPaid != null && amountPaid.signum() >= 0) {
            p.setPaymentAmount(amountPaid);
        } else if (p.getPaymentAmount() == null) {
            p.setPaymentAmount(resolvePaidAmountForEstadoCuenta(p, resolveExpectedAmount(p)));
        }
        ChatbotMatriculaProceso saved = procesoRepository.save(p);
        log.info("Pago aprobado doc={} amount={}",
                saved.getNumeroDocumento(),
                saved.getPaymentAmount());
        return saved;
    }
    @Transactional
    public ChatbotMatriculaProceso markContractLinkSent(String documento, String contractLink) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        p.setContractLink(normalizeContractLink(contractLink));
        p.setContractStatus("LINK_SENT");
        p.setFlowStatus("CONTRACT_LINK_SENT");
        return procesoRepository.save(p);
    }
    @Transactional
    public ChatbotMatriculaProceso updateContractLink(String documento, String contractLink) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        String normalized = normalizeContractLink(contractLink);
        if (Objects.equals(trim(p.getContractLink()), normalized)) {
            return p;
        }
        p.setContractLink(normalized);
        return procesoRepository.save(p);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatbotMatriculaProceso markContractSignedByEmail(String email) {
        String mail = normalizeEmail(email);
        ChatbotMatriculaProceso proceso = procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .orElseThrow(() -> new NoSuchElementException("No hay proceso de matricula para " + mail));
        List<ChatbotContractCategoryProgress> categoryProgressList = ensureContractCategoryProgress(proceso);
        if (!areAllCategoriesCompleted(categoryProgressList)) {
            throw new IllegalStateException("Aun hay categorias pendientes por firmar.");
        }
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
        String rebuilt = buildPaymentLink(p);
        if (!rebuilt.isBlank()) {
            return rebuilt;
        }
        return p.getPaymentLink() == null ? "" : p.getPaymentLink();
    }

    public ChatbotMatriculaProceso setPaymentLinkIfMissing(String documento) {
        ChatbotMatriculaProceso p = getByDocumentoOrThrow(documento);
        if (p.getPaymentLink() == null || p.getPaymentLink().isBlank()) {
            p.setPaymentLink(buildPaymentLink(p));
            return procesoRepository.save(p);
        }
        return p;
    }

    public String getPaymentConfirmationUrl() {
        return paymentConfirmationUrl;
    }

    public String getPaymentReturnUrl() {
        return paymentReturnUrl;
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findProcesoByDocumento(String documento) {
        return procesoRepository.findByNumeroDocumento(normalizeDoc(documento));
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findProcesoById(Long procesoId) {
        if (procesoId == null) {
            return Optional.empty();
        }
        return procesoRepository.findById(procesoId);
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findLatestProcesoByEmail(String email) {
        return procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findLatestProcesoByMaskedHints(String maskedDocumentRaw, String maskedEmailRaw) {
        return findLatestProcesoByMaskedHints(maskedDocumentRaw, maskedEmailRaw, "");
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findLatestProcesoByMaskedHints(String maskedDocumentRaw,
                                                                            String maskedEmailRaw,
                                                                            String maskedPhoneRaw) {
        MaskLookupParts docMask = MaskLookupParts.forDocument(maskedDocumentRaw);
        MaskLookupParts emailMask = MaskLookupParts.forEmail(maskedEmailRaw);
        MaskLookupParts phoneMask = MaskLookupParts.forDocument(maskedPhoneRaw);
        if (!docMask.hasCriteria() && !emailMask.hasCriteria() && !phoneMask.hasCriteria()) {
            return Optional.empty();
        }

        List<ChatbotMatriculaProceso> strictCandidates = findMaskedCandidates(docMask, emailMask);
        Optional<ChatbotMatriculaProceso> strictResolved = resolveMaskedCandidates(strictCandidates, docMask, emailMask, phoneMask);
        if (strictResolved.isPresent()) {
            return strictResolved;
        }

        List<ChatbotMatriculaProceso> docOnlyCandidates = List.of();
        if (docMask.hasCriteria()) {
            docOnlyCandidates = findMaskedCandidates(docMask, MaskLookupParts.empty());
            Optional<ChatbotMatriculaProceso> docOnlyResolved =
                    resolveMaskedCandidates(docOnlyCandidates, docMask, MaskLookupParts.empty(), phoneMask);
            if (docOnlyResolved.isPresent()) {
                return docOnlyResolved;
            }
        }

        List<ChatbotMatriculaProceso> emailOnlyCandidates = List.of();
        if (emailMask.hasCriteria()) {
            emailOnlyCandidates = findMaskedCandidates(MaskLookupParts.empty(), emailMask);
            Optional<ChatbotMatriculaProceso> emailOnlyResolved =
                    resolveMaskedCandidates(emailOnlyCandidates, MaskLookupParts.empty(), emailMask, phoneMask);
            if (emailOnlyResolved.isPresent()) {
                return emailOnlyResolved;
            }
        }

        Optional<ChatbotMatriculaProceso> intersectionResolved =
                resolveByIntersection(docOnlyCandidates, emailOnlyCandidates, phoneMask);
        if (intersectionResolved.isPresent()) {
            return intersectionResolved;
        }

        log.warn("No se pudo resolver proceso por mascaras. docMask={} emailMask={} phoneMask={} candidatos={}",
                maskMaskedValue(maskedDocumentRaw),
                maskMaskedValue(maskedEmailRaw),
                maskMaskedValue(maskedPhoneRaw),
                Math.max(strictCandidates.size(), Math.max(docOnlyCandidates.size(), emailOnlyCandidates.size())));
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findLatestProcesoByPhone(String phoneRaw) {
        String normalized = trim(phoneRaw).replaceAll("[^0-9+]", "");
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        Optional<ChatbotMatriculaProceso> exact =
                procesoRepository.findTopByPhoneOrderByUpdatedAtDesc(normalized);
        if (exact.isPresent()) {
            return exact;
        }

        if (normalized.startsWith("+")) {
            return procesoRepository.findTopByPhoneOrderByUpdatedAtDesc(normalized.substring(1));
        }
        return procesoRepository.findTopByPhoneOrderByUpdatedAtDesc("+" + normalized);
    }

    @Transactional(readOnly = true)
    public Optional<ChatbotMatriculaProceso> findLatestProcesoByInvoiceHint(String invoiceRaw) {
        String invoice = trim(invoiceRaw);
        if (invoice.isBlank()) {
            return Optional.empty();
        }
        return procesoRepository.findTopByPaymentLinkContainingOrderByUpdatedAtDesc(invoice);
    }
    @Transactional
    public Optional<ChatbotMatriculaProceso> mergeContractSubmissionByEmail(String email,
                                                                            String contractName,
                                                                            String pdfFile,
                                                                            String formDataJson,
                                                                            String fileName,
                                                                            String fileUrl,
                                                                            String objectKey,
                                                                            String signerFolder) {
        return mergeContractSubmissionByEmail(email, "", contractName, pdfFile, formDataJson, fileName, fileUrl, objectKey, signerFolder);
    }

    @Transactional
    public Optional<ChatbotMatriculaProceso> mergeContractSubmissionByEmail(String email,
                                                                            String requestedCategoryCode,
                                                                            String contractName,
                                                                            String pdfFile,
                                                                            String formDataJson,
                                                                            String fileName,
                                                                            String fileUrl,
                                                                            String objectKey,
                                                                            String signerFolder) {
        String mail = normalizeEmail(email);
        Optional<ChatbotMatriculaProceso> procesoOpt =
                procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail);
        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        List<ChatbotContractCategoryProgress> categoryProgressList = ensureContractCategoryProgress(proceso);
        List<String> purchasedCategories = splitPurchasedCategories(proceso.getCategoria());
        List<String> requiredContractCategories = splitContractRequiredCategories(proceso.getCategoria());
        ChatbotContractCategoryProgress targetProgress =
                resolveValidatedUploadCategoryProgress(
                        categoryProgressList,
                        purchasedCategories,
                        requiredContractCategories,
                        requestedCategoryCode
                );
        validateExpectedContractUpload(targetProgress, pdfFile, contractName, fileName);

        Map<String, Object> mergedFormData = readJsonMap(proceso.getContractFormData());
        Map<String, Object> incomingFormData = readJsonMap(formDataJson);
        if (!incomingFormData.isEmpty()) {
            mergedFormData.putAll(incomingFormData);
            proceso.setContractFormData(writeJson(mergedFormData));
            applyContractFormSnapshotToProceso(proceso, mergedFormData);

            Map<String, Object> categoryFormData = readJsonMap(targetProgress.getContractFormData());
            categoryFormData.putAll(incomingFormData);
            targetProgress.setContractFormData(writeJson(categoryFormData));
        }

        Map<String, Object> uploadMeta = new LinkedHashMap<>();
        putIfNotBlank(uploadMeta, "contractName", contractName);
        putIfNotBlank(uploadMeta, "pdfFile", pdfFile);
        putIfNotBlank(uploadMeta, "fileName", fileName);
        putIfNotBlank(uploadMeta, "fileUrl", fileUrl);
        putIfNotBlank(uploadMeta, "objectKey", objectKey);
        putIfNotBlank(uploadMeta, "signerFolder", signerFolder);
        if (!uploadMeta.isEmpty()) {
            uploadMeta.put("storedAt", Instant.now().toString());
            List<Map<String, Object>> categoryUploads = readJsonList(targetProgress.getSignedContractFiles());
            putIfNotBlank(uploadMeta, "categoryCode", targetProgress.getCategoryCode());
            putIfNotBlank(uploadMeta, "categoryLabel", targetProgress.getCategoryLabel());
            upsertContractUpload(categoryUploads, uploadMeta);
            targetProgress.setSignedContractFiles(writeJson(categoryUploads));
        }

        refreshContractCategoryProgressStatus(targetProgress);
        contractCategoryProgressRepository.save(targetProgress);
        syncMasterContractStatus(proceso, ensureContractCategoryProgress(proceso));
        return Optional.of(procesoRepository.save(proceso));
    }
    @Transactional
    public Optional<ChatbotMatriculaProceso> capturePaymentMetadataByDocument(String documento,
                                                                              String paymentMethod,
                                                                              String paymentNote) {
        String doc = normalizeDoc(documento);
        Optional<ChatbotMatriculaProceso> procesoOpt = procesoRepository.findByNumeroDocumento(doc);
        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        Map<String, Object> formData = readJsonMap(proceso.getContractFormData());
        putIfNotBlank(formData, "shared_payment_method", firstNotBlank(paymentMethod, readValue(formData, "shared_payment_method")));
        putIfNotBlank(formData, "payment_method", firstNotBlank(paymentMethod, readValue(formData, "payment_method")));
        putIfNotBlank(formData, "shared_payment_note", firstNotBlank(paymentNote, readValue(formData, "shared_payment_note")));
        putIfNotBlank(formData, "payment_note", firstNotBlank(paymentNote, readValue(formData, "payment_note")));
        if (!formData.isEmpty()) {
            proceso.setContractFormData(writeJson(formData));
        }
        return Optional.of(procesoRepository.save(proceso));
    }

    @Transactional
    public Map<String, Object> buildContractAccessPayloadByEmail(String email) {
        String mail = normalizeEmail(email);
        Optional<ChatbotMatriculaProceso> procesoOpt =
                procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", mail);
        if (procesoOpt.isEmpty()) {
            return payload;
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        List<ChatbotContractCategoryProgress> categoryProgressList = ensureContractCategoryProgress(proceso);
        List<String> purchasedCategories = splitPurchasedCategories(proceso.getCategoria());
        List<String> requiredContractCategories = splitContractRequiredCategories(proceso.getCategoria());
        ContractCategoryFlowSnapshot contractFlow = buildContractCategoryFlowSnapshot(
                categoryProgressList,
                purchasedCategories,
                requiredContractCategories
        );
        Map<String, Object> formData = readJsonMap(proceso.getContractFormData());
        ChatbotContractCategoryProgress currentCategoryProgress =
                findCurrentCategoryProgress(categoryProgressList).orElse(null);
        if (currentCategoryProgress != null) {
            Map<String, Object> currentCategoryForm = readJsonMap(currentCategoryProgress.getContractFormData());
            if (!currentCategoryForm.isEmpty()) {
                formData.putAll(currentCategoryForm);
            }
        }

        putIfNotBlank(payload, "nombreCompleto", firstNotBlank(
                readValue(formData, "c2_nombre"),
                readValue(formData, "est_nombre"),
                readValue(formData, "c3_est_nombre"),
                proceso.getNombreCompleto()
        ));
        putIfNotBlank(payload, "document", firstNotBlank(
                readValue(formData, "c2_doc_num"),
                readValue(formData, "est_doc_num"),
                readValue(formData, "c3_est_doc_num"),
                proceso.getNumeroDocumento()
        ));
        putIfNotBlank(payload, "documentType", firstNotBlank(
                readValue(formData, "c2_doc_tipo"),
                readValue(formData, "est_doc_tipo"),
                readValue(formData, "c3_est_doc_tipo")
        ));
        putIfNotBlank(payload, "categoria", firstNotBlank(
                readValue(formData, "c2_categoria"),
                contractFlow.currentCategoryLabel(),
                proceso.getCategoria()
        ));

        putIfNotBlank(payload, "shared_contact_email", firstNotBlank(
                readValue(formData, "shared_contact_email"),
                readValue(formData, "c2_correo"),
                proceso.getEmail()
        ));
        putIfNotBlank(payload, "shared_contact_phone", firstNotBlank(
                readValue(formData, "shared_contact_phone"),
                readValue(formData, "c2_celular"),
                proceso.getTelefono(),
                proceso.getPhone()
        ));
        putIfNotBlank(payload, "shared_contact_address", firstNotBlank(
                readValue(formData, "shared_contact_address"),
                readValue(formData, "c2_direccion"),
                proceso.getDireccion()
        ));
        putIfNotBlank(payload, "shared_sede", readValue(formData, "shared_sede"));
        putIfNotBlank(payload, "shared_payment_method", firstNotBlank(
                readValue(formData, "shared_payment_method"),
                readValue(formData, "payment_method"),
                readValue(formData, "medio_pago")
        ));
        putIfNotBlank(payload, "shared_payment_note", firstNotBlank(
                readValue(formData, "shared_payment_note"),
                readValue(formData, "payment_note"),
                readValue(formData, "payment_detail"),
                readValue(formData, "bank_name"),
                readValue(formData, "franchise")
        ));
        putIfNotBlank(payload, "paymentMethod", firstNotBlank(
                readValue(formData, "shared_payment_method"),
                readValue(formData, "payment_method"),
                readValue(formData, "medio_pago")
        ));
        putIfNotBlank(payload, "paymentNote", firstNotBlank(
                readValue(formData, "shared_payment_note"),
                readValue(formData, "payment_note"),
                readValue(formData, "payment_detail"),
                readValue(formData, "bank_name"),
                readValue(formData, "franchise")
        ));
        putIfNotBlank(payload, "paymentStatus", proceso.getPaymentStatus());
        putIfNotBlank(payload, "contractStatus", proceso.getContractStatus());
        putIfNotBlank(payload, "flowStatus", proceso.getFlowStatus());
        if (proceso.getExpectedAmount() != null) {
            payload.put("expectedAmount", proceso.getExpectedAmount().toPlainString());
        }
        if (proceso.getPaymentAmount() != null) {
            payload.put("paymentAmount", proceso.getPaymentAmount().toPlainString());
        }
        if (proceso.getStudentId() != null) {
            payload.put("studentId", proceso.getStudentId());
        }
        if (!trim(proceso.getStudentPasswordHash()).isBlank()) {
            payload.put("accessPasswordConfigured", true);
        }
        Map<String, Object> contractFlowPayload = new LinkedHashMap<>();
        contractFlowPayload.put("allCompleted", contractFlow.allCompleted());
        contractFlowPayload.put("totalCategories", contractFlow.totalCategories());
        contractFlowPayload.put("completedCategories", contractFlow.completedCategories());
        contractFlowPayload.put("remainingCategories", contractFlow.remainingCategories());
        contractFlowPayload.put("currentCategoryIndex", contractFlow.currentCategoryIndex());
        contractFlowPayload.put("currentCategoryPosition", contractFlow.currentCategoryPosition());
        putIfNotBlank(contractFlowPayload, "currentCategoryCode", contractFlow.currentCategoryCode());
        putIfNotBlank(contractFlowPayload, "currentCategoryLabel", contractFlow.currentCategoryLabel());
        contractFlowPayload.put("currentContractIndex", contractFlow.currentContractIndex());
        contractFlowPayload.put("currentContractPosition", contractFlow.currentContractPosition());
        contractFlowPayload.put("expectedContractNumber", contractFlow.expectedContractNumber());
        contractFlowPayload.put("expectedContractFile", contractFlow.expectedContractFile());
        contractFlowPayload.put("nextCategoryCode", trim(contractFlow.nextCategoryCode()).isBlank() ? null : contractFlow.nextCategoryCode());
        contractFlowPayload.put("nextCategoryLabel", trim(contractFlow.nextCategoryLabel()).isBlank() ? null : contractFlow.nextCategoryLabel());
        contractFlowPayload.put("contractCategoriesRequired", contractFlow.contractCategoriesRequired());
        contractFlowPayload.put("categories", contractFlow.categories());
        payload.put("contractFlow", contractFlowPayload);
        payload.put("selectedCategories", purchasedCategories);
        payload.put("contractCategoriesRequired", requiredContractCategories);
        return payload;
    }

    @Transactional
    public ContractCategoryFlowSnapshot getContractCategoryFlowByEmail(String email) {
        String mail = normalizeEmail(email);
        ChatbotMatriculaProceso proceso = procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .orElseThrow(() -> new NoSuchElementException("No hay proceso de matricula para " + mail));
        return buildContractCategoryFlowSnapshot(
                ensureContractCategoryProgress(proceso),
                splitPurchasedCategories(proceso.getCategoria()),
                splitContractRequiredCategories(proceso.getCategoria())
        );
    }

    @Transactional
    public String resolveCurrentContractCategoryLabel(String email, String requestedCategoryCode) {
        String mail = normalizeEmail(email);
        ChatbotMatriculaProceso proceso = procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .orElseThrow(() -> new NoSuchElementException("No hay proceso de matricula para " + mail));
        List<ChatbotContractCategoryProgress> items = ensureContractCategoryProgress(proceso);
        return resolveWritableCategoryProgress(
                proceso,
                items,
                splitPurchasedCategories(proceso.getCategoria()),
                splitContractRequiredCategories(proceso.getCategoria()),
                requestedCategoryCode
        ).getCategoryLabel();
    }

    @Transactional
    public String validateAndResolveUploadCategoryLabel(String email,
                                                        String requestedCategoryCode,
                                                        String pdfFile,
                                                        String contractName) {
        String mail = normalizeEmail(email);
        ChatbotMatriculaProceso proceso = procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .orElseThrow(() -> new NoSuchElementException("No hay proceso de matricula para " + mail));
        List<ChatbotContractCategoryProgress> items = ensureContractCategoryProgress(proceso);
        ChatbotContractCategoryProgress target = resolveValidatedUploadCategoryProgress(
                items,
                splitPurchasedCategories(proceso.getCategoria()),
                splitContractRequiredCategories(proceso.getCategoria()),
                requestedCategoryCode
        );
        validateExpectedContractUpload(target, pdfFile, contractName, "");
        return target.getCategoryLabel();
    }

    @Transactional(readOnly = true)
    public String resolveSedeByEmail(String email) {
        String mail = normalizeEmail(email);
        return procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(mail)
                .map(ChatbotMatriculaProceso::getSede)
                .map(this::trim)
                .orElse("");
    }

    private List<ChatbotContractCategoryProgress> ensureContractCategoryProgress(ChatbotMatriculaProceso proceso) {
        if (proceso == null || proceso.getId() == null) {
            return List.of();
        }

        List<ChatbotContractCategoryProgress> existing =
                contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(proceso.getId());
        Map<String, ChatbotContractCategoryProgress> byCode = new LinkedHashMap<>();
        for (ChatbotContractCategoryProgress item : existing) {
            byCode.put(trim(item.getCategoryCode()).toUpperCase(Locale.ROOT), item);
        }

        boolean changed = false;
        List<String> categories = splitContractRequiredCategories(proceso.getCategoria());

        // Data hygiene: if the process category was corrected over time, stale rows may remain.
        // They must not force extra "categorias por firmar" in the UI/backoffice.
        java.util.Set<String> desired = categories.stream()
                .map(c -> trim(c).toUpperCase(Locale.ROOT))
                .filter(c -> !c.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!desired.isEmpty()) {
            for (ChatbotContractCategoryProgress item : existing) {
                String code = trim(item.getCategoryCode()).toUpperCase(Locale.ROOT);
                if (code.isBlank() || desired.contains(code)) continue;
                try {
                    contractCategoryProgressRepository.delete(item);
                    changed = true;
                } catch (Exception ex) {
                    log.warn("No se pudo eliminar categoria de contrato sobrante procesoId={} categoryCode={}: {}",
                            proceso.getId(), code, ex.getMessage());
                }
            }
        }

        for (int i = 0; i < categories.size(); i++) {
            String code = categories.get(i);
            ChatbotContractCategoryProgress item = byCode.get(code);
            if (item == null) {
                item = new ChatbotContractCategoryProgress();
                item.setProceso(proceso);
                item.setCategoryCode(code);
                item.setCategoryLabel(code);
                item.setOrderIndex(i);
                item.setStatus(i == 0 ? "IN_PROGRESS" : "PENDING");
                item.setCurrentContractIndex(0);
                byCode.put(code, contractCategoryProgressRepository.save(item));
                changed = true;
                continue;
            }

            String expectedLabel = code;
            if (!Objects.equals(trim(item.getCategoryLabel()), expectedLabel)) {
                item.setCategoryLabel(expectedLabel);
                changed = true;
            }
            if (item.getOrderIndex() == null || item.getOrderIndex() != i) {
                item.setOrderIndex(i);
                changed = true;
            }
            refreshContractCategoryProgressStatus(item);
            contractCategoryProgressRepository.save(item);
        }

        List<ChatbotContractCategoryProgress> ordered = new ArrayList<>(
                contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(proceso.getId())
        );

        syncMasterContractStatus(proceso, ordered);
        if (changed) {
            procesoRepository.save(proceso);
        }
        return ordered;
    }

    private ChatbotContractCategoryProgress resolveWritableCategoryProgress(ChatbotMatriculaProceso proceso,
                                                                           List<ChatbotContractCategoryProgress> items,
                                                                           List<String> purchasedCategories,
                                                                           List<String> requiredContractCategories,
                                                                           String requestedCategoryCode) {
        String requested = normalizeSingleCategoryCode(requestedCategoryCode);
        if (!requested.isBlank()) {
            boolean purchased = purchasedCategories.stream().anyMatch(code -> code.equalsIgnoreCase(requested));
            boolean required = requiredContractCategories.stream().anyMatch(code -> code.equalsIgnoreCase(requested));
            if (purchased && !required) {
                throw new IllegalArgumentException("La categoria " + requested + " no requiere contrato en este combo.");
            }
            return items.stream()
                    .filter(item -> requested.equalsIgnoreCase(trim(item.getCategoryCode())))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("La categoria " + requested + " no pertenece a la compra."));
        }

        return findCurrentCategoryProgress(items)
                .orElseThrow(() -> new IllegalStateException("No hay categorias pendientes para este proceso."));
    }

    private Optional<ChatbotContractCategoryProgress> findCurrentCategoryProgress(List<ChatbotContractCategoryProgress> items) {
        return items.stream()
                .filter(item -> !"COMPLETED".equalsIgnoreCase(trim(item.getStatus())))
                .sorted((left, right) -> Integer.compare(safeOrder(left), safeOrder(right)))
                .findFirst()
                .or(() -> items.stream()
                        .sorted((left, right) -> Integer.compare(safeOrder(left), safeOrder(right)))
                        .findFirst());
    }

    private ContractCategoryFlowSnapshot buildContractCategoryFlowSnapshot(List<ChatbotContractCategoryProgress> items,
                                                                           List<String> purchasedCategories,
                                                                           List<String> requiredContractCategories) {
        List<String> purchasedOrdered = purchasedCategories == null ? List.of() : new ArrayList<>(purchasedCategories);
        List<String> requiredOrdered = requiredContractCategories == null ? List.of() : new ArrayList<>(requiredContractCategories);
        Map<String, ChatbotContractCategoryProgress> byCode = new LinkedHashMap<>();
        for (ChatbotContractCategoryProgress item : items) {
            if (item == null) continue;
            byCode.put(trim(item.getCategoryCode()).toUpperCase(Locale.ROOT), item);
        }

        List<ChatbotContractCategoryProgress> requiredItems = new ArrayList<>();
        for (String code : requiredOrdered) {
            ChatbotContractCategoryProgress item = byCode.get(trim(code).toUpperCase(Locale.ROOT));
            if (item != null) {
                requiredItems.add(item);
            }
        }

        requiredItems.sort((left, right) -> Integer.compare(safeOrder(left), safeOrder(right)));
        ChatbotContractCategoryProgress current = findCurrentCategoryProgress(requiredItems).orElse(null);
        int total = requiredItems.size();
        int completed = 0;
        List<Map<String, Object>> categories = new ArrayList<>();

        for (int purchasedIndex = 0; purchasedIndex < purchasedOrdered.size(); purchasedIndex++) {
            String code = trim(purchasedOrdered.get(purchasedIndex)).toUpperCase(Locale.ROOT);
            boolean requiresContract = requiredOrdered.stream().anyMatch(required -> required.equalsIgnoreCase(code));
            Map<String, Object> categoryPayload = new LinkedHashMap<>();
            categoryPayload.put("categoryCode", code);
            categoryPayload.put("categoryLabel", code);
            categoryPayload.put("requiresContract", requiresContract);

            if (!requiresContract) {
                categoryPayload.put("orderIndex", purchasedIndex);
                categoryPayload.put("status", "EXEMPT");
                categoryPayload.put("uploadedContracts", 0);
                categoryPayload.put("totalContracts", 0);
                categoryPayload.put("currentContractIndex", null);
                categoryPayload.put("currentContractPosition", null);
                categoryPayload.put("expectedContractNumber", null);
                categoryPayload.put("expectedContractFile", null);
                categoryPayload.put("completed", true);
                categories.add(categoryPayload);
                continue;
            }

            ChatbotContractCategoryProgress item = byCode.get(code);
            if (item == null) {
                categoryPayload.put("orderIndex", purchasedIndex);
                categoryPayload.put("status", "PENDING");
                categoryPayload.put("uploadedContracts", 0);
                categoryPayload.put("totalContracts", 4);
                categoryPayload.put("currentContractIndex", 0);
                categoryPayload.put("currentContractPosition", 1);
                categoryPayload.put("expectedContractNumber", 1);
                categoryPayload.put("expectedContractFile", expectedContractFile(1));
                categoryPayload.put("completed", false);
                categories.add(categoryPayload);
                continue;
            }

            refreshContractCategoryProgressStatus(item);
            int uploadedContracts = countUploadedContracts(item.getSignedContractFiles());
            boolean completedCategory = "COMPLETED".equalsIgnoreCase(trim(item.getStatus()));
            if (completedCategory) completed++;

            categoryPayload.put("orderIndex", safeOrder(item));
            categoryPayload.put("status", trim(item.getStatus()));
            categoryPayload.put("uploadedContracts", uploadedContracts);
            categoryPayload.put("totalContracts", 4);
            categoryPayload.put("currentContractIndex", Math.min(uploadedContracts, 3));
            categoryPayload.put("currentContractPosition", uploadedContracts >= 4 ? null : uploadedContracts + 1);
            categoryPayload.put("expectedContractNumber", uploadedContracts >= 4 ? null : uploadedContracts + 1);
            categoryPayload.put("expectedContractFile", uploadedContracts >= 4 ? null : expectedContractFile(uploadedContracts + 1));
            categoryPayload.put("completed", completedCategory);
            if (item.getCompletedAt() != null) {
                categoryPayload.put("completedAt", item.getCompletedAt().toString());
            }
            categories.add(categoryPayload);
        }

        boolean allCompleted = total > 0 && completed == total;
        int currentCategoryIndex = current == null ? Math.max(0, total - 1) : safeOrder(current);
        int currentCategoryPosition = total <= 0 ? 0 : currentCategoryIndex + 1;
        String currentCode = current == null ? "" : trim(current.getCategoryCode());
        String currentLabel = current == null ? "" : trim(current.getCategoryLabel());
        int currentContractIndex = current == null ? 0 : Math.min(countUploadedContracts(current.getSignedContractFiles()), 3);
        int currentUploadedContracts = current == null ? 0 : countUploadedContracts(current.getSignedContractFiles());
        Integer currentContractPosition = currentUploadedContracts >= 4 ? null : currentUploadedContracts + 1;
        Integer expectedContractNumber = currentUploadedContracts >= 4 ? null : currentUploadedContracts + 1;
        String expectedContractFile = expectedContractNumber == null ? null : expectedContractFile(expectedContractNumber);
        String nextCategoryCode = "";
        String nextCategoryLabel = "";
        if (current != null && currentCategoryIndex >= 0 && currentCategoryIndex + 1 < requiredItems.size()) {
            ChatbotContractCategoryProgress next = requiredItems.get(currentCategoryIndex + 1);
            nextCategoryCode = trim(next.getCategoryCode());
            nextCategoryLabel = trim(next.getCategoryLabel());
        }

        return new ContractCategoryFlowSnapshot(
                allCompleted,
                currentCode,
                currentLabel,
                currentCategoryIndex,
                currentCategoryPosition,
                total,
                completed,
                Math.max(0, total - completed),
                currentContractIndex,
                currentContractPosition,
                expectedContractNumber,
                expectedContractFile,
                nextCategoryCode,
                nextCategoryLabel,
                requiredOrdered,
                categories
        );
    }

    private void refreshContractCategoryProgressStatus(ChatbotContractCategoryProgress item) {
        if (item == null) {
            return;
        }
        int uploadedContracts = countUploadedContracts(item.getSignedContractFiles());
        if (uploadedContracts >= 4) {
            item.setStatus("COMPLETED");
            item.setCurrentContractIndex(3);
            if (item.getCompletedAt() == null) {
                item.setCompletedAt(Instant.now());
            }
            return;
        }
        if (uploadedContracts > 0) {
            item.setStatus("IN_PROGRESS");
            item.setCurrentContractIndex(uploadedContracts);
            item.setCompletedAt(null);
        } else {
            item.setStatus("PENDING");
            item.setCurrentContractIndex(0);
            item.setCompletedAt(null);
        }
    }

    private void syncMasterContractStatus(ChatbotMatriculaProceso proceso, List<ChatbotContractCategoryProgress> items) {
        if (proceso == null) {
            return;
        }
        boolean allCompleted = areAllCategoriesCompleted(items);
        boolean anyStarted = items.stream().anyMatch(item -> countUploadedContracts(item.getSignedContractFiles()) > 0);
        if (allCompleted) {
            proceso.setContractStatus("SIGNED");
            proceso.setFlowStatus("CONTRACT_SIGNED");
            if (proceso.getContractSignedAt() == null) {
                proceso.setContractSignedAt(Instant.now());
            }
            return;
        }
        if (anyStarted) {
            proceso.setContractStatus("IN_PROGRESS");
            proceso.setFlowStatus("CONTRACT_IN_PROGRESS");
        } else if (!"LINK_SENT".equalsIgnoreCase(trim(proceso.getContractStatus()))) {
            proceso.setContractStatus("PENDING_SIGNATURE");
            if ("CONTRACT_SIGNED".equalsIgnoreCase(trim(proceso.getFlowStatus()))) {
                proceso.setFlowStatus("PAID");
            }
        }
        if (!allCompleted) {
            proceso.setContractSignedAt(null);
        }
    }

    private boolean areAllCategoriesCompleted(List<ChatbotContractCategoryProgress> items) {
        return !items.isEmpty() && items.stream().allMatch(item -> "COMPLETED".equalsIgnoreCase(trim(item.getStatus())));
    }

    private int countUploadedContracts(String signedContractFilesJson) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> upload : readJsonList(signedContractFilesJson)) {
            // Prefer pdfFile (stable key), but fall back to contractName/fileName for older payloads.
            String identity = firstNotBlank(
                    trim(asString(upload.get("pdfFile"))),
                    trim(asString(upload.get("contractName"))),
                    trim(asString(upload.get("fileName")))
            );
            if (!identity.isBlank()) seen.add(identity);
        }
        return Math.min(seen.size(), 4);
    }

    private ChatbotContractCategoryProgress resolveValidatedUploadCategoryProgress(List<ChatbotContractCategoryProgress> items,
                                                                                  List<String> purchasedCategories,
                                                                                  List<String> requiredContractCategories,
                                                                                  String requestedCategoryCode) {
        ChatbotContractCategoryProgress current = findCurrentCategoryProgress(items)
                .orElseThrow(() -> new ContractUploadValidationException(
                        HttpStatus.CONFLICT,
                        "No hay categorias pendientes para este proceso."
                ));
        String requested = normalizeSingleCategoryCode(requestedCategoryCode);
        if (requested.isBlank()) {
            throw new ContractUploadValidationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "categoryCode es obligatorio y debe ser una categoria valida."
            );
        }
        boolean purchased = purchasedCategories.stream().anyMatch(code -> code.equalsIgnoreCase(requested));
        boolean required = requiredContractCategories.stream().anyMatch(code -> code.equalsIgnoreCase(requested));
        if (purchased && !required) {
            throw new ContractUploadValidationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La categoria " + requested + " no requiere contrato en este combo."
            );
        }
        String active = trim(current.getCategoryCode()).toUpperCase(Locale.ROOT);
        if (!requested.equalsIgnoreCase(active)) {
            throw new ContractUploadValidationException(
                    HttpStatus.CONFLICT,
                    "La categoria activa es " + active + ". Debe cargar los contratos en ese orden."
            );
        }
        return current;
    }

    private void validateExpectedContractUpload(ChatbotContractCategoryProgress targetProgress,
                                                String pdfFile,
                                                String contractName,
                                                String fileName) {
        int uploadedContracts = countUploadedContracts(targetProgress.getSignedContractFiles());
        if (uploadedContracts >= 4) {
            throw new ContractUploadValidationException(
                    HttpStatus.CONFLICT,
                    "La categoria " + trim(targetProgress.getCategoryCode()) + " ya tiene sus 4 contratos firmados."
            );
        }
        int expectedContractNumber = uploadedContracts + 1;
        Integer providedContractNumber = resolveContractNumber(pdfFile, contractName, fileName);
        if (providedContractNumber == null) {
            throw new ContractUploadValidationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo identificar el contrato recibido. Envie pdfFile con el formato Contrato1.pdf a Contrato4.pdf."
            );
        }
        if (providedContractNumber.intValue() != expectedContractNumber) {
            throw new ContractUploadValidationException(
                    HttpStatus.CONFLICT,
                    "Debe cargar " + expectedContractFile(expectedContractNumber) + " para la categoria "
                            + trim(targetProgress.getCategoryCode()) + "."
            );
        }
    }

    private Integer resolveContractNumber(String pdfFile, String contractName, String fileName) {
        Integer fromPdfFile = extractContractNumber(pdfFile);
        if (fromPdfFile != null) return fromPdfFile;
        Integer fromContractName = extractContractNumber(contractName);
        if (fromContractName != null) return fromContractName;
        return extractContractNumber(fileName);
    }

    private Integer extractContractNumber(String raw) {
        String value = trim(raw).toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("CONTRATO\\s*([1-4])").matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String expectedContractFile(int contractNumber) {
        int safeNumber = Math.max(1, Math.min(contractNumber, 4));
        return "Contrato" + safeNumber + ".pdf";
    }

    private int safeOrder(ChatbotContractCategoryProgress item) {
        return item == null || item.getOrderIndex() == null ? Integer.MAX_VALUE : item.getOrderIndex();
    }

    private List<String> splitPurchasedCategories(String categoriaRaw) {
        String normalized = normalizeCategoria(categoriaRaw);
        List<String> categories = new ArrayList<>();
        if (normalized.contains("A2")) categories.add("A2");
        if (normalized.contains("B1")) categories.add("B1");
        if (normalized.contains("C1")) categories.add("C1");
        if (categories.isEmpty()) {
            categories.add("A2");
        }
        return categories;
    }

    private List<String> splitContractRequiredCategories(String categoriaRaw) {
        List<String> purchased = splitPurchasedCategories(categoriaRaw);
        if (purchased.size() == 3
                && purchased.contains("A2")
                && purchased.contains("B1")
                && purchased.contains("C1")) {
            // Politica comercial vigente: en el combo A2+B1+C1, B1 no requiere contrato.
            return List.of("A2", "C1");
        }
        return purchased;
    }

    private String normalizeSingleCategoryCode(String rawCategoryCode) {
        String value = trim(rawCategoryCode).toUpperCase(Locale.ROOT);
        if ("A2".equals(value) || "B1".equals(value) || "C1".equals(value)) {
            return value;
        }
        return "";
    }
    @Transactional
    public Long createStudentFromSignedContract(String documento) {
        String doc = normalizeDoc(documento);
        ChatbotMatriculaProceso proceso = getByDocumentoOrThrow(doc);

        if (!"APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) {
            throw new IllegalStateException("El pago aun no esta aprobado para el documento " + doc);
        }

        if (!"SIGNED".equalsIgnoreCase(proceso.getContractStatus())) {
            throw new IllegalStateException("El contrato aun no esta firmado para el documento " + doc);
        }

        ContractStudentProfile profile = extractStudentProfile(proceso);

        Optional<Estudiante> existing = estudianteRepository.findByNumeroDocumento(doc);
        if (existing.isPresent()) {
            Estudiante e = existing.get();
            applyContractProfileToStudent(e, profile, proceso);
            estudianteRepository.save(e);
            ensureEstadoCuentaForStudent(proceso, e.getId());
            ensurePagoForStudent(proceso, e.getId());
            proceso.setStudentId(e.getId());
            proceso.setFlowStatus("STUDENT_CREATED");
            if (proceso.getEnrolledAt() == null) {
                proceso.setEnrolledAt(Instant.now());
            }
            procesoRepository.save(proceso);
            return e.getId();
        }

        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(profile.nombre());
        nuevo.setApellido(profile.apellido());
        nuevo.setNumeroDocumento(doc);
        nuevo.setCategoria(profile.categoria());
        nuevo.setTipoPase(toTipoPase(profile.categoria()));
        nuevo.setTipoDocumento(profile.tipoDocumento());
        nuevo.setTelefono(profile.telefono());
        nuevo.setEmail(profile.email());
        nuevo.setDireccion(profile.direccion());
        nuevo.setSede(profile.sede());
        nuevo.setTipoEstudiante("matriculado");
        nuevo.setFechaMatricula(resolveEnrollmentDate(proceso));
        nuevo.setOrigenMatricula("CHATBOT");
        nuevo.setEstado("Activo");
        nuevo.setVisible(true);
        nuevo.setUsuario(generateUniqueUsername(profile.email(), doc));
        // La contrasena no debe venir del usuario en el flujo de contrato.
        // El servidor asigna una clave temporal (ver EstudianteService) o un flujo dedicado.
        nuevo.setContrasena(null);

        Estudiante created = estudianteService.createEstudiante(nuevo);
        ensureEstadoCuentaForStudent(proceso, created.getId());
        ensurePagoForStudent(proceso, created.getId());

        proceso.setStudentId(created.getId());
        proceso.setFlowStatus("STUDENT_CREATED");
        proceso.setEnrolledAt(Instant.now());
        procesoRepository.save(proceso);
        return created.getId();
    }

    /**
     * Intenta finalizar la matricula (crear/actualizar estudiante, estado de cuenta y pago) si y solo si:
     * - paymentStatus = APPROVED
     * - contractStatus = SIGNED
     *
     * No lanza excepciones para evitar marcar transacciones como rollback-only (idempotente / best-effort).
     */
    public Optional<Long> tryFinalizeEnrollmentIfReadyByDocumento(String documentoRaw) {
        String doc = normalizeDoc(documentoRaw);
        if (doc.isBlank()) {
            return Optional.empty();
        }

        ChatbotMatriculaProceso proceso;
        try {
            // Lock para evitar carreras pago/contrato.
            proceso = procesoRepository.findByNumeroDocumentoForUpdate(doc)
                    .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + doc));
        } catch (Exception ex) {
            log.error("No se pudo resolver proceso para finalizar matricula doc={}: {}", doc, ex.getMessage(), ex);
            return Optional.empty();
        }

        String paymentStatus = trim(proceso.getPaymentStatus()).toUpperCase(Locale.ROOT);
        String contractStatus = trim(proceso.getContractStatus()).toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(paymentStatus) || !"SIGNED".equals(contractStatus)) {
            return Optional.empty();
        }

        if (proceso.getStudentId() != null) {
            return Optional.of(proceso.getStudentId());
        }

        try {
            Long studentId = createStudentFromSignedContract(doc);
            return Optional.ofNullable(studentId);
        } catch (Exception ex) {
            log.error("Error finalizando matricula doc={}: {}", doc, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Fuerza la finalizacion de matricula: crea/actualiza estudiante, estado de cuenta y pago
     * aun si el flujo no cumple precondiciones (ej. pago aun no aprobado).
     *
     * Esto se usa cuando el negocio requiere que el estudiante exista obligatoriamente despues de firmar contrato.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Long forceFinalizeEnrollmentByDocumento(String documentoRaw) {
        String doc = normalizeDoc(documentoRaw);
        if (doc.isBlank()) {
            throw new IllegalArgumentException("documento requerido para finalizar matricula");
        }

        // Lock para evitar carreras en la creacion/actualizacion del estudiante.
        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumentoForUpdate(doc)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + doc));

        if (proceso.getStudentId() != null) {
            Long id = proceso.getStudentId();
            ensureEstadoCuentaForStudent(proceso, id);
            ensurePagoForStudentForce(proceso, id);
            if (trim(proceso.getFlowStatus()).isBlank() || !"STUDENT_CREATED".equalsIgnoreCase(trim(proceso.getFlowStatus()))) {
                proceso.setFlowStatus("STUDENT_CREATED");
                if (proceso.getEnrolledAt() == null) proceso.setEnrolledAt(Instant.now());
                procesoRepository.save(proceso);
            }
            return id;
        }

        ContractStudentProfile profile = extractStudentProfile(proceso);

        Optional<Estudiante> existing = estudianteRepository.findByNumeroDocumento(doc);
        final Long studentId;
        if (existing.isPresent()) {
            Estudiante e = existing.get();
            applyContractProfileToStudent(e, profile, proceso);
            estudianteRepository.save(e);
            studentId = e.getId();
        } else {
            Estudiante nuevo = new Estudiante();
            nuevo.setNombre(profile.nombre());
            nuevo.setApellido(profile.apellido());
            nuevo.setNumeroDocumento(doc);
            nuevo.setCategoria(profile.categoria());
            nuevo.setTipoPase(toTipoPase(profile.categoria()));
            nuevo.setTipoDocumento(profile.tipoDocumento());
            nuevo.setTelefono(profile.telefono());
            nuevo.setEmail(profile.email());
            nuevo.setDireccion(profile.direccion());
            nuevo.setSede(profile.sede());
            nuevo.setTipoEstudiante("matriculado");
            nuevo.setFechaMatricula(resolveEnrollmentDate(proceso));
            nuevo.setOrigenMatricula("CHATBOT");
            nuevo.setEstado("Activo");
            nuevo.setVisible(true);
            nuevo.setUsuario(generateUniqueUsername(profile.email(), doc));
            nuevo.setContrasena(null);

            Estudiante created = estudianteService.createEstudiante(nuevo);
            studentId = created.getId();
        }

        ensureEstadoCuentaForStudent(proceso, studentId);
        ensurePagoForStudentForce(proceso, studentId);

        proceso.setStudentId(studentId);
        proceso.setFlowStatus("STUDENT_CREATED");
        if (proceso.getEnrolledAt() == null) proceso.setEnrolledAt(Instant.now());
        procesoRepository.save(proceso);
        return studentId;
    }
    @Transactional
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

    private void applyContractFormSnapshotToProceso(ChatbotMatriculaProceso proceso, Map<String, Object> formData) {
        if (proceso == null || formData == null || formData.isEmpty()) {
            return;
        }

        String fullName = firstNotBlank(
                readValue(formData, "c2_nombre"),
                readValue(formData, "est_nombre"),
                readValue(formData, "c3_est_nombre"),
                proceso.getNombreCompleto()
        );
        if (!fullName.isBlank()) {
            proceso.setNombreCompleto(collapseSpaces(fullName));
        }

        String category = firstNotBlank(readValue(formData, "c2_categoria"), proceso.getCategoria());
        if (!category.isBlank()) {
            proceso.setCategoria(normalizeCategoria(category));
        }

        String phone = normalizePhoneIfPossible(firstNotBlank(
                readValue(formData, "shared_contact_phone"),
                readValue(formData, "c2_celular"),
                proceso.getTelefono()
        ));
        if (!phone.isBlank()) {
            proceso.setTelefono(phone);
            if (trim(proceso.getPhone()).isBlank()) {
                proceso.setPhone(phone);
            }
        }

        String direccion = firstNotBlank(
                readValue(formData, "shared_contact_address"),
                readValue(formData, "c2_direccion"),
                proceso.getDireccion()
        );
        if (!direccion.isBlank()) {
            proceso.setDireccion(collapseSpaces(direccion));
        }

        String sede = trim(readValue(formData, "shared_sede"));
        if (!sede.isBlank()) {
            proceso.setSede(collapseSpaces(sede));
        }
    }

    private ContractStudentProfile extractStudentProfile(ChatbotMatriculaProceso proceso) {
        Map<String, Object> formData = readJsonMap(proceso.getContractFormData());
        String fullName = firstNotBlank(
                readValue(formData, "c2_nombre"),
                readValue(formData, "est_nombre"),
                readValue(formData, "c3_est_nombre"),
                proceso.getNombreCompleto()
        );
        String[] nameParts = splitName(fullName);

        String tipoDocumento = normalizeStudentDocType(firstNotBlank(
                readValue(formData, "c2_doc_tipo"),
                readValue(formData, "est_doc_tipo"),
                readValue(formData, "c3_est_doc_tipo"),
                "CC"
        ));
        String telefono = normalizePhoneIfPossible(firstNotBlank(
                readValue(formData, "shared_contact_phone"),
                readValue(formData, "c2_celular"),
                proceso.getTelefono(),
                proceso.getPhone()
        ));
        String email = normalizeEmailIfPossible(firstNotBlank(
                readValue(formData, "shared_contact_email"),
                readValue(formData, "c2_correo"),
                proceso.getEmail()
        ));
        String direccion = firstNotBlank(
                readValue(formData, "shared_contact_address"),
                readValue(formData, "c2_direccion"),
                proceso.getDireccion()
        );
        String categoria = normalizeCategoria(firstNotBlank(
                readValue(formData, "c2_categoria"),
                proceso.getCategoria()
        ));
        String sede = firstNotBlank(readValue(formData, "shared_sede"), proceso.getSede());

        return new ContractStudentProfile(
                nameParts[0],
                nameParts[1],
                tipoDocumento,
                telefono,
                email,
                direccion,
                categoria,
                sede
        );
    }

    private void applyContractProfileToStudent(Estudiante estudiante,
                                               ContractStudentProfile profile,
                                               ChatbotMatriculaProceso proceso) {
        if (estudiante == null || profile == null) {
            return;
        }

        if (!profile.nombre().isBlank()) {
            estudiante.setNombre(profile.nombre());
        }
        if (!profile.apellido().isBlank()) {
            estudiante.setApellido(profile.apellido());
        }
        if (!profile.tipoDocumento().isBlank()) {
            estudiante.setTipoDocumento(profile.tipoDocumento());
        }
        if (!profile.telefono().isBlank()) {
            estudiante.setTelefono(profile.telefono());
        }
        if (!profile.email().isBlank()) {
            estudiante.setEmail(profile.email());
        }
        if (!profile.direccion().isBlank()) {
            estudiante.setDireccion(profile.direccion());
        }
        if (!profile.sede().isBlank()) {
            estudiante.setSede(profile.sede());
        }
        if (!profile.categoria().isBlank()) {
            estudiante.setCategoria(profile.categoria());
            estudiante.setTipoPase(toTipoPase(profile.categoria()));
        }
        // En el flujo de contrato/pago aprobado, el estudiante debe quedar matriculado (aunque existiera como prospecto).
        String tipoEst = trim(estudiante.getTipoEstudiante());
        if (tipoEst.isBlank() || "prospecto".equalsIgnoreCase(tipoEst)) {
            estudiante.setTipoEstudiante("matriculado");
        }
        if (estudiante.getFechaMatricula() == null) {
            estudiante.setFechaMatricula(resolveEnrollmentDate(proceso));
        }
        estudiante.setOrigenMatricula("CHATBOT");
        String estadoActual = trim(estudiante.getEstado());
        if (estadoActual.isBlank() || "pendiente".equalsIgnoreCase(estadoActual)) {
            estudiante.setEstado("Activo");
        }
        if (estudiante.getVisible() == null) {
            estudiante.setVisible(true);
        }
        if ((estudiante.getUsuario() == null || estudiante.getUsuario().isBlank()) && proceso != null) {
            estudiante.setUsuario(generateUniqueUsername(profile.email(), proceso.getNumeroDocumento()));
        }
    }

    private void upsertContractUpload(List<Map<String, Object>> uploads, Map<String, Object> incoming) {
        if (uploads == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        String identity = firstNotBlank(
                stringValue(incoming.get("pdfFile")),
                stringValue(incoming.get("contractName")),
                stringValue(incoming.get("fileName"))
        );
        if (identity.isBlank()) {
            uploads.add(incoming);
            return;
        }

        for (int i = 0; i < uploads.size(); i++) {
            Map<String, Object> current = uploads.get(i);
            String currentIdentity = firstNotBlank(
                    stringValue(current.get("pdfFile")),
                    stringValue(current.get("contractName")),
                    stringValue(current.get("fileName"))
            );
            if (identity.equalsIgnoreCase(currentIdentity)) {
                uploads.set(i, incoming);
                return;
            }
        }
        uploads.add(incoming);
    }

    private Map<String, Object> readJsonMap(String rawJson) {
        String json = trim(rawJson);
        if (json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("No se pudo leer JSON de formulario de contrato: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readJsonList(String rawJson) {
        String json = trim(rawJson);
        if (json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            // Jackson normalmente materializa mapas como LinkedHashMap; tipamos como Map para evitar mismatch de generics.
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            log.warn("No se pudo leer JSON de archivos firmados: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la informacion del contrato", ex);
        }
    }

    private String readValue(Map<String, Object> data, String key) {
        if (data == null || data.isEmpty() || key == null || key.isBlank()) {
            return "";
        }
        Object value = data.get(key);
        return stringValue(value);
    }

    private String stringValue(Object value) {
        return value == null ? "" : trim(String.valueOf(value));
    }

    private String normalizeStudentDocType(String raw) {
        String value = trim(raw).toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            return "CC";
        }
        return switch (value) {
            case "C.C.", "CC" -> "CC";
            case "C.E.", "CE" -> "CE";
            case "T.I.", "TI" -> "TI";
            case "PAS", "PASAPORTE" -> "PAS";
            default -> value;
        };
    }

    private String normalizeEmailIfPossible(String raw) {
        String value = trim(raw);
        if (value.isBlank() || !value.contains("@")) {
            return "";
        }
        try {
            return normalizeEmail(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private String normalizePhoneIfPossible(String raw) {
        String value = trim(raw);
        if (value.isBlank()) {
            return "";
        }
        try {
            return normalizePhone(value);
        } catch (Exception ex) {
            return value.replaceAll("[^0-9+]", "");
        }
    }

    private String resolvePaymentMethod(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return "PAGO_EN_LINEA";
        }
        Map<String, Object> formData = readJsonMap(proceso.getContractFormData());
        String raw = firstNotBlank(
                readValue(formData, "shared_payment_method"),
                readValue(formData, "payment_method"),
                readValue(formData, "medio_pago")
        );
        if (raw.isBlank()) {
            return "PAGO_EN_LINEA";
        }

        return switch (trim(raw).toUpperCase(Locale.ROOT)) {
            case "PSE" -> "PSE";
            case "TARJETA_CREDITO", "TARJETA DE CREDITO" -> "TARJETA_CREDITO";
            case "TARJETA_DEBITO", "TARJETA DE DEBITO" -> "TARJETA_DEBITO";
            case "TARJETA", "CARD" -> "TARJETA";
            case "TRANSFERENCIA", "TRANSFERENCIA_BANCARIA" -> "TRANSFERENCIA";
            case "NEQUI", "DAVIPLATA", "BILLETERA_DIGITAL" -> "BILLETERA_DIGITAL";
            case "EFECTIVO" -> "EFECTIVO";
            case "OTRO" -> "OTRO";
            default -> collapseSpaces(raw).toUpperCase(Locale.ROOT).replace(' ', '_');
        };
    }

    private void putIfNotBlank(Map<String, Object> out, String key, String value) {
        String normalized = trim(value);
        if (!normalized.isBlank()) {
            out.put(key, normalized);
        }
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = trim(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    @Transactional(readOnly = true)
    public StudentAccessData requireStudentAccessData(String documento) {
        Estudiante estudiante = estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));

        String email = trim(estudiante.getEmail()).toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new IllegalStateException("El estudiante no tiene correo registrado para verificacion OTP");
        }

        String nombreCompleto = buildStudentDisplayName(estudiante);
        String categoria = trim(estudiante.getCategoria());
        String tipoPase = trim(estudiante.getTipoPase()).toLowerCase(Locale.ROOT);
        if (tipoPase.isBlank() && !categoria.isBlank()) {
            tipoPase = toTipoPase(categoria);
        }
        String sede = trim(estudiante.getSede());
        if (sede.isBlank()) {
            String doc = trim(estudiante.getNumeroDocumento());
            if (!doc.isBlank()) {
                sede = trim(procesoRepository.findByNumeroDocumento(doc)
                        .map(ChatbotMatriculaProceso::getSede)
                        .orElse(""));
            }
        }

        return new StudentAccessData(
                estudiante.getNumeroDocumento(),
                email,
                maskEmail(email),
                nombreCompleto,
                estudiante.getId(),
                categoria,
                tipoPase,
                sede
        );
    }

    @Transactional(readOnly = true)
    public List<Clase> listAgendaByDocumento(String documento) {
        return listAgendaByStudentId(resolveStudentIdByDocumento(documento));
    }

    @Transactional(readOnly = true)
    public List<Clase> listAgendaByStudentId(Long studentId) {
        return claseRepository.findAgendaByIdEstudiante(requireStudentId(studentId));
    }

    @Transactional(readOnly = true)
    public List<Clase> listUpcomingClassesForCancellation(String documento) {
        return listUpcomingClassesForCancellationByStudentId(resolveStudentIdByDocumento(documento));
    }

    @Transactional(readOnly = true)
    public List<Clase> listUpcomingClassesForCancellationByStudentId(Long studentId) {
        Long id = requireStudentId(studentId);
        ZoneId zone = resolveBookingZone();
        ZonedDateTime now = ZonedDateTime.now(zone);

        return claseRepository.findAgendaByIdEstudiante(id).stream()
                .filter(c -> !isCanceledState(c.getEstado()))
                .filter(c -> ZonedDateTime.of(c.getFecha(), c.getHoraInicio(), zone).isAfter(now))
                .toList();
    }

    public BookingResult bookPracticalClass(String documento, LocalDate fecha, LocalTime horaInicio) {
        return bookPracticalClassByStudentId(resolveStudentIdByDocumento(documento), fecha, horaInicio);
    }

    public BookingResult bookPracticalClassByStudentId(Long studentId, LocalDate fecha, LocalTime horaInicio) {
        return bookPracticalClassByStudentId(studentId, fecha, horaInicio, null);
    }

    public BookingResult bookPracticalClassByStudentId(Long studentId,
                                                       LocalDate fecha,
                                                       LocalTime horaInicio,
                                                       String tipoPasePreferido) {
        if (fecha == null || horaInicio == null) {
            throw new IllegalArgumentException("Fecha y hora son requeridas");
        }
        if (!fecha.isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("La fecha debe ser hoy o futura");
        }

        Long id = requireStudentId(studentId);
        Estudiante estudiante = estudianteRepository.findById(id)
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese ID"));

        validatePracticalClassEligibility(id);

        LocalTime horaFin = horaInicio.plusMinutes(Math.max(30, bookingDurationMinutes));

        if (claseRepository.existsStudentOverlap(id, fecha, horaInicio, horaFin)) {
            throw new IllegalStateException("El estudiante ya tiene una clase en ese horario");
        }

        String desiredTipo = resolveDesiredTipoPase(tipoPasePreferido, estudiante);
        String desiredSede = resolveStudentSedeForBooking(estudiante);
        if (desiredSede.isBlank()) {
            throw new IllegalStateException("No tienes sede asignada. Comunicate con la academia para actualizarla antes de agendar.");
        }
        if (trim(estudiante.getSede()).isBlank()) {
            // Curacion suave: si la sede existe en el proceso de matricula, la persistimos en el estudiante.
            estudiante.setSede(desiredSede);
            estudianteRepository.save(estudiante);
        }

        Profesor profesor = pickAvailableProfesor(fecha, horaInicio, horaFin, desiredTipo, desiredSede)
                .orElseThrow(() -> new IllegalStateException(
                        desiredTipo.isBlank()
                                ? "No hay instructor disponible en la sede '" + desiredSede + "' para ese horario. Elige otra hora."
                                : ("No hay instructor disponible para practica de " + desiredTipo + " en la sede '" + desiredSede + "' para ese horario. Elige otra hora.")
                ));

        Vehiculo vehiculo = pickAvailableVehiculo(fecha, horaInicio, horaFin, desiredSede)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay vehiculo disponible en la sede '" + desiredSede + "' para ese horario. Elige otra hora."
                ));

        Clase clase = new Clase();
        clase.setId_estudiante(id);
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
        return cancelPracticalClassByStudentId(resolveStudentIdByDocumento(documento), idClase);
    }

    public CancellationResult cancelPracticalClassByStudentId(Long studentId, Long idClase) {
        if (idClase == null || idClase <= 0) {
            throw new IllegalArgumentException("ID de clase invalido");
        }

        Long id = requireStudentId(studentId);
        Clase clase = claseRepository.findById(idClase)
                .orElseThrow(() -> new NoSuchElementException("No existe una clase con ID " + idClase));

        if (!Objects.equals(clase.getId_estudiante(), id)) {
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
            EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(id)
                    .orElseGet(EstadoCuenta::new);
            estadoCuenta.setIdEstudiante(id);
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
        return listPracticalSlotAvailabilityByStudentId(resolveStudentIdByDocumento(documento), fecha);
    }

    @Transactional(readOnly = true)
    public List<SlotAvailability> listPracticalSlotAvailabilityByStudentId(Long studentId, LocalDate fecha) {
        return listPracticalSlotAvailabilityByStudentId(studentId, fecha, null);
    }

    @Transactional(readOnly = true)
    public List<SlotAvailability> listPracticalSlotAvailabilityByStudentId(Long studentId,
                                                                           LocalDate fecha,
                                                                           String tipoPasePreferido) {
        if (fecha == null) {
            throw new IllegalArgumentException("Fecha requerida para consultar disponibilidad");
        }
        if (fecha.isBefore(LocalDate.now())) {
            return List.of();
        }

        Long id = requireStudentId(studentId);
        validatePracticalClassEligibility(id);

        Estudiante estudiante = estudianteRepository.findById(id)
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese ID"));

        String desiredTipo = resolveDesiredTipoPase(tipoPasePreferido, estudiante);
        String desiredSede = resolveStudentSedeForBooking(estudiante);
        if (desiredSede.isBlank()) {
            throw new IllegalStateException("No tienes sede asignada. Comunicate con la academia para actualizarla antes de consultar disponibilidad.");
        }

        List<LocalTime> slots = defaultPracticalSlots();
        List<SlotAvailability> out = new ArrayList<>();
        int idx = 1;
        for (LocalTime slot : slots) {
            LocalTime fin = slot.plusMinutes(Math.max(30, bookingDurationMinutes));
            boolean disponible = isPracticalSlotAvailable(id, fecha, slot, fin, desiredTipo, desiredSede);
            out.add(new SlotAvailability(idx, formatHour(slot), disponible));
            idx++;
        }
        return out;
    }

    @Transactional(readOnly = true)
    public boolean isPracticalSlotAvailable(String documento, LocalDate fecha, LocalTime horaInicio) {
        return isPracticalSlotAvailableByStudentId(resolveStudentIdByDocumento(documento), fecha, horaInicio);
    }

    @Transactional(readOnly = true)
    public boolean isPracticalSlotAvailableByStudentId(Long studentId, LocalDate fecha, LocalTime horaInicio) {
        if (fecha == null || horaInicio == null) return false;
        if (fecha.isBefore(LocalDate.now())) return false;

        Long id = requireStudentId(studentId);
        validatePracticalClassEligibility(id);

        LocalTime horaFin = horaInicio.plusMinutes(Math.max(30, bookingDurationMinutes));
        return isPracticalSlotAvailable(id, fecha, horaInicio, horaFin);
    }

    private boolean isPracticalSlotAvailable(Long studentId,
                                             LocalDate fecha,
                                             LocalTime horaInicio,
                                             LocalTime horaFin) {
        return isPracticalSlotAvailable(studentId, fecha, horaInicio, horaFin, "", "");
    }

    private boolean isPracticalSlotAvailable(Long studentId,
                                             LocalDate fecha,
                                             LocalTime horaInicio,
                                             LocalTime horaFin,
                                             String desiredTipoPase,
                                             String desiredSede) {
        if (claseRepository.existsStudentOverlap(studentId, fecha, horaInicio, horaFin)) {
            return false;
        }
        if (pickAvailableProfesor(fecha, horaInicio, horaFin, desiredTipoPase, desiredSede).isEmpty()) {
            return false;
        }
        return pickAvailableVehiculo(fecha, horaInicio, horaFin, desiredSede).isPresent();
    }

    private void validatePracticalClassEligibility(Long studentId) {
        EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(requireStudentId(studentId))
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

    private void ensurePagoForStudent(ChatbotMatriculaProceso proceso, Long studentId) {
        if (proceso == null || studentId == null) return;
        if (!"APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) return;

        EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(studentId).orElse(null);
        if (estadoCuenta == null || estadoCuenta.getId() == null) {
            return;
        }

        BigDecimal monto = resolvePaidAmountForEstadoCuenta(proceso, resolveExpectedAmount(proceso));
        if (monto == null || monto.signum() < 0) {
            return;
        }

        Pagos pago = pagoRepository.findTopByEstadoCuentaOrderByIdDesc(estadoCuenta.getId())
                .orElseGet(Pagos::new);
        pago.setEstadoCuenta(estadoCuenta.getId());
        if (pago.getFechaPago() == null) {
            pago.setFechaPago(LocalDate.now(ZoneId.of("America/Bogota")));
        }
        pago.setMonto(monto);
        pago.setMetodo(resolvePaymentMethod(proceso));
        pagoRepository.save(pago);
    }

    private void ensurePagoForStudentForce(ChatbotMatriculaProceso proceso, Long studentId) {
        if (proceso == null || studentId == null) return;

        EstadoCuenta estadoCuenta = estadoCuentaRepository.findByIdEstudiante(studentId).orElse(null);
        if (estadoCuenta == null || estadoCuenta.getId() == null) {
            return;
        }

        BigDecimal monto = resolvePaidAmountForEstadoCuenta(proceso, resolveExpectedAmount(proceso));
        if (monto == null || monto.signum() < 0) {
            monto = BigDecimal.ZERO;
        }

        Pagos pago = pagoRepository.findTopByEstadoCuentaOrderByIdDesc(estadoCuenta.getId())
                .orElseGet(Pagos::new);
        pago.setEstadoCuenta(estadoCuenta.getId());
        if (pago.getFechaPago() == null) {
            pago.setFechaPago(LocalDate.now(ZoneId.of("America/Bogota")));
        }
        pago.setMonto(monto);
        pago.setMetodo(resolvePaymentMethod(proceso));
        pagoRepository.save(pago);
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

    private String buildPaymentLink(ChatbotMatriculaProceso proceso) {
        String plan = normalizePaymentPlan(proceso == null ? "" : proceso.getPaymentPlan());
        if ("HALF".equals(plan) && proceso != null && !canGenerateHalfPaymentLink(proceso)) {
            log.warn("Pago por mitad solicitado pero no configurado doc={} categoria={}",
                    proceso.getNumeroDocumento(),
                    safe(proceso.getCategoria()));
            return "";
        }

        String base = trim(resolvePaymentBaseLink(proceso));
        if (base.isBlank()) {
            return "";
        }

        boolean paycoHostedLink = isPaycoHostedLink(base);
        BigDecimal expectedAmount = safeAmount(resolveExpectedAmount(proceso));
        BigDecimal linkAmount = resolvePaymentLinkAmount(plan, expectedAmount);
        String confirmationParam = normalizeCallbackParam(paymentConfirmationParam, "confirmation");
        String responseParam = normalizeCallbackParam(paymentReturnParam, "response");
        String phoneForPayment = resolvePhoneForPayment(proceso);
        String flowIdForPayment = proceso == null || proceso.getId() == null ? "" : String.valueOf(proceso.getId());
        String invoiceHintForPayment = flowIdForPayment.isBlank() ? "" : "FLOW-" + flowIdForPayment;

        String link = base;
        link = appendQueryParam(link, paymentDocumentParam, proceso == null ? null : proceso.getNumeroDocumento());
        link = appendQueryParam(link, paymentEmailParam, proceso == null ? null : proceso.getEmail());
        link = appendQueryParam(link, paymentFlowIdParam, flowIdForPayment);
        link = appendQueryParam(link, "flow_id", flowIdForPayment);
        link = appendQueryParam(link, "extra2", flowIdForPayment);
        link = appendQueryParam(link, "document", proceso == null ? null : proceso.getNumeroDocumento());
        link = appendQueryParam(link, "email", proceso == null ? null : proceso.getEmail());
        link = appendQueryParam(link, "phone", phoneForPayment);
        link = appendQueryParam(link, paymentInvoiceParam, invoiceHintForPayment);
        link = appendQueryParam(link, "x_customer_phone", phoneForPayment);
        link = appendQueryParam(link, "x_customer_mobile", phoneForPayment);

        if (!paycoHostedLink && linkAmount.signum() > 0) {
            link = appendQueryParam(link, paymentAmountParam, linkAmount.toPlainString());
        }

        String confirmationUrlWithContext = buildCallbackUrlWithContext(paymentConfirmationUrl, proceso);
        String responseUrlWithContext = buildCallbackUrlWithContext(paymentReturnUrl, proceso);

        link = appendQueryParam(link, confirmationParam, confirmationUrlWithContext);
        link = appendQueryParam(link, responseParam, responseUrlWithContext);

        if (link.length() > 580) {
            String compactLink = base;
            compactLink = appendQueryParam(compactLink, paymentDocumentParam, proceso == null ? null : proceso.getNumeroDocumento());
            compactLink = appendQueryParam(compactLink, paymentEmailParam, proceso == null ? null : proceso.getEmail());
            compactLink = appendQueryParam(compactLink, paymentFlowIdParam, flowIdForPayment);
            compactLink = appendQueryParam(compactLink, "flow_id", flowIdForPayment);
            compactLink = appendQueryParam(compactLink, "extra2", flowIdForPayment);
            compactLink = appendQueryParam(compactLink, "document", proceso == null ? null : proceso.getNumeroDocumento());
            compactLink = appendQueryParam(compactLink, "email", proceso == null ? null : proceso.getEmail());
            compactLink = appendQueryParam(compactLink, "phone", phoneForPayment);
            compactLink = appendQueryParam(compactLink, paymentInvoiceParam, invoiceHintForPayment);
            compactLink = appendQueryParam(compactLink, "x_customer_phone", phoneForPayment);
            compactLink = appendQueryParam(compactLink, "x_customer_mobile", phoneForPayment);
            if (!paycoHostedLink && linkAmount.signum() > 0) {
                compactLink = appendQueryParam(compactLink, paymentAmountParam, linkAmount.toPlainString());
            }
            compactLink = appendQueryParam(compactLink, confirmationParam, buildCallbackUrlWithCompactContext(paymentConfirmationUrl, proceso));
            compactLink = appendQueryParam(compactLink, responseParam, buildCallbackUrlWithCompactContext(paymentReturnUrl, proceso));
            link = compactLink;
            log.warn("LINK PAGO generado en modo compacto doc={} len={}",
                    proceso != null ? proceso.getNumeroDocumento() : null,
                    link.length());
        }

        log.info("LINK PAGO generado doc={} confirmation={} response={} link={}",
                proceso != null ? proceso.getNumeroDocumento() : null,
                paymentConfirmationUrl,
                paymentReturnUrl,
                link);

        return link;
    }

    private String buildCallbackUrlWithContext(String baseUrl, ChatbotMatriculaProceso proceso) {
        String out = trim(baseUrl);
        if (out.isBlank() || proceso == null) {
            return out;
        }

        String documento = trim(proceso.getNumeroDocumento());
        String email = trim(proceso.getEmail()).toLowerCase(Locale.ROOT);
        String phone = resolvePhoneForPayment(proceso);
        String flowId = proceso.getId() == null ? "" : String.valueOf(proceso.getId());

        // Contexto redundante: el frontend prioriza x_extra1/customer_email/x_customer_phone.
        // Si ePayco agrega versiones enmascaradas al final, URLSearchParams.get(...) tomara
        // el primer valor (este), conservando los datos reales para sync automatico.
        out = appendQueryParam(out, "flow_id", flowId);
        out = appendQueryParam(out, "x_extra2", flowId);
        out = appendQueryParam(out, "extra2", flowId);
        out = appendQueryParam(out, "x_extra1", documento);
        out = appendQueryParam(out, "doc", documento);
        out = appendQueryParam(out, "customer_email", email);
        out = appendQueryParam(out, "x_customer_email", email);
        out = appendQueryParam(out, "customer_phone", phone);
        out = appendQueryParam(out, "x_customer_phone", phone);
        out = appendQueryParam(out, "x_customer_mobile", phone);
        out = appendQueryParam(out, "x_customer_movil", phone);
        out = appendQueryParam(out, "document", documento);
        out = appendQueryParam(out, "email", email);
        out = appendQueryParam(out, "phone", phone);
        return out;
    }

    private String buildCallbackUrlWithFlowId(String baseUrl, ChatbotMatriculaProceso proceso) {
        String out = trim(baseUrl);
        if (out.isBlank() || proceso == null || proceso.getId() == null) {
            return out;
        }
        String flowId = String.valueOf(proceso.getId());
        out = appendQueryParam(out, "flow_id", flowId);
        out = appendQueryParam(out, "x_extra2", flowId);
        out = appendQueryParam(out, "extra2", flowId);
        return out;
    }

    private String buildCallbackUrlWithCompactContext(String baseUrl, ChatbotMatriculaProceso proceso) {
        String out = buildCallbackUrlWithFlowId(baseUrl, proceso);
        if (out.isBlank() || proceso == null) {
            return out;
        }

        String documento = trim(proceso.getNumeroDocumento());
        String email = trim(proceso.getEmail()).toLowerCase(Locale.ROOT);
        String phone = resolvePhoneForPayment(proceso);

        // Contexto minimo pero util: mantiene identificadores reales aun si ePayco enmascara otros campos.
        out = appendQueryParam(out, "x_extra1", documento);
        out = appendQueryParam(out, "customer_email", email);
        out = appendQueryParam(out, "x_customer_phone", phone);
        out = appendQueryParam(out, "x_customer_mobile", phone);
        return out;
    }

    private String resolvePhoneForPayment(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return "";
        }
        String phone = trim(proceso.getPhone());
        if (phone.isBlank()) {
            phone = trim(proceso.getTelefono());
        }
        return phone;
    }

    private String resolvePaymentBaseLink(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return trim(defaultPaymentLink);
        }

        String categoria = normalizeCategoria(proceso.getCategoria());
        String plan = normalizePaymentPlan(proceso.getPaymentPlan());
        if ("HALF".equals(plan)) {
            String half = resolvePaymentHalfBaseLinkByCategoryConfigured(categoria);
            if (!half.isBlank()) {
                return half;
            }
        }

        return resolvePaymentBaseLinkByCategory(categoria);
    }

    private String resolvePaymentBaseLinkByCategory(String categoria) {
        String candidate = switch (categoria) {
            case "A2" -> trim(paymentLinkA2);
            case "B1" -> trim(paymentLinkB1);
            case "C1" -> trim(paymentLinkC1);
            case "A2 y B1" -> trim(paymentLinkA2B1);
            case "A2 y C1" -> trim(paymentLinkA2C1);
            case "B1 y C1" -> trim(paymentLinkB1C1);
            case "A2, B1 y C1" -> trim(paymentLinkA2B1C1);
            default -> trim(defaultPaymentLink);
        };
        if (candidate.isBlank()) {
            return trim(defaultPaymentLink);
        }
        return candidate;
    }

    private String resolvePaymentHalfBaseLinkByCategoryConfigured(String categoria) {
        String candidate = switch (categoria) {
            case "A2" -> trim(paymentLinkA2Half);
            case "B1" -> trim(paymentLinkB1Half);
            case "C1" -> trim(paymentLinkC1Half);
            case "A2 y B1" -> trim(paymentLinkA2B1Half);
            case "A2 y C1" -> trim(paymentLinkA2C1Half);
            case "B1 y C1" -> trim(paymentLinkB1C1Half);
            case "A2, B1 y C1" -> trim(paymentLinkA2B1C1Half);
            default -> trim(defaultPaymentLinkHalf);
        };
        if (candidate.isBlank()) {
            return trim(defaultPaymentLinkHalf);
        }
        return candidate;
    }

    private String appendQueryParam(String baseUrl, String key, String value) {
        String safeKey = trim(key);
        String safeValue = trim(value);
        if (safeKey.isBlank() || safeValue.isBlank()) {
            return baseUrl;
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + encode(safeKey) + "=" + encode(safeValue);
    }

    private String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private boolean isPaycoHostedLink(String url) {
        String normalized = trim(url).toLowerCase(Locale.ROOT);
        return normalized.contains("://payco.link/") || normalized.startsWith("payco.link/");
    }

    private String normalizeCallbackParam(String configured, String fallback) {
        String key = trim(configured);
        if (key.isBlank()) {
            return fallback;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if ("response_url".equals(normalized)) {
            return "response";
        }
        if ("confirmation_url".equals(normalized)) {
            return "confirmation";
        }
        return key;
    }

    private String normalizePaymentPlan(String raw) {
        String v = trim(raw).toUpperCase(Locale.ROOT);
        if (v.isBlank()) return "FULL";
        if ("HALF".equals(v) || "MITAD".equals(v) || "ABONO".equals(v) || "50".equals(v) || "50%".equals(v)) return "HALF";
        if ("FULL".equals(v) || "COMPLETO".equals(v) || "100".equals(v) || "100%".equals(v)) return "FULL";
        return "FULL";
    }

    private BigDecimal resolvePaymentLinkAmount(String plan, BigDecimal expectedAmount) {
        BigDecimal base = safeAmount(expectedAmount);
        if (base.signum() <= 0) return BigDecimal.ZERO;
        if (!"HALF".equals(plan)) return base;
        return base.divide(BigDecimal.valueOf(2L), 2, RoundingMode.HALF_UP);
    }

    private boolean canGenerateHalfPaymentLink(ChatbotMatriculaProceso proceso) {
        if (proceso == null) return false;
        String categoria = normalizeCategoria(proceso.getCategoria());
        String fullBase = resolvePaymentBaseLinkByCategory(categoria);
        if (!isPaycoHostedLink(fullBase)) {
            // Si no es un payco.link, podemos enviar el abono con x_amount.
            return true;
        }
        // En payco.link se requiere un link específico con el valor del 50%.
        return !resolvePaymentHalfBaseLinkByCategoryConfigured(categoria).isBlank();
    }

    private String normalizeSingleTipoPase(String raw) {
        String v = trim(raw).toLowerCase(Locale.ROOT);
        if ("carro".equals(v) || "moto".equals(v)) {
            return v;
        }
        return "";
    }

    private String normalizeSedeKey(String raw) {
        String base = trim(raw);
        if (base.isBlank()) return "";
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return collapseSpaces(normalized);
    }

    private Set<String> resolveAllowedTipoPases(Estudiante estudiante) {
        if (estudiante == null) {
            return Set.of();
        }
        String raw = trim(estudiante.getTipoPase()).toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            raw = toTipoPase(trim(estudiante.getCategoria()));
        }
        if (raw.isBlank()) {
            return Set.of();
        }

        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String v = normalizeSingleTipoPase(part);
            if (!v.isBlank()) {
                out.add(v);
            }
        }
        return out;
    }

    private String resolveDesiredTipoPase(String explicitTipoPase, Estudiante estudiante) {
        Set<String> allowed = resolveAllowedTipoPases(estudiante);

        String desired = normalizeSingleTipoPase(explicitTipoPase);
        if (!desired.isBlank()) {
            if (!allowed.isEmpty() && !allowed.contains(desired)) {
                throw new IllegalStateException("Tu categoria no permite agendar practica de " + desired + ".");
            }
            return desired;
        }

        if (allowed.size() == 1) {
            return allowed.iterator().next();
        }
        if (allowed.size() > 1) {
            throw new IllegalStateException("Debes elegir si deseas agendar practica de carro o de moto.");
        }
        return "";
    }

    private String resolveStudentSedeForBooking(Estudiante estudiante) {
        if (estudiante == null) {
            return "";
        }

        String sede = trim(estudiante.getSede());
        if (!sede.isBlank()) {
            return sede;
        }

        String doc = normalizeDoc(estudiante.getNumeroDocumento());
        if (doc.isBlank()) {
            return "";
        }

        return trim(procesoRepository.findByNumeroDocumento(doc)
                .map(ChatbotMatriculaProceso::getSede)
                .orElse(""));
    }

    private Optional<Profesor> pickAvailableProfesor(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return pickAvailableProfesor(fecha, horaInicio, horaFin, "", "");
    }

    private Optional<Profesor> pickAvailableProfesor(LocalDate fecha,
                                                     LocalTime horaInicio,
                                                     LocalTime horaFin,
                                                     String desiredTipoPase,
                                                     String desiredSede) {
        List<Profesor> activos = profesorRepository.findByVisibleTrueOrderByIdAsc();
        if (activos.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> ocupados = new HashSet<>(claseRepository.findBusyProfesorIds(fecha, horaInicio, horaFin));
        String desired = normalizeSingleTipoPase(desiredTipoPase);
        String sede = normalizeSedeKey(desiredSede);
        for (Profesor profesor : activos) {
            if (ocupados.contains(profesor.getId())) {
                continue;
            }
            if (!sede.isBlank()) {
                String profesorSede = normalizeSedeKey(profesor.getSede());
                if (!sede.equals(profesorSede)) {
                    continue;
                }
            }
            if (!desired.isBlank()) {
                String categoria = trim(profesor.getCategoria()).toLowerCase(Locale.ROOT);
                if (!desired.equals(categoria)) {
                    continue;
                }
            }
            return Optional.of(profesor);
        }
        // Si hay sede o tipo objetivo, no debemos asignar un instructor de otra sede o categoria.
        if (!sede.isBlank() || !desired.isBlank()) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Vehiculo> pickAvailableVehiculo(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        return pickAvailableVehiculo(fecha, horaInicio, horaFin, "");
    }

    private Optional<Vehiculo> pickAvailableVehiculo(LocalDate fecha,
                                                     LocalTime horaInicio,
                                                     LocalTime horaFin,
                                                     String desiredSede) {
        Set<String> ocupados = new HashSet<>(claseRepository.findBusyVehiculoPlacas(fecha, horaInicio, horaFin));
        String sede = normalizeSedeKey(desiredSede);
        List<Vehiculo> disponibles = vehiculoRepository.findByVisibleTrueAndEstadoIgnoreCaseOrderByPlacaAsc("Disponible");
        if (!sede.isBlank()) {
            for (Vehiculo v : disponibles) {
                if (ocupados.contains(v.getPlaca())) {
                    continue;
                }
                String vehiculoSede = normalizeSedeKey(v.getSede());
                if (sede.equals(vehiculoSede)) {
                    return Optional.of(v);
                }
            }
            List<Vehiculo> activos = vehiculoRepository.findByVisibleTrueOrderByPlacaAsc();
            for (Vehiculo v : activos) {
                if (ocupados.contains(v.getPlaca())) {
                    continue;
                }
                String vehiculoSede = normalizeSedeKey(v.getSede());
                if (sede.equals(vehiculoSede)) {
                    return Optional.of(v);
                }
            }
            // Si hay sede objetivo, no debemos usar vehiculos de otra sede.
            return Optional.empty();
        }

        for (Vehiculo v : disponibles) {
            if (!ocupados.contains(v.getPlaca())) {
                return Optional.of(v);
            }
        }

        List<Vehiculo> activos = vehiculoRepository.findByVisibleTrueOrderByPlacaAsc();
        for (Vehiculo v : activos) {
            if (!ocupados.contains(v.getPlaca())) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
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

        String titulo = "Clase practica HARO - " + safe(estudiante.getNombre()) + " " + safe(estudiante.getApellido());
        // Nota: este "description" se envia tal cual en el correo de invitacion de Calendar a los asistentes.
        // Evitamos exponer datos internos (vehiculo / profesor / IDs / documento) en correos externos.
        String descripcion =
                "Clase practica agendada desde chatbot.\n\n" +
                        "Si tiene alguna novedad, por favor comuniquese con la academia y llegue puntual a las clases.";

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
                calendarId.isBlank() ? null : calendarId,
                true
        );
    }

    private ChatbotMatriculaProceso getByDocumentoOrThrow(String documento) {
        String doc = normalizeDoc(documento);
        return procesoRepository.findByNumeroDocumento(doc)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + doc));
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
        String c = trim(categoria);
        if (c.isBlank()) {
            // Defensive default: never infer extra categories from an empty/invalid value.
            return "A2";
        }

        c = Normalizer.normalize(c, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("+", "Y")
                .replace("/", "Y")
                .replace(",", "Y");

        // Strip any other punctuation (e.g. "A-2", "B.1") but keep Y as a separator.
        c = c.replaceAll("[^A-Z0-9Y]+", "");

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

        // If we cannot detect a category reliably, don't add extra categories.
        return "A2";
    }

    private String normalizeDoc(String doc) {
        String out = trim(doc).replaceAll("\\D+", "");
        if (out.length() < 5) {
            throw new IllegalArgumentException("Documento invalido");
        }
        return out;
    }

    private String normalizeEmail(String email) {
        String out = trim(email).toLowerCase(Locale.ROOT);
        if (out.isBlank() || !out.contains("@")) {
            throw new IllegalArgumentException("Email invalido");
        }
        return out;
    }

    private String normalizePhone(String phone) {
        String out = trim(phone).replaceAll("[^0-9+]", "");
        if (out.isBlank()) {
            throw new IllegalArgumentException("Telefono requerido");
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private String normalizeContractLink(String raw) {
        String link = trim(raw);
        if (link.isBlank()) {
            return link;
        }
        return link.replaceFirst("(?i)(?:/Contratos)*/contrato\\.html(?=($|[?#]))", "/Contratos/contrato.html");
    }

    private String maskMaskedValue(String raw) {
        String value = trim(raw);
        if (value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private List<ChatbotMatriculaProceso> findMaskedCandidates(MaskLookupParts docMask, MaskLookupParts emailMask) {
        return procesoRepository.findCandidatesForMaskedLookup(
                docMask.prefix(),
                docMask.suffix(),
                emailMask.prefix(),
                emailMask.suffix(),
                PageRequest.of(0, 200)
        );
    }

    private Optional<ChatbotMatriculaProceso> resolveMaskedCandidates(List<ChatbotMatriculaProceso> candidates,
                                                                      MaskLookupParts docMask,
                                                                      MaskLookupParts emailMask,
                                                                      MaskLookupParts phoneMask) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        List<ChatbotMatriculaProceso> filtered = new ArrayList<>();
        for (ChatbotMatriculaProceso candidate : candidates) {
            String candidateDocument = trim(candidate.getNumeroDocumento()).replaceAll("\\D+", "");
            String candidateEmail = trim(candidate.getEmail()).toLowerCase(Locale.ROOT);
            String candidatePhone = trim(candidate.getPhone());
            if (candidatePhone.isBlank()) {
                candidatePhone = trim(candidate.getTelefono());
            }
            candidatePhone = candidatePhone.replaceAll("\\D+", "");
            if (!docMask.matches(candidateDocument)) {
                continue;
            }
            if (!emailMask.matches(candidateEmail)) {
                continue;
            }
            if (!phoneMask.matches(candidatePhone)) {
                continue;
            }
            filtered.add(candidate);
        }
        if (filtered.size() == 1) {
            return Optional.of(filtered.get(0));
        }
        return Optional.empty();
    }

    private Optional<ChatbotMatriculaProceso> resolveByIntersection(List<ChatbotMatriculaProceso> docCandidates,
                                                                    List<ChatbotMatriculaProceso> emailCandidates,
                                                                    MaskLookupParts phoneMask) {
        if (docCandidates == null || docCandidates.isEmpty() || emailCandidates == null || emailCandidates.isEmpty()) {
            return Optional.empty();
        }
        List<ChatbotMatriculaProceso> intersections = new ArrayList<>();
        for (ChatbotMatriculaProceso docCandidate : docCandidates) {
            String doc = trim(docCandidate.getNumeroDocumento());
            if (doc.isBlank()) {
                continue;
            }
            for (ChatbotMatriculaProceso emailCandidate : emailCandidates) {
                if (doc.equals(trim(emailCandidate.getNumeroDocumento()))) {
                    intersections.add(docCandidate);
                    break;
                }
            }
        }
        if (phoneMask.hasCriteria()) {
            List<ChatbotMatriculaProceso> byPhone = new ArrayList<>();
            for (ChatbotMatriculaProceso candidate : intersections) {
                String candidatePhone = trim(candidate.getPhone());
                if (candidatePhone.isBlank()) {
                    candidatePhone = trim(candidate.getTelefono());
                }
                candidatePhone = candidatePhone.replaceAll("\\D+", "");
                if (phoneMask.matches(candidatePhone)) {
                    byPhone.add(candidate);
                }
            }
            intersections = byPhone;
        }
        if (intersections.size() == 1) {
            return Optional.of(intersections.get(0));
        }
        return Optional.empty();
    }

    private record MaskLookupParts(String prefix, String suffix) {
        private static MaskLookupParts empty() {
            return new MaskLookupParts("", "");
        }

        static MaskLookupParts forDocument(String raw) {
            String value = raw == null ? "" : raw.trim().replaceAll("\\s+", "");
            if (value.isBlank() || !value.contains("*")) {
                return empty();
            }
            String normalized = value.replaceAll("[^0-9*]", "");
            if (normalized.isBlank() || !normalized.contains("*")) {
                return empty();
            }
            int firstMask = normalized.indexOf('*');
            int lastMask = normalized.lastIndexOf('*');
            String prefix = normalized.substring(0, firstMask).replaceAll("\\D+", "");
            String suffix = normalized.substring(lastMask + 1).replaceAll("\\D+", "");
            if (prefix.isBlank() && suffix.isBlank()) {
                return empty();
            }
            return new MaskLookupParts(prefix, suffix);
        }

        static MaskLookupParts forEmail(String raw) {
            String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (value.isBlank() || !value.contains("*") || !value.contains("@")) {
                return empty();
            }
            int at = value.indexOf('@');
            if (at <= 0 || at >= value.length() - 1) {
                return empty();
            }
            String local = value.substring(0, at);
            String domain = value.substring(at + 1);
            int firstMask = local.indexOf('*');
            int lastMask = local.lastIndexOf('*');
            if (firstMask < 0) {
                return empty();
            }
            String prefix = local.substring(0, firstMask);
            String suffixLocal = local.substring(lastMask + 1);
            String suffix = suffixLocal + "@" + domain;
            if (prefix.isBlank() && suffix.isBlank()) {
                return empty();
            }
            return new MaskLookupParts(prefix, suffix);
        }

        boolean hasCriteria() {
            return !prefix.isBlank() || !suffix.isBlank();
        }

        boolean matches(String valueRaw) {
            if (!hasCriteria()) {
                return true;
            }
            String value = valueRaw == null ? "" : valueRaw.trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) {
                return false;
            }
            if (!prefix.isBlank() && !value.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (!suffix.isBlank() && !value.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return false;
            }
            return true;
        }
    }

    private String buildStudentDisplayName(Estudiante estudiante) {
        if (estudiante == null) return "estudiante";

        String fullName = collapseSpaces(trim(estudiante.getNombre()) + " " + trim(estudiante.getApellido()));
        if (!fullName.isBlank()) return fullName;

        String user = trim(estudiante.getUsuario());
        return user.isBlank() ? "estudiante" : user;
    }

    private LocalDate resolveEnrollmentDate(ChatbotMatriculaProceso proceso) {
        Instant enrolledAt = proceso == null ? null : proceso.getEnrolledAt();
        if (enrolledAt != null) {
            return enrolledAt.atZone(ZoneId.of("America/Bogota")).toLocalDate();
        }
        return LocalDate.now(ZoneId.of("America/Bogota"));
    }

    private String maskEmail(String email) {
        String e = trim(email).toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        String user = e.substring(0, at);
        String domain = e.substring(at);
        return user.charAt(0) + "***" + domain;
    }

    private Long resolveStudentIdByDocumento(String documento) {
        return estudianteRepository.findByNumeroDocumento(normalizeDoc(documento))
                .filter(e -> !Boolean.FALSE.equals(e.getVisible()))
                .map(Estudiante::getId)
                .orElseThrow(() -> new NoSuchElementException("No existe un estudiante activo con ese documento"));
    }

    private Long requireStudentId(Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("ID de estudiante invalido");
        }
        return studentId;
    }
}
