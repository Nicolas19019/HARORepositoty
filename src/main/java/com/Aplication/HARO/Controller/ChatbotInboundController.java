package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentSyncContextService;
import com.Aplication.HARO.Service.ProspectoService;
import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotInboundController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotInboundController.class);
    private static final String ADVISOR_WHATSAPP_LINK = "https://wa.me/573202114876";

    private static final int MAX_SESSIONS = 5000;
    /**
     * Formato esperado (un solo mensaje):
     *   Nombre(s) + Documento + Categoría
     *
     * Ejemplos:
     *   Juan Perez 12345678 A2
     *   Juan Perez 12345678 B1
     *   Juan Perez 12345678 C1
     *   Juan Perez 12345678 A2 y B1
     *   Juan Perez 12345678 A2, B1 y C1
     */
    private static final Pattern ENROLLMENT_BASIC_PATTERN = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+(\\d{5,20})\\s+(" +
                    "A2\\s*(?:Y|,|\\+|/)\\s*B1\\s*(?:Y|,|\\+|/)\\s*C1" + // A2 y B1 y C1
                    "|A2\\s*(?:Y|,|\\+|/)\\s*B1" +                       // A2 y B1
                    "|A2\\s*(?:Y|,|\\+|/)\\s*C1" +                       // A2 y C1 (por si luego lo usas)
                    "|B1\\s*(?:Y|,|\\+|/)\\s*C1" +                       // B1 y C1 (por si luego lo usas)
                    "|A2|B1|C1" +
                    ")\\s*$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{8,15}$");

    // YYYY-MM-DD HH:mm
    private static final Pattern BOOKING_SLOT_PATTERN = Pattern.compile(
            "^\\s*(\\d{4}-\\d{2}-\\d{2})\\s+([0-2]\\d:[0-5]\\d)\\s*$"
    );

    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d{4,10}$");
    private static final Pattern OTP_COOLDOWN_SECONDS_PATTERN = Pattern.compile("(?i)\\bespera\\s+(\\d+)s\\b");
    private static final Pattern DOCUMENT_PATTERN = Pattern.compile("^\\d{5,20}$");

    private final ChatbotProcesoService procesoService;
    private final PaymentSyncContextService paymentSyncContextService;
    private final ProspectoService prospectoService;
    private final VerificationService verificationService;
    private final WhatsAppTemplateService waService;

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.contract.ui-url:}")
    private String contractUiUrl;

    @Value("${chatbot.inactivity.timeout.minutes:15}")
    private long inactivityTimeoutMinutes;

    @Value("${chatbot.booking.duration.minutes:120}")
    private int bookingDurationMinutes;

    @Value("${chatbot.booking.cancel.min-hours:48}")
    private long bookingCancelMinHours;

    @Value("${chatbot.enrollment.welcome-image-url:}")
    private String enrollmentWelcomeImageUrl;
    @Value("${chatbot.inbound.include-image-action:false}")
    private boolean includeImageActionInInbound;

    public ChatbotInboundController(ChatbotProcesoService procesoService,
                                    PaymentSyncContextService paymentSyncContextService,
                                    ProspectoService prospectoService,
                                    VerificationService verificationService,
                                    WhatsAppTemplateService waService) {
        this.procesoService = procesoService;
        this.paymentSyncContextService = paymentSyncContextService;
        this.prospectoService = prospectoService;
        this.verificationService = verificationService;
        this.waService = waService;
    }

   public record InboundMessage(
        String from,
        String text,
        String messageId,
        String timestamp,
        String phoneNumberId,
        Boolean newConversation
) {}
    public record BotAction(
            String type,
            String body,
            String name,
            String lang,
            List<String> params
    ) {}

    public record BotResponse(List<BotAction> actions) {}

    private record EnrollmentBasic(String nombre, String documento, String categoria) {}
    private record BookingSlot(String fecha, String hora) {}

    enum ChatState {
        MAIN_MENU,
        COURSES_MENU,

        ENROLLMENT_DATA_AUTH_WAIT,
        ENROLLMENT_CAPTURE,
        ENROLLMENT_AGE_CAPTURE,
        ENROLLMENT_EMAIL_CAPTURE,
        ENROLLMENT_PHONE_CAPTURE,
        ENROLLMENT_ADDRESS_CAPTURE,
        ENROLLMENT_SEDE_CAPTURE,
        ENROLLMENT_CONFIRM,
        PAYMENT_METHOD_SELECT,
        PAYMENT_PLAN_SELECT,
        PAYMENT_WAIT,
        PAYMENT_CASH_WAIT,
        CONTRACT_WAIT,
        ENROLLMENT_ABORT_CONFIRM,

        SEDE_SELECTION,

        STUDENT_MENU,
        STUDENT_DOC_CAPTURE,
        STUDENT_OTP_VERIFY,
        STUDENT_BOOKING_TYPE,
        STUDENT_BOOKING_SLOT,
        STUDENT_CANCEL_CLASS_PICK,
        STUDENT_LOGOUT_CONFIRM,

        DONE
    }

    enum StudentAction {
        NONE,
        VIEW_CALENDAR,
        VIEW_SCHEDULE,
        BOOK_CLASS,
        CANCEL_CLASS
    }

    enum EnrollmentAbortAction {
        NONE,
        CANCEL_TO_MENU,
        END_CONVERSATION
    }

    static class SessionData {
        ChatState state = ChatState.MAIN_MENU;
        boolean courseOptionsExpanded = false;

        // Captura matrícula
        String nombre;
        String documento;
        String categoria;
        Integer edad;
        String email;
        String telefono;
        String direccion;

        // Sede seleccionada
        String sedeSeleccionada;

        // Subflujo estudiante + OTP
        StudentAction pendingStudentAction = StudentAction.NONE;
        Long studentId;
        String studentDocumento;
        String studentEmail;
        String studentNombre;
        String studentCategoria;
        String studentTipoPase; // carro | moto | carro,moto
        String studentSede;
        String studentBookingDate;
        String studentBookingTipoPase; // carro | moto (solo para agendar)
        boolean studentOtpVerified = false;

        // Confirmaciones (matricula)
        ChatState pendingEnrollmentAbortReturnState;
        EnrollmentAbortAction pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;

        // Confirmacion (cerrar sesion estudiante)
        ChatState pendingStudentLogoutReturnState;

        Instant lastSeen = Instant.now();
    }

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    @PostMapping(value = "/inbound", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<BotResponse> inbound(@RequestBody InboundMessage msg) {
        evictStaleSessions();

        String from = trim(msg == null ? null : msg.from());
        String rawText = trim(msg == null ? null : msg.text());
        String text = normalizeInput(rawText);

        if (from.isBlank()) {
            return ResponseEntity.ok(new BotResponse(List.of(
                    textMsg("⚠️ No pude identificar tu número.\n\nEscribe MENU para volver al inicio.")
            )));
        }

        boolean newConversation = msg != null && Boolean.TRUE.equals(msg.newConversation());
        if (newConversation) {
            sessions.remove(from);
            log.info("NEW CONVERSATION RESET -> from={}", maskPhone(from));
        }

        SessionData session = sessions.computeIfAbsent(from, k -> new SessionData());
        log.debug("CHATBOT INBOUND from={} state={} textLength={} newConversation={}",
                maskPhone(from),
                session.state,
                rawText.length(),
                newConversation);

        List<BotAction> actions = new ArrayList<>();

        synchronized (session) {
            Instant now = Instant.now();

            // Inactividad
            if (isConversationExpired(session, now)) {
                log.info("Session expired by inactivity from={} state={}", maskPhone(from), session.state);
                sessions.remove(from);
                actions.add(textMsg(inactivityTimeoutText()));
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Cerrar sesion de estudiante (sin finalizar conversacion)
            if (isStudentLogoutCommand(text) && (session.studentId != null || session.studentOtpVerified)) {
                session.pendingStudentLogoutReturnState = session.state;
                session.state = ChatState.STUDENT_LOGOUT_CONFIRM;
                actions.add(textMsg(studentLogoutConfirmText(session.studentNombre)));
                actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Finalizar
            if (isEndCommand(text)) {
                if (shouldConfirmEnrollmentAbort(session.state)) {
                    session.pendingEnrollmentAbortReturnState = session.state;
                    session.pendingEnrollmentAbortAction = EnrollmentAbortAction.END_CONVERSATION;
                    session.state = ChatState.ENROLLMENT_ABORT_CONFIRM;
                    actions.add(textMsg(enrollmentAbortConfirmText(true)));
                    actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
                    session.lastSeen = now;
                    return ResponseEntity.ok(new BotResponse(actions));
                }
                sessions.remove(from);
                actions.add(textMsg(conversationEndedText()));
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Menú
            if (isMenuCommand(text)) {
                resetToMain(session);
                actions.add(textMsg(mainMenuText()));
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Ayuda
            if (isHelpCommand(text)) {
                actions.add(textMsg(helpText(session.state)));
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Contactar asesor por comando textual en cualquier estado
            if (isAdvisorCommand(text)) {
                actions.add(textMsg(advisorContactText()));
                actions.add(textMsg("Opciones: MENU"));
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Cancelar
            if (isCancelCommand(text)) {
                if (shouldConfirmEnrollmentAbort(session.state)) {
                    session.pendingEnrollmentAbortReturnState = session.state;
                    session.pendingEnrollmentAbortAction = EnrollmentAbortAction.CANCEL_TO_MENU;
                    session.state = ChatState.ENROLLMENT_ABORT_CONFIRM;
                    actions.add(textMsg(enrollmentAbortConfirmText(false)));
                    actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
                    session.lastSeen = now;
                    return ResponseEntity.ok(new BotResponse(actions));
                }
                if (session.state == ChatState.MAIN_MENU) {
                    actions.add(textMsg(mainMenuText()));
                } else {
                    resetToMain(session);
                    actions.add(textMsg("❌ Proceso cancelado.\n\n" + mainMenuText()));
                }
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Debug
            if ("estado".equals(text)) {
                actions.add(textMsg("🧩 Estado actual: " + session.state + "\n\nEscribe MENU para volver."));
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            if (shouldSyncEnrollmentFlow(session.state, text)
                    && resumeEnrollmentAfterExternalPayment(from, session, actions)) {
                session.lastSeen = now;
                return ResponseEntity.ok(new BotResponse(actions));
            }

            switch (session.state) {
                case MAIN_MENU -> handleMainMenu(text, from, session, actions);
                case COURSES_MENU -> handleCoursesMenu(text, from, session, actions);

                case ENROLLMENT_DATA_AUTH_WAIT -> handleEnrollmentDataAuthWait(text, session, actions);
                case ENROLLMENT_CAPTURE -> handleEnrollmentCapture(from, rawText, session, actions);
                case ENROLLMENT_AGE_CAPTURE -> handleEnrollmentAgeCapture(text, session, actions);
                case ENROLLMENT_EMAIL_CAPTURE -> handleEnrollmentEmailCapture(text, session, actions);
                case ENROLLMENT_PHONE_CAPTURE -> handleEnrollmentPhoneCapture(text, session, actions);
                 case ENROLLMENT_ADDRESS_CAPTURE -> handleEnrollmentAddressCapture(rawText, session, actions);
                 case ENROLLMENT_SEDE_CAPTURE -> handleEnrollmentSedeCapture(from, text, session, actions);
                 case ENROLLMENT_CONFIRM -> handleEnrollmentConfirm(text, session, actions);
                 case PAYMENT_METHOD_SELECT -> handlePaymentMethodSelect(text, session, actions);
                 case PAYMENT_PLAN_SELECT -> handlePaymentPlanSelect(text, session, actions);
                 case PAYMENT_WAIT -> handlePaymentWait(text, session, actions);
                 case PAYMENT_CASH_WAIT -> handlePaymentCashWait(text, session, actions);
                 case CONTRACT_WAIT -> handleContractWait(from, text, session, actions);
                 case ENROLLMENT_ABORT_CONFIRM -> handleEnrollmentAbortConfirm(from, text, session, actions);

                case SEDE_SELECTION -> handleSedeSelection(from, text, session, actions);

                case STUDENT_MENU -> handleStudentMenu(text, session, actions);
                case STUDENT_DOC_CAPTURE -> handleStudentDocCapture(text, session, actions);
                case STUDENT_OTP_VERIFY -> handleStudentOtpVerify(text, session, actions);
                case STUDENT_BOOKING_TYPE -> handleStudentBookingType(text, session, actions);
                case STUDENT_BOOKING_SLOT -> handleStudentBookingSlot(rawText, session, actions);
                case STUDENT_CANCEL_CLASS_PICK -> handleStudentCancelClassPick(text, session, actions);
                case STUDENT_LOGOUT_CONFIRM -> handleStudentLogoutConfirm(text, session, actions);

                case DONE -> {
                sessions.remove(from);
                actions.add(textMsg("✅ Tu proceso ya fue completado.\n\nEscribe *MENU* para iniciar una nueva solicitud."));
            }
            }

            session.lastSeen = now;
        }

        return ResponseEntity.ok(new BotResponse(actions));
    }

    // =========================
    // HANDLERS
    // =========================

    private void handleMainMenu(String text, String from, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if (isEnrollmentCommand(cmd)) {
            startEnrollmentAuthorization(session, actions);
            return;
        }

        switch (cmd) {
            case "1", "cursos", "curso", "categorias", "categoria" -> {
                trackProspectServiceSafe(from, session, "SERVICIOS_MENU");
                session.state = ChatState.COURSES_MENU;
                session.courseOptionsExpanded = false;
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "2" -> {
                trackProspectServiceSafe(from, session, "INICIAR_MATRICULA");
                startEnrollmentAuthorization(session, actions);
            }
            case "3", "informacion", "info", "horarios" -> {
                trackProspectServiceSafe(from, session, "HORARIOS_Y_SEDES");
                actions.add(textMsg(infoText()));
                actions.add(textMsg(practicalProcessText()));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "4", "estudiante", "soy estudiante" -> {
                if (session.studentOtpVerified && session.studentId != null) {
                    session.state = ChatState.STUDENT_MENU;
                    actions.add(textMsg("🎓 Ya tienes una sesion de estudiante activa."));
                    actions.add(textMsg(studentGreetingText(session.studentNombre)));
                    actions.add(textMsg(studentMenuText()));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                if (session.studentId != null && !session.studentOtpVerified && !trim(session.studentEmail).isBlank()) {
                    session.state = ChatState.STUDENT_OTP_VERIFY;
                    actions.add(textMsg(
                            "🔐 Ya iniciamos tu verificacion como estudiante.\n\n" +
                                    "Escribe el codigo OTP que enviamos al correo " + maskEmail(session.studentEmail) + ".\n\n" +
                                    "Si no lo encuentras, revisa Spam/No deseado o escribe tu documento nuevamente para solicitar otro codigo."
                    ));
                    actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
                    return;
                }

                clearStudentAccessData(session);
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg(
                        "🎓 ¡Perfecto! Vamos a validar tu acceso como estudiante.\n\n" +
                                "1) Escribe tu numero de documento (solo numeros).\n" +
                                "2) Te enviaremos un OTP al correo registrado.\n" +
                                "3) Con una sola validacion podras navegar por el menu de estudiante hasta que cierres sesion o expire por inactividad.\n\n" +
                                "Ejemplo: 12345678"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "5", "asesor", "contactar asesor", "contactar un asesor", "hablar con asesor" -> {
                trackProspectServiceSafe(from, session, "CONTACTAR_ASESOR");
                actions.add(textMsg(advisorContactText()));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "link", "contrato", "contratos", "link contrato", "link contratos" -> {
                try {
                    Optional<ChatbotMatriculaProceso> procesoOpt = procesoService.findLatestProcesoByPhone(from);
                    if (procesoOpt.isEmpty()) {
                        actions.add(textMsg(
                                "⚠️ No encuentro un proceso reciente asociado a este número.\n\n" +
                                        "Escribe *MATRICULA* para iniciar un nuevo proceso."
                        ));
                        actions.add(textMsg(mainMenuText()));
                        return;
                    }

                    ChatbotMatriculaProceso proceso = procesoOpt.get();
                    String paymentStatus = trim(proceso.getPaymentStatus()).toUpperCase(Locale.ROOT);
                    if (!"APPROVED".equals(paymentStatus)) {
                        String payLink = trim(proceso.getPaymentLink());
                        if (!payLink.isBlank()) {
                            actions.add(textMsg(
                                    "💳 Aún no tenemos el pago confirmado.\n\n" +
                                            "Enlace de pago:\n" + payLink
                            ));
                        } else {
                            actions.add(textMsg(
                                    "⚠️ Aún no hay un enlace de contratos disponible porque el pago no está confirmado.\n\n" +
                                            "Escribe *MATRICULA* para iniciar o continúa tu proceso."
                            ));
                        }
                        actions.add(textMsg("Opciones: MENU"));
                        return;
                    }

                    // En este punto el pago está aprobado: entregamos (y si aplica regeneramos) el enlace de contrato.
                    session.documento = trim(proceso.getNumeroDocumento());
                    session.state = ChatState.CONTRACT_WAIT;

                    String link = normalizeStoredContractLink(proceso.getContractLink());
                    Instant expiresAt = null;

                    if (link.isBlank() || shouldRefreshContractLink(link)) {
                        ContractUserLink renewed = createAndStoreContractLink(session.documento);
                        link = renewed.url();
                        expiresAt = renewed.expiresAt();
                    } else {
                        VerificationService.ContractAccessResult peek = peekContractLinkAccess(link);
                        expiresAt = peek == null ? null : peek.expiresAt();
                    }

                    if (link.isBlank()) {
                        actions.add(textMsg("⚠️ Aún no hay un enlace de contrato disponible."));
                        actions.add(textMsg("Opciones: MENU"));
                        return;
                    }

                    actions.add(textMsg(
                            "📄 *Enlace de contrato*\n\n" +
                                    link + "\n\n" +
                                    buildContractExpiryHint(expiresAt) + "\n\n" +
                                    "Cuando termines, escribe *LISTO*."
                    ));
                    actions.add(textMsg("Opciones: MENU | LINK"));
                } catch (Exception e) {
                    log.error("No se pudo resolver link de contrato desde menu principal from={}: {}", maskPhone(from), e.getMessage(), e);
                    actions.add(textMsg("⚠️ No pude obtener el enlace de contrato en este momento. Intenta nuevamente."));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            default -> {
                actions.add(textMsg("⚠️ Opción no reconocida en este menú. Escribe 1, 2, 3, 4 o 5, o MENU."));
                actions.add(textMsg(mainMenuText()));
            }
        }
    }

    private void handleCoursesMenu(String text, String from, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if (isEnrollmentCommand(cmd)) {
            session.courseOptionsExpanded = false;
            startEnrollmentAuthorization(session, actions);
            return;
        }

        // "ver todas/combos" por compatibilidad
        if ("todos".equals(cmd) || "todas".equals(cmd) || "ver todas".equals(cmd) || "combos".equals(cmd) || "ver todos".equals(cmd)) {
            session.courseOptionsExpanded = true;
            actions.add(textMsg(allCategoriesMenuText()));
            actions.add(textMsg("Opciones: MATRICULA | MENU"));
            return;
        }

        // Menú expandido
        if (session.courseOptionsExpanded) {
            switch (cmd) {
                case "1", "a2" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2");
                    actions.add(textMsg(courseA2Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "2", "b1" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_B1");
                    actions.add(textMsg(courseB1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "3", "c1" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_C1");
                    actions.add(textMsg(courseC1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "4", "a2 y b1", "a2+b1", "a2/b1", "a2,b1" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2_B1");
                    actions.add(textMsg(courseA2B1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "5", "a2, b1 y c1", "a2 b1 y c1", "a2+b1+c1", "a2/b1/c1", "a2,b1,c1" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2_B1_C1");
                    actions.add(textMsg(courseA2B1C1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "6", "refuerzo carro", "clase de refuerzo carro", "clases de refuerzo carro" -> {
                    trackProspectServiceSafe(from, session, "REFUERZO_CARRO");
                    actions.add(textMsg(serviceRefuerzoCarroText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de carro por favor.")));
                    actions.add(textMsg("Opciones: MENU"));
                }
                case "7", "refuerzo moto", "clase de refuerzo moto", "clases de refuerzo moto" -> {
                    trackProspectServiceSafe(from, session, "REFUERZO_MOTO");
                    actions.add(textMsg(serviceRefuerzoMotoText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de moto por favor.")));
                    actions.add(textMsg("Opciones: MENU"));
                }
                case "8", "recategorizacion", "recategorización", "b1 a c1", "b1->c1" -> {
                    trackProspectServiceSafe(from, session, "RECATEGORIZACION_B1_C1");
                    actions.add(textMsg(courseRecategorizacionText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de la recategorizacion B1 a C1 por favor.")));
                    actions.add(textMsg("Opciones: MENU"));
                }
            default -> {
                actions.add(textMsg("⚠️ Opción no reconocida para este listado de cursos."));
                actions.add(textMsg(allCategoriesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            }
            return;
        }

        // Menú principal de cursos (1..8)
        switch (cmd) {
            case "1", "a2" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2");
                actions.add(textMsg(courseA2Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "2", "b1" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_B1");
                actions.add(textMsg(courseB1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "3", "c1" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_C1");
                actions.add(textMsg(courseC1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "4", "a2 y b1", "a2+b1", "a2/b1", "a2,b1" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2_B1");
                actions.add(textMsg(courseA2B1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "5", "a2, b1 y c1", "a2 b1 y c1", "a2+b1+c1", "a2/b1/c1", "a2,b1,c1" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2_B1_C1");
                actions.add(textMsg(courseA2B1C1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "6", "refuerzo carro", "clase de refuerzo carro", "clases de refuerzo carro" -> {
                trackProspectServiceSafe(from, session, "REFUERZO_CARRO");
                actions.add(textMsg(serviceRefuerzoCarroText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de carro por favor.")));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "7", "refuerzo moto", "clase de refuerzo moto", "clases de refuerzo moto" -> {
                trackProspectServiceSafe(from, session, "REFUERZO_MOTO");
                actions.add(textMsg(serviceRefuerzoMotoText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de moto por favor.")));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "8", "recategorizacion", "recategorización", "b1 a c1", "b1->c1" -> {
                trackProspectServiceSafe(from, session, "RECATEGORIZACION_B1_C1");
                actions.add(textMsg(courseRecategorizacionText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de la recategorizacion B1 a C1 por favor.")));
                actions.add(textMsg("Opciones: MENU"));
            }
            default -> {
                actions.add(textMsg("⚠️ Opción no reconocida para este menú. Escoge 1 a 8, MATRICULA o MENU."));
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
        }
    }

    private void startEnrollmentAuthorization(SessionData session, List<BotAction> actions) {
        clearEnrollmentData(session);
        session.state = ChatState.ENROLLMENT_DATA_AUTH_WAIT;
        addEnrollmentIntro(actions);
    }

    private void handleEnrollmentDataAuthWait(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if (isEnrollmentDataAuthRejected(cmd)) {
            resetToMain(session);
            actions.add(textMsg(
                    "Entendido. Sin tu autorizacion no podemos continuar con la matricula.\n\n" +
                            mainMenuText()
            ));
            return;
        }

        if (isEnrollmentDataAuthAccepted(cmd)) {
            session.state = ChatState.ENROLLMENT_CAPTURE;
            actions.add(textMsg(enrollmentInitialPromptText()));
            actions.add(textMsg("Despues de ese primer mensaje te pedire tu edad, correo, telefono, direccion y la sede."));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        actions.add(textMsg("Para continuar, responde SI o NO sobre el tratamiento de datos."));
        actions.add(textMsg("Opciones: SI | NO | MENU | CANCELAR"));
    }

    private void handleEnrollmentCapture(String from, String rawText, SessionData session, List<BotAction> actions) {
        EnrollmentBasic basic = parseEnrollmentBasic(rawText);
        if (basic == null) {
            actions.add(textMsg(
                    "⚠️ No pude leer tus datos.\n\n" +
                            "Envía TODO en un solo mensaje así:\n" +
                            "Nombre Apellido Documento Categoría\n\n" +
                            "Ejemplo: Juan Perez 12345678 A2\n\n" +
                            "Categorías: A2 | B1 | C1 | A2 y B1 | A2, B1 y C1"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.nombre = basic.nombre();
        session.documento = basic.documento();
        session.categoria = basic.categoria();
        session.state = ChatState.ENROLLMENT_AGE_CAPTURE;

        trackProspectServiceSafe(from, session, prospectServiceFromCategoria(session.categoria));

        actions.add(textMsg(
                "Paso 2 de 8: envía tu edad en años.\n\n" +
                        "Ejemplo: 18\n\n" +
                        "Importante: debes tener mínimo 16 años para matricularte."
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentAgeCapture(String text, SessionData session, List<BotAction> actions) {
        String digits = trim(text).replaceAll("\\D+", "");
        if (digits.isBlank()) {
            actions.add(textMsg(
                    "⚠️ Edad inválida.\n\n" +
                            "Envía solo tu edad en años (número).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        final int age;
        try {
            age = Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            actions.add(textMsg(
                    "⚠️ Edad inválida.\n\n" +
                            "Envía solo tu edad en años (número).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        if (age < 16) {
            resetToMain(session);
            actions.add(textMsg(
                    "⚠️ En este momento no podemos continuar con la matrícula.\n\n" +
                            "La matrícula está disponible desde los 16 años.\n\n" +
                            "Si necesitas ayuda, puedes contactar un asesor."
            ));
            actions.add(textMsg(advisorContactText()));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        if (age > 100) {
            actions.add(textMsg(
                    "⚠️ Edad inválida.\n\n" +
                            "Envía solo tu edad en años (número).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.edad = age;
        session.state = ChatState.ENROLLMENT_EMAIL_CAPTURE;

        actions.add(textMsg(
                "Paso 3 de 8: envia tu correo electronico.\n" +
                        "Ejemplo: usuario@correo.com"
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentEmailCapture(String text, SessionData session, List<BotAction> actions) {
        String email = trim(text).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            actions.add(textMsg(
                    "⚠️ Correo inválido.\n\n" +
                            "Envía solo el correo, por ejemplo: usuario@correo.com"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.email = email;
        session.state = ChatState.ENROLLMENT_PHONE_CAPTURE;

        actions.add(textMsg(
                "Paso 4 de 8: envia tu telefono de contacto.\n" +
                        "Ejemplo: 573001112233"
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentPhoneCapture(String text, SessionData session, List<BotAction> actions) {
        String phone = trim(text).replaceAll("\\s+", "");
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            actions.add(textMsg(
                    "Telefono invalido.\n\n" +
                            "Debes enviar entre 8 y 15 digitos (puede iniciar con +).\n" +
                            "Ejemplo: 573001112233"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.telefono = phone;
        session.state = ChatState.ENROLLMENT_ADDRESS_CAPTURE;
        actions.add(textMsg(
                "Paso 5 de 8: envia tu direccion de residencia.\n\n" +
                        "Escríbela completa con barrio, nomenclatura o apartamento si aplica.\n\n" +
                        "Ejemplo: Cra 80 #12-45 Apto 302, Kennedy, Bogota"
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentAddressCapture(String rawText, SessionData session, List<BotAction> actions) {
        String address = collapseSpaces(rawText);
        if (address.length() < 8) {
            actions.add(textMsg(
                    "⚠️ Direccion invalida.\n\n" +
                            "Enviala con mas detalle para poder registrarla correctamente.\n" +
                            "Ejemplo: Cra 80 #12-45 Apto 302, Kennedy, Bogota"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.direccion = address;
        session.state = ChatState.ENROLLMENT_SEDE_CAPTURE;
        actions.add(textMsg(
                "Paso 6 de 8: selecciona tu sede.\n\n" +
                        "1) Kennedy - Av. 1 de Mayo #68D-23 Piso 2\n" +
                        "2) CC El Eden - Local L2-094A\n\n" +
                        "Responde con 1 o 2."
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentSedeCapture(String from, String text, SessionData session, List<BotAction> actions) {
        String sede = resolveSedeSelection(text);
        if (sede.isBlank()) {
            actions.add(textMsg(
                    "Debes seleccionar una sede valida.\n\n" +
                            "1) Kennedy\n" +
                            "2) CC El Eden\n\n" +
                            "Responde con 1 o 2."
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.sedeSeleccionada = sede;

        try {
            procesoService.upsertDraft(
                    from,
                    session.nombre,
                    session.documento,
                    session.categoria,
                    session.email,
                    session.telefono,
                    session.direccion,
                    session.sedeSeleccionada,
                    session.edad
            );
        } catch (Exception e) {
            log.error("No se pudo guardar pre-registro from={} doc={}",
                    maskPhone(from),
                    safe(session.documento),
                    e);
            actions.add(textMsg("⚠️ No pude guardar tu pre-registro en este momento. Intenta nuevamente."));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        session.state = ChatState.ENROLLMENT_CONFIRM;

        actions.add(textMsg(
                "Paso 7 de 8: revisa tus datos y confirma.\n\n" +
                        "Nombre: " + safe(session.nombre) + "\n" +
                        "Documento: " + safe(session.documento) + "\n" +
                        "Categoria: " + safe(session.categoria) + "\n" +
                        "Edad: " + (session.edad == null ? "N/A" : (session.edad + " años")) + "\n" +
                        "Correo: " + safe(session.email) + "\n" +
                        "Telefono: " + safe(session.telefono) + "\n" +
                        "Direccion: " + safe(session.direccion) + "\n" +
                        "Sede: " + safe(session.sedeSeleccionada) + "\n\n" +
                        "Responde:\n" +
                        "1) Confirmar y continuar\n" +
                        "2) Corregir datos\n" +
                        "3) Cancelar"
        ));
        actions.add(textMsg("Opciones: MENU"));
    }
    private void handleEnrollmentConfirm(String text, SessionData session, List<BotAction> actions) {
        switch (text) {
            case "1", "si", "sí", "confirmar", "continuar" -> {
                try {
                    ChatbotProcesoService.StudentDuplicateCheckResult duplicate =
                            procesoService.validateStudentUniquenessForEnrollment(session.documento, session.email);
                    if (duplicate.exists()) {
                        resetToMain(session);
                        actions.add(textMsg(
                                "Ya existe un estudiante registrado con los datos suministrados.\n\n" +
                                        "No es posible continuar con una nueva matricula.\n" +
                                        "Por favor comunicate con un asesor para revisar tu caso."
                        ));
                        actions.add(textMsg(advisorContactText()));
                        actions.add(textMsg("Opciones: MENU"));
                        return;
                    }

                    session.state = ChatState.PAYMENT_METHOD_SELECT;

                    actions.add(textMsg(
                            "Paso 8 de 8: selecciona tu metodo de pago.\n\n" +
                                    "1) Pagar por ePayco (en linea)\n" +
                                    "2) Pagar en efectivo en la academia\n\n" +
                                    "Responde 1 o 2."
                    ));
                    actions.add(textMsg("Opciones: MENU | TERMINAR"));
                } catch (Exception e) {
                    log.error("No se pudo iniciar pago from={} doc={} email={}",
                            maskPhone(session.telefono),
                            safe(session.documento),
                            maskEmail(session.email),
                            e);
                    actions.add(textMsg("⚠️ No pude iniciar el pago en este momento. Intenta de nuevo en 1 minuto."));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            case "2", "corregir" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("Despues de ese primer mensaje te pedire tu edad, correo, telefono, direccion y la sede."));
                actions.add(textMsg("Opciones: MENU | CANCELAR"));
            }
            case "3", "no", "cancelar" -> {
                resetToMain(session);
                actions.add(textMsg("❌ Proceso cancelado.\n\n" + mainMenuText()));
            }
            default -> {
                actions.add(textMsg("Responde 1, 2 o 3."));
                actions.add(textMsg("Opciones: MENU"));
            }
        }
    }

    private void handlePaymentMethodSelect(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);

        if ("1".equals(cmd) || cmd.contains("epayco") || cmd.contains("en linea") || cmd.contains("online")) {
            try {
                procesoService.setMetodoPago(session.documento, "EPAYCO");
            } catch (Exception ex) {
                // no bloquea el flujo, pero deja rastro en logs
                log.warn("No se pudo guardar metodoPago=EPAYCO doc={}: {}", safe(session.documento), ex.getMessage());
            }

            session.state = ChatState.PAYMENT_PLAN_SELECT;
            actions.add(textMsg(
                    "Elige como deseas pagar por ePayco:\n\n" +
                            "1) Pagar completo (100%)\n" +
                            "2) Pagar por la mitad (50%)\n\n" +
                            "Responde 1 o 2."
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
            return;
        }

        if ("2".equals(cmd) || cmd.contains("efectivo") || cmd.contains("academia")) {
            try {
                procesoService.markCashPaymentPending(session.documento);
            } catch (Exception ex) {
                log.error("No se pudo marcar pago en efectivo doc={}: {}", safe(session.documento), ex.getMessage(), ex);
                actions.add(textMsg("⚠️ No pude registrar el pago en efectivo en este momento. Intenta nuevamente."));
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            session.state = ChatState.PAYMENT_CASH_WAIT;
            actions.add(textMsg(
                    "✅ Tu proceso quedo registrado correctamente.\n\n" +
                            "⏳ El pago en efectivo debe ser confirmado por la academia antes de continuar.\n\n" +
                            "📩 Cuando validemos tu pago, te enviaremos por este chat el enlace para firmar los contratos."
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
            return;
        }

        actions.add(textMsg(
                "Selecciona el metodo de pago para continuar:\n\n" +
                        "1) Pagar por ePayco (en linea)\n" +
                        "2) Pagar en efectivo en la academia"
        ));
        actions.add(textMsg("Opciones: MENU | TERMINAR"));
    }

    private void handlePaymentPlanSelect(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        final String plan;
        final String planLabel;

        if ("1".equals(cmd) || cmd.contains("completo") || "full".equals(cmd) || "100".equals(cmd) || "100%".equals(cmd)) {
            plan = "FULL";
            planLabel = "completo (100%)";
        } else if ("2".equals(cmd) || cmd.contains("mitad") || cmd.contains("abono") || "half".equals(cmd) || "50".equals(cmd) || "50%".equals(cmd)) {
            plan = "HALF";
            planLabel = "por la mitad (50%)";
        } else {
            actions.add(textMsg(
                    "Responde 1 o 2 para continuar con el pago.\n\n" +
                            "1) Pagar completo (100%)\n" +
                            "2) Pagar por la mitad (50%)"
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
            return;
        }

        try {
            procesoService.markPaymentPending(session.documento, plan);
            String link = procesoService.getPaymentLink(session.documento);
            capturePaymentContextFromChat(link, session,
                    "HALF".equals(plan) ? "chatbot_inbound_payment_half" : "chatbot_inbound_payment_full");

            session.state = ChatState.PAYMENT_WAIT;

            actions.add(textMsg(
                    "Paso 8 de 8: realiza el pago para continuar (" + planLabel + ").\n\n" +
                            "Enlace de pago:\n" + link + "\n\n" +
                            "Cuando lo realices, vuelve a este chat.\n" +
                            "Si necesitas el enlace otra vez escribe: LINK\n\n" +
                            paymentFlowInfo()
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
        } catch (Exception e) {
            log.error("No se pudo iniciar pago plan={} from={} doc={} email={}",
                    plan,
                    maskPhone(session.telefono),
                    safe(session.documento),
                    maskEmail(session.email),
                    e);

            if ("HALF".equals(plan)) {
                actions.add(textMsg(
                        "No pude iniciar el pago por la mitad (50%) en este momento.\n\n" +
                                "Puedes pagar completo respondiendo 1, o escribir MENU."
                ));
                actions.add(textMsg("Opciones: 1 | MENU | TERMINAR"));
                return;
            }

            actions.add(textMsg("⚠️ No pude iniciar el pago en este momento. Intenta de nuevo en 1 minuto."));
            actions.add(textMsg("Opciones: MENU"));
        }
    }

    private void handlePaymentCashWait(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if ("link".equals(cmd) || cmd.contains("link")) {
            actions.add(textMsg(
                    "⏳ Aun no podemos continuar.\n\n" +
                            "El pago en efectivo debe ser confirmado por la academia.\n\n" +
                            "Cuando sea confirmado te enviaremos por este chat el enlace para firmar contratos."
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
            return;
        }

        actions.add(textMsg(
                "⏳ Estamos esperando la confirmacion del pago en efectivo.\n\n" +
                        "Cuando se confirme, te enviaremos por este chat el enlace para firmar los contratos.\n\n" +
                        "Escribe MENU para ver opciones o TERMINAR para salir."
        ));
        actions.add(textMsg("Opciones: MENU | TERMINAR"));
    }

        private void handlePaymentWait(String text, SessionData session, List<BotAction> actions) {
        switch (text) {

       
        case "pendiente" -> {
            actions.add(textMsg(
                    "⏳ Tu pago aparece como *PENDIENTE*.\n\n" +
                    "Esto puede tardar unos minutos dependiendo del banco.\n\n" +
                    "🔁 Si necesitas el enlace nuevamente escribe *LINK*."
            ));
            actions.add(textMsg("Opciones: MENU"));
        }

        case "aprobado" -> {
            actions.add(textMsg(
                    "🔐 Por seguridad, el pago no se valida por mensaje.\n\n" +
                    "La confirmación se realiza automáticamente con la pasarela de pago.\n\n" +
                    "Cuando el pago se confirme recibirás el siguiente paso en este chat."
            ));
            actions.add(textMsg("Opciones: MENU"));
        }

        case "ya pague", "ya pagué", "pague", "pagué" -> {
            actions.add(textMsg(
                    "✅ Perfecto.\n\n" +
                    "Estamos validando tu pago con la pasarela.\n\n" +
                    "📩 Cuando el pago sea confirmado te enviaremos el enlace para continuar con la firma del contrato."
            ));
            actions.add(textMsg("Opciones: MENU"));
        }

        case "link", "pagar" -> {
            try {
                String link = procesoService.getPaymentLink(session.documento);
                capturePaymentContextFromChat(link, session, "chatbot_inbound_relink");

                actions.add(textMsg(
                        "💳 *Enlace de pago*\n\n" +
                        link + "\n\n" +
                        "Después de pagar vuelve a este chat."
                ));
            } catch (Exception e) {
                actions.add(textMsg("⚠️ No pude encontrar el enlace de pago para este proceso."));
            }

            actions.add(textMsg("Opciones: MENU"));
        }

        default -> {
            actions.add(textMsg(
                    "💳 Estamos esperando la confirmación de tu pago.\n\n" +
                    "Puedes escribir:\n" +
                    "🔹 *LINK* para ver el enlace de pago\n" +
                    "🔹 *YA PAGUÉ* si ya realizaste el pago"
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
        }
    }
}

    private String paymentFlowInfo() {
        String confirmation = safe(procesoService.getPaymentConfirmationUrl());
        String response = safe(procesoService.getPaymentReturnUrl());

        if (confirmation.isBlank() && response.isBlank()) {
            return "";
        }

        StringBuilder out = new StringBuilder("Configuración del flujo de pago:\n");
        if (!confirmation.isBlank()) {
            out.append("- Confirmación: ").append(confirmation).append("\n");
        }
        if (!response.isBlank()) {
            out.append("- Retorno: ").append(response).append("\n");
        }
        return out.toString().trim();
    }

    private void capturePaymentContextFromChat(String paymentLink, SessionData session, String source) {
        if (session == null) {
            return;
        }

        String documento = trim(session.documento);
        String email = trim(session.email).toLowerCase(Locale.ROOT);
        String telefono = trim(session.telefono);
        if (documento.isBlank() && email.isBlank() && telefono.isBlank()) {
            return;
        }

        String invoice = firstNotBlank(
                queryParam(paymentLink, "x_id_invoice"),
                queryParam(paymentLink, "x_id_factura"),
                queryParam(paymentLink, "invoice"),
                queryParam(paymentLink, "p_id_invoice"),
                queryParam(paymentLink, "id_invoice")
        );

        String flowId = firstNotBlank(
                queryParam(paymentLink, "x_extra2"),
                queryParam(paymentLink, "flow_id"),
                queryParam(paymentLink, "flowId"),
                queryParam(paymentLink, "process_id"),
                queryParam(paymentLink, "processId"),
                queryParam(paymentLink, "matricula_id"),
                queryParam(paymentLink, "matriculaId")
        );

        if (invoice.isBlank() && !flowId.isBlank()) {
            invoice = "flow:" + flowId;
        }
        Long flowIdLong = null;
        if (!flowId.isBlank() && flowId.matches("^\\d{1,18}$")) {
            try {
                long parsed = Long.parseLong(flowId);
                if (parsed > 0) {
                    flowIdLong = parsed;
                }
            } catch (NumberFormatException ignored) {
                // Ignora flow_id invalido
            }
        }

        try {
            paymentSyncContextService.capture(
                    "",
                    "",
                    invoice,
                    "",
                    documento,
                    email,
                    telefono,
                    "PENDING",
                    source,
                    flowIdLong
            );
        } catch (Exception ex) {
            log.warn("No se pudo guardar contexto de pago desde chatbot. doc={} email={} source={} err={}",
                    safe(documento),
                    maskEmail(email),
                    source,
                    ex.getMessage());
        }
    }

    private String queryParam(String rawUrl, String key) {
        String url = trim(rawUrl);
        String searchKey = trim(key);
        if (url.isBlank() || searchKey.isBlank()) {
            return "";
        }

        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return "";
            }
            for (String chunk : query.split("&")) {
                if (chunk == null || chunk.isBlank()) {
                    continue;
                }
                String[] parts = chunk.split("=", 2);
                String parsedKey = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (!searchKey.equalsIgnoreCase(parsedKey)) {
                    continue;
                }
                String parsedValue = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                return trim(parsedValue);
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean shouldSyncEnrollmentFlow(ChatState state, String text) {
        if (state == ChatState.PAYMENT_WAIT) {
            return true;
        }
        if (state == ChatState.PAYMENT_CASH_WAIT) {
            return true;
        }
        if (state != ChatState.MAIN_MENU) {
            return false;
        }
        String cmd = normalizeCommandText(text);
        return "ya pague".equals(cmd)
                || "pague".equals(cmd)
                || "aprobado".equals(cmd)
                || "listo".equals(cmd)
                || "contrato".equals(cmd)
                || "contratos".equals(cmd)
                || "continuar".equals(cmd);
    }

    private boolean resumeEnrollmentAfterExternalPayment(String from, SessionData session, List<BotAction> actions) {
        Optional<ChatbotMatriculaProceso> procesoOpt = procesoService.findLatestProcesoByPhone(from);
        if (procesoOpt.isEmpty()) {
            return false;
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        String documento = trim(proceso.getNumeroDocumento());
        if (documento.isBlank()) {
            return false;
        }

        session.documento = documento;
        session.nombre = trim(proceso.getNombreCompleto());
        session.categoria = trim(proceso.getCategoria());
        session.email = trim(proceso.getEmail());
        session.telefono = trim(proceso.getTelefono());

        String paymentStatus = normalizeCommandText(proceso.getPaymentStatus()).toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(paymentStatus)) {
            return false;
        }

        String contractStatus = normalizeCommandText(proceso.getContractStatus()).toUpperCase(Locale.ROOT);
        if ("SIGNED".equals(contractStatus)) {
            if (proceso.getStudentId() != null) {
                session.state = ChatState.DONE;
                actions.add(textMsg(
                        "Pago y contrato ya confirmados.\n\n" +
                                "Tu matricula ya esta activa.\n" +
                                "Ref estudiante: " + proceso.getStudentId() + "\n\n" +
                                "Escribe MENU para continuar."
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
                return true;
            }

            session.state = ChatState.SEDE_SELECTION;
            actions.add(textMsg(
                    "Contrato validado.\n\n" +
                            "Antes de finalizar, selecciona tu sede:\n\n" +
                            "1) Kennedy - Av. 1 de Mayo #68D-23 Piso 2\n" +
                            "2) CC El Eden - Local L2-094A\n\n" +
                            "Responde con 1 o 2."
            ));
            actions.add(textMsg("Opciones: MENU"));
            return true;
        }

        String contractLink = normalizeStoredContractLink(proceso.getContractLink());
        if (!contractLink.equals(trim(proceso.getContractLink()))) {
            try {
                procesoService.updateContractLink(documento, contractLink);
            } catch (Exception ex) {
                log.warn("No se pudo normalizar contractLink almacenado doc={}: {}", documento, ex.getMessage());
            }
        }

        boolean linkAlreadySent = "LINK_SENT".equals(contractStatus);
        boolean refreshed = false;
        Instant contractExpiresAt = null;

        if (shouldRefreshContractLink(contractLink)) {
            try {
                ContractUserLink renewed = createAndStoreContractLink(documento);
                contractLink = renewed.url();
                contractExpiresAt = renewed.expiresAt();
                refreshed = true;
            } catch (Exception ex) {
                log.error("No se pudo reconstruir link de contrato doc={}: {}", documento, ex.getMessage(), ex);
            }
        }

        if (contractExpiresAt == null && !contractLink.isBlank()) {
            try {
                VerificationService.ContractAccessResult peek = peekContractLinkAccess(contractLink);
                contractExpiresAt = peek == null ? null : peek.expiresAt();
            } catch (Exception ignored) {
                contractExpiresAt = null;
            }
        }

        session.state = ChatState.CONTRACT_WAIT;
        if (!contractLink.isBlank()) {
            if (linkAlreadySent && !refreshed) {
                actions.add(textMsg(
                        "Pago confirmado.\n\n" +
                                "Ya te enviamos el enlace de contratos anteriormente.\n" +
                                "Si no lo encuentras, escribe *LINK* para recibirlo nuevamente.\n\n" +
                                "Cuando termines, responde *LISTO* para activar la matrícula."
                ));
                actions.add(textMsg("Opciones: MENU | LINK"));
                return true;
            }

            String expiryHint = buildContractExpiryHint(contractExpiresAt);
            actions.add(textMsg(
                    "Pago confirmado.\n\n" +
                            "Siguiente paso: firma tus contratos en este enlace:\n" + contractLink + "\n\n" +
                            expiryHint + "\n\n" +
                            "Cuando termines, responde *LISTO* para activar la matrícula."
            ));
        } else {
            actions.add(textMsg(
                    "Pago confirmado.\n\n" +
                            "Estamos generando tu enlace de contratos.\n" +
                            "En unos minutos te lo enviaremos por este chat."
            ));
        }
        actions.add(textMsg("Opciones: MENU"));
        return true;
    }

        private void handleContractWait(String from, String text, SessionData session, List<BotAction> actions) {

    switch (text) {

        case "listo", "firmado", "hecho" -> {
            try {
                boolean signed = procesoService.isContractSigned(session.documento);

                if (!signed) {
                    actions.add(textMsg(
                            "⏳ Aún no vemos el contrato firmado.\n\n" +
                            "1️⃣ Abre el enlace del contrato\n" +
                            "2️⃣ Firma el documento\n" +
                            "3️⃣ Luego escribe *LISTO* nuevamente."
                    ));
                    actions.add(textMsg("Opciones: MENU"));
                    return;
                }

                session.state = ChatState.SEDE_SELECTION;

                actions.add(textMsg(
                        "✅ Contrato validado correctamente.\n\n" +
                        "Ahora selecciona tu sede:\n\n" +
                        "1️⃣ Kennedy\n" +
                        "2️⃣ CC El Edén"
                ));
                actions.add(textMsg("Opciones: MENU"));

            } catch (Exception e) {
                actions.add(textMsg(
                        "⚠️ No pude validar el contrato.\n\n" +
                        "Detalle: " + e.getMessage()
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
        }

        case "link", "contrato", "contratos" -> {
            try {
                Optional<ChatbotMatriculaProceso> proceso =
                        procesoService.findProcesoByDocumento(session.documento);

                String link = normalizeStoredContractLink(proceso.map(ChatbotMatriculaProceso::getContractLink).orElse(""));
                Instant expiresAt = null;

                if (shouldRefreshContractLink(link)) {
                    ContractUserLink renewed = createAndStoreContractLink(session.documento);
                    link = renewed.url();
                    expiresAt = renewed.expiresAt();
                } else if (!link.isBlank()) {
                    VerificationService.ContractAccessResult peek = peekContractLinkAccess(link);
                    expiresAt = peek == null ? null : peek.expiresAt();
                }

                if (link == null || link.isBlank()) {
                    actions.add(textMsg("⚠️ Aún no hay un enlace de contrato disponible."));
                } else {
                    actions.add(textMsg("📄 *Enlace de contrato*\n\n" + link + "\n\n" + buildContractExpiryHint(expiresAt)));
                }

                actions.add(textMsg("Opciones: MENU"));

            } catch (Exception e) {
                actions.add(textMsg("⚠️ No pude obtener el enlace del contrato."));
                actions.add(textMsg("Opciones: MENU"));
            }
        }

        default -> {
            actions.add(textMsg(
                    "📄 Debes completar la firma del contrato.\n\n" +
                    "Cuando termines escribe *LISTO*."
            ));
            actions.add(textMsg("Opciones: MENU"));
        }
    }
}

    private String resolveSedeSelection(String text) {
        String t = normalizeInput(text);
        if ("1".equals(t) || "kennedy".equals(t)) {
            return "Kennedy";
        }
        if ("2".equals(t) || "eden".equals(t) || "el eden".equals(t) || "cc el eden".equals(t) || "cc eleden".equals(t)) {
            return "CC El Eden";
        }
        return "";
    }

        private void handleSedeSelection(String from, String text, SessionData session, List<BotAction> actions) {

        String sede = resolveSedeSelection(text);

        if (sede.isBlank()) {

            actions.add(textMsg(
                    "⚠️ Debes seleccionar una sede válida.\n\n" +
                    "1️⃣ Kennedy\n" +
                    "2️⃣ CC El Edén"
            ));

            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        session.sedeSeleccionada = sede;

        try {

            Long studentId =
                    procesoService.createStudentFromSignedContract(session.documento, sede);

            session.state = ChatState.DONE;

            actions.add(textMsg(
                    "🎉 *Matrícula finalizada correctamente*\n\n" +
                    "📍 Sede asignada: " + sede + "\n" +
                    "🆔 Referencia estudiante: " + studentId + "\n\n" +
                    "Gracias por completar tu proceso con *CEA HARO*."
            ));

            sessions.remove(from);

        } catch (Exception e) {

            actions.add(textMsg(
                    "⚠️ No pude crear el estudiante.\n\n" +
                    "Detalle: " + e.getMessage()
            ));

            actions.add(textMsg("Opciones: MENU"));
        }
    }

    private void handleStudentMenu(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Para continuar como estudiante, primero valida tu identidad.\n\n" +
                            "Escribe tu número de documento (solo números) y te enviaremos un OTP."
            ));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        if (!session.studentOtpVerified) {
            session.state = ChatState.STUDENT_OTP_VERIFY;
            actions.add(textMsg(
                    "🔐 Aun falta validar tu OTP para ingresar al menu de estudiante.\n\n" +
                            "Escribe el codigo OTP enviado al correo " + maskEmail(session.studentEmail) + "."
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        switch (text) {
            case "1", "calendario" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "2", "reservar" -> {
                session.pendingStudentAction = StudentAction.NONE;
                session.studentBookingDate = null;
                session.studentBookingTipoPase = null;

                String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
                String rawSede = trim(session.studentSede);
                String sede = firstNotBlank(rawSede, "No asignada");
                String tipoPase = trim(session.studentTipoPase).toLowerCase(Locale.ROOT);
                boolean dual = tipoPase.contains("carro") && tipoPase.contains("moto");

                if (rawSede.isBlank()) {
                    actions.add(textMsg(
                            "⚠️ No encuentro una sede asignada para tu matrícula, por lo que no puedo agendar prácticas.\n\n" +
                                    "Comunícate con la academia para registrar tu sede y vuelve a intentarlo.\n\n" +
                                    "📌 Datos registrados:\n" +
                                    "🪪 Categoría: " + categoria + "\n" +
                                    "🏫 Sede asignada: " + sede
                    ));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                actions.add(textMsg(practicalProcessText()));
                if (dual) {
                    session.state = ChatState.STUDENT_BOOKING_TYPE;
                    actions.add(textMsg(
                            "🚘 Vamos a reservar tu práctica.\n\n" +
                                    "📌 Datos registrados:\n" +
                                    "🪪 Categoría: " + categoria + "\n" +
                                    "🏫 Sede asignada: " + sede + "\n\n" +
                                    "✅ Como tienes prácticas para *carro* y *moto*, elige qué deseas agendar:\n" +
                                    "1️⃣ Carro\n" +
                                    "2️⃣ Moto\n\n" +
                                    "✍️ Responde 1 o 2."
                    ));
                } else {
                    session.state = ChatState.STUDENT_BOOKING_SLOT;
                    if ("carro".equals(tipoPase) || "moto".equals(tipoPase)) {
                        session.studentBookingTipoPase = tipoPase;
                    }
                    actions.add(textMsg(
                            "🚘 Vamos a reservar tu práctica.\n\n" +
                                    "📌 Datos registrados:\n" +
                                    "🪪 Categoría: " + categoria + "\n" +
                                    "🏫 Sede asignada: " + sede + "\n\n" +
                                    "Pasos a seguir:\n" +
                                    "1️⃣ Escribe la fecha de la clase.\n" +
                                    "2️⃣ Puedes escribir: hoy, mañana o YYYY-MM-DD.\n" +
                                    "3️⃣ Después te mostraré opciones de hora para elegir.\n\n" +
                                    "Ejemplos de fecha: hoy | mañana | 2026-03-15"
                    ));
                }
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            }
            case "3", "horario" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "4", "cancelar clase", "cancelar" -> {
                List<Clase> agenda = procesoService.listUpcomingClassesForCancellationByStudentId(session.studentId);
                if (agenda.isEmpty()) {
                    actions.add(textMsg(
                            "ℹ️ No tienes clases futuras para cancelar.\n\n" +
                                     "Si agendas una nueva clase, podrás verla aquí."
                    ));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                session.pendingStudentAction = StudentAction.NONE;
                session.state = ChatState.STUDENT_CANCEL_CLASS_PICK;
                actions.add(textMsg(
                        "🛑 Estas son tus clases futuras:\n\n" +
                                formatCancelableClasses(agenda) + "\n\n" +
                                 "Escribe el ID de la clase que deseas cancelar.\n" +
                                 "Regla: con menos de 48 horas se aplica multa."
                ));
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            }
            case "5", "menu principal", "principal", "inicio" -> {
                resetToMain(session); // no cierra sesion de estudiante
                actions.add(textMsg(mainMenuText()));
                actions.add(textMsg("ℹ️ Tu sesion de estudiante sigue activa. Para volver, escribe: SOY ESTUDIANTE."));
            }
            case "6" -> {
                session.pendingStudentLogoutReturnState = ChatState.STUDENT_MENU;
                session.state = ChatState.STUDENT_LOGOUT_CONFIRM;
                actions.add(textMsg(studentLogoutConfirmText(session.studentNombre)));
                actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
            }
            case "volver", "atras", "atrás" -> {
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            default -> {
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
        }
    }

    private void handleStudentDocCapture(String text, SessionData session, List<BotAction> actions) {
        String doc = trim(text).replaceAll("\\D+", "");
        if (!DOCUMENT_PATTERN.matcher(doc).matches()) {
            actions.add(textMsg(
                    "⚠️ Documento inválido.\n\n" +
                            "Pasos para corregirlo:\n" +
                            "1️⃣ Escribe solo números.\n" +
                            "2️⃣ Usa entre 5 y 20 dígitos.\n" +
                            "3️⃣ Envíalo de nuevo en un solo mensaje.\n\n" +
                            "Ejemplo: 12345678"
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        try {
            ChatbotProcesoService.StudentAccessData access = procesoService.requireStudentAccessData(doc);
            boolean cooldown = false;
            Integer cooldownSeconds = null;
            try {
                // Importante: en Cloud Run el CPU puede no estar disponible post-response;
                // por eso el OTP debe enviarse dentro del request para garantizar entrega.
                verificationService.sendEmailVerification(access.email());
            } catch (Exception e) {
                String msg = trim(e.getMessage());
                if (msg.toLowerCase(Locale.ROOT).startsWith("espera") && msg.toLowerCase(Locale.ROOT).contains("reenviar")) {
                    cooldown = true;
                    Matcher m = OTP_COOLDOWN_SECONDS_PATTERN.matcher(msg);
                    if (m.find()) {
                        try {
                            cooldownSeconds = Integer.parseInt(m.group(1));
                        } catch (NumberFormatException ignored) {
                            // ignore parsing
                        }
                    }
                    log.info("OTP resend cooldown email={} msg={}", maskEmail(access.email()), msg);
                } else {
                    log.error("OTP send failed email={}", maskEmail(access.email()), e);
                    actions.add(textMsg(
                            "⚠️ No pude enviar el código OTP al correo " + access.emailMasked() + ".\n\n" +
                                    "Intenta nuevamente en unos minutos o contacta un asesor si el problema persiste.\n\n" +
                                    "Detalle: " + firstNotBlank(msg, e.getClass().getSimpleName())
                    ));
                    actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
                    return;
                }
            }

            session.studentId = access.studentId();
            session.studentDocumento = access.documento();
            session.studentEmail = access.email();
            session.studentNombre = access.nombreCompleto();
            session.studentCategoria = access.categoria();
            session.studentTipoPase = access.tipoPase();
            session.studentSede = access.sede();
            session.studentBookingTipoPase = null;
            session.studentOtpVerified = false;
            session.state = ChatState.STUDENT_OTP_VERIFY;

            String otpIntro = cooldown
                    ? ("🔐 Ya existe un código OTP reciente enviado al correo " + access.emailMasked() + ".\n\n" +
                       (cooldownSeconds != null
                               ? ("Espera " + cooldownSeconds + "s para solicitar otro código si lo necesitas.\n\n")
                               : "Espera un momento antes de solicitar otro código.\n\n"))
                    : ("🔐 Listo. Te enviamos un código OTP al correo " + access.emailMasked() + ".\n\n");

            actions.add(textMsg(
                    otpIntro +
                            "Pasos a seguir:\n" +
                            "1️⃣ Abre tu correo (revisa también Spam o No deseado).\n" +
                            "2️⃣ Busca el mensaje con tu código OTP.\n" +
                            "3️⃣ Copia solo los números del código.\n" +
                            "4️⃣ Escríbelo aquí en el chat.\n\n" +
                             "⏱️ Si no llega de inmediato, espera hasta 1 minuto."
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude iniciar verificación OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
        }
    }

    private void handleStudentOtpVerify(String text, SessionData session, List<BotAction> actions) {
        String code = trim(text);
        if (!OTP_PATTERN.matcher(code).matches()) {
            actions.add(textMsg(
                    "⚠️ Ese código no es válido.\n\n" +
                            "Pasos para enviarlo bien:\n" +
                            "1️⃣ Envía solo números.\n" +
                            "2️⃣ No uses letras ni espacios.\n" +
                            "3️⃣ No agregues puntos ni símbolos.\n\n" +
                            "Ejemplo: 123456"
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        try {
            boolean ok = verificationService.verifyEmailOtp(session.studentEmail, code);
            if (!ok) {
                actions.add(textMsg(
                        "❌ El código es incorrecto o ya venció.\n\n" +
                                "Pasos para pedir uno nuevo:\n" +
                                "1️⃣ Escribe tu documento nuevamente\n" +
                                "2️⃣ Te enviaremos un nuevo OTP (si aplica cooldown, espera unos segundos)\n" +
                                "3️⃣ Escribe el nuevo OTP aquí"
                ));
                actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
                return;
            }

            actions.add(textMsg(studentGreetingText(session.studentNombre)));
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            session.studentOtpVerified = true;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg(studentNavigationOptionsText()));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude validar OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
        }
    }

    private void handleStudentBookingType(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Tu sesion de estudiante expiró o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        String trimmed = trim(text);
        if (isStudentBackCommand(trimmed)) {
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            session.studentBookingDate = null;
            session.studentBookingTipoPase = null;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        String cmd = normalizeCommandText(trimmed);
        String choice = "";
        if ("1".equals(cmd) || cmd.contains("carro") || cmd.contains("auto")) {
            choice = "carro";
        } else if ("2".equals(cmd) || cmd.contains("moto") || cmd.contains("motoc")) {
            choice = "moto";
        }

        if (choice.isBlank()) {
            String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
            String sede = firstNotBlank(trim(session.studentSede), "No asignada");
            actions.add(textMsg(
                    "⚠️ No entendí tu elección.\n\n" +
                            "📌 Datos registrados:\n" +
                            "🪪 Categoría: " + categoria + "\n" +
                            "🏫 Sede asignada: " + sede + "\n\n" +
                            "Elige qué deseas agendar:\n" +
                            "1️⃣ Carro\n" +
                            "2️⃣ Moto\n\n" +
                            "✍️ Responde 1 o 2."
            ));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            return;
        }

        session.studentBookingTipoPase = choice;
        session.studentBookingDate = null;
        session.state = ChatState.STUDENT_BOOKING_SLOT;

        String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
        String sede = firstNotBlank(trim(session.studentSede), "No asignada");
        String choiceLabel = "carro".equals(choice) ? "CARRO" : "MOTO";
        actions.add(textMsg(
                "✅ Listo. Agendaremos práctica de *" + choiceLabel + "*.\n\n" +
                        "📌 Datos registrados:\n" +
                        "🪪 Categoría: " + categoria + "\n" +
                        "🏫 Sede asignada: " + sede + "\n\n" +
                        "Ahora escribe la fecha de la clase.\n" +
                        "Puedes escribir: hoy, mañana o YYYY-MM-DD.\n\n" +
                        "Ejemplos: hoy | mañana | 2026-03-15"
        ));
        actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
    }

    private void handleStudentBookingSlot(String rawText, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Tu sesion de estudiante expiró o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        String trimmed = trim(rawText);
        if (isStudentBackCommand(trimmed)) {
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            session.studentBookingDate = null;
            session.studentBookingTipoPase = null;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        if (session.studentBookingDate == null) {
            // Compatibilidad: si el usuario envía fecha y hora juntas, también se procesa.
            BookingSlot fullSlot = parseBookingSlot(trimmed);
            if (fullSlot != null) {
                try {
                    LocalDate fecha = LocalDate.parse(fullSlot.fecha());
                    LocalTime hora = LocalTime.parse(fullSlot.hora());
                    completeStudentBooking(fecha, hora, session, actions);
                    return;
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No pude agendar la clase: " + e.getMessage()));
                    actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
                    return;
                }
            }

            LocalDate fecha = parseBookingDate(trimmed);
            if (fecha == null) {
                actions.add(textMsg(
                        "⚠️ No entendí la fecha.\n\n" +
                                "Pasos para enviarla bien:\n" +
                                "1️⃣ Escribe hoy o mañana.\n" +
                                "2️⃣ O usa formato YYYY-MM-DD.\n" +
                                "3️⃣ Envíala en un solo mensaje.\n\n" +
                                "Ejemplos: hoy | mañana | 2026-03-15"
                ));
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
                return;
            }

            if (fecha.isBefore(LocalDate.now())) {
                actions.add(textMsg(
                        "⚠️ La fecha no puede ser pasada.\n\n" +
                                "Escribe hoy, mañana o una fecha futura."
                ));
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
                return;
            }

            session.studentBookingDate = fecha.toString();
            actions.add(textMsg(
                    "📅 Fecha registrada: " + fecha + "\n\n" +
                            bookingSlotsAvailabilityText(session, fecha)
            ));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            return;
        }

        LocalDate fecha = LocalDate.parse(session.studentBookingDate);
        LocalTime hora = parseBookingTime(trimmed);
        if (hora == null) {
            actions.add(textMsg(
                    "⚠️ No entendí la hora.\n\n" +
                            "Pasos para enviarla bien:\n" +
                            "1️⃣ Escribe un número del 1 al 6.\n" +
                            "2️⃣ O escribe la hora en formato HH:mm.\n\n" +
                            "Ejemplos: 2 | 08:30"
            ));
            actions.add(textMsg(bookingSlotsAvailabilityText(session, fecha)));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            return;
        }

        try {
            completeStudentBooking(fecha, hora, session, actions);
        } catch (Exception e) {
            actions.add(textMsg(
                    "⚠️ No pude agendar la clase: " + e.getMessage() + "\n\n" +
                            bookingSlotsAvailabilityText(session, fecha)
            ));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
        }
    }

    private void handleStudentCancelClassPick(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Tu sesion de estudiante expiró o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: MENU | CERRAR SESION"));
            return;
        }

        if (isStudentBackCommand(text)) {
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        String input = trim(text);
        if (!input.matches("^\\d+$")) {
            List<Clase> agenda = procesoService.listUpcomingClassesForCancellationByStudentId(session.studentId);
            if (agenda.isEmpty()) {
                session.state = ChatState.STUDENT_MENU;
                session.pendingStudentAction = StudentAction.NONE;
                actions.add(textMsg("ℹ️ Ya no hay clases futuras para cancelar."));
                actions.add(textMsg(studentNavigationOptionsText()));
                return;
            }

            actions.add(textMsg(
                    "⚠️ Debes escribir el ID de la clase a cancelar.\n\n" +
                            "Clases futuras:\n\n" +
                            formatCancelableClasses(agenda) + "\n\n" +
                            "Ejemplo: 123"
            ));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            return;
        }

        try {
            Long idClase = Long.parseLong(input);
            ChatbotProcesoService.CancellationResult result =
                    procesoService.cancelPracticalClassByStudentId(session.studentId, idClase);

            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;

            StringBuilder out = new StringBuilder();
            out.append("✅ Clase cancelada correctamente.\n\n")
                    .append("ID: ").append(result.clase().getId()).append("\n")
                    .append("Fecha: ").append(result.clase().getFecha()).append("\n")
                    .append("Inicio: ").append(result.clase().getHoraInicio());

            if (result.multaAplicada()) {
                out.append("\n\n⚠️ Se aplicó multa por cancelar con menos de 48 horas.\n")
                        .append("Valor multa: $").append(formatMoney(result.valorMulta())).append("\n")
                        .append("Tiempo restante al cancelar: ").append(result.horasRestantes()).append(" horas\n")
                        .append("Multas acumuladas: $").append(formatMoney(result.multasAcumuladas()));
            } else {
                out.append("\n\n✅ Cancelaste con 48 horas o más. No se aplicó multa.");
            }

            actions.add(textMsg(out.toString()));
            actions.add(textMsg(studentNavigationOptionsText()));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude cancelar la clase: " + e.getMessage()));
            actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
        }
    }

    private void handleStudentLogoutConfirm(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if ("si".equals(cmd) || "1".equals(cmd) || "confirmar".equals(cmd)) {
            ChatState returnState = session.pendingStudentLogoutReturnState;
            session.pendingStudentLogoutReturnState = null;

            clearStudentAccessData(session);

            // Si el usuario estaba en el subflujo de estudiante, lo llevamos al menu principal.
            if (isStudentState(returnState)) {
                resetToMain(session);
                actions.add(textMsg("✅ Sesion de estudiante cerrada."));
                actions.add(textMsg(mainMenuText()));
                return;
            }

            // Si estaba en otro flujo, solo cerramos sesion y continuamos donde iba.
            if (returnState != null) {
                session.state = returnState;
            } else {
                resetToMain(session);
            }
            actions.add(textMsg("✅ Sesion de estudiante cerrada."));
            if (session.state == ChatState.MAIN_MENU) {
                actions.add(textMsg(mainMenuText()));
            } else {
                actions.add(textMsg("ℹ️ Continuemos con el paso actual."));
            }
            return;
        }

        if ("no".equals(cmd) || "2".equals(cmd) || isStudentBackCommand(cmd)) {
            ChatState returnState = session.pendingStudentLogoutReturnState;
            session.pendingStudentLogoutReturnState = null;

            if (returnState != null) {
                session.state = returnState;
            }

            // Re-render minimo (no cambia logica)
            if (session.state == ChatState.STUDENT_MENU && session.studentOtpVerified) {
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg(studentNavigationOptionsText()));
            } else if (session.state == ChatState.STUDENT_BOOKING_SLOT && session.studentOtpVerified) {
                actions.add(textMsg("✅ Listo. Sigamos con tu reserva.\n\nEscribe la fecha: hoy | mañana | YYYY-MM-DD"));
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            } else if (session.state == ChatState.STUDENT_CANCEL_CLASS_PICK && session.studentOtpVerified) {
                actions.add(textMsg("✅ Listo. Sigamos con la cancelacion.\n\nEscribe el ID de la clase que deseas cancelar."));
                actions.add(textMsg("Opciones: VOLVER | MENU | CERRAR SESION"));
            } else {
                actions.add(textMsg("✅ Perfecto. Continuemos."));
            }
            return;
        }

        actions.add(textMsg(studentLogoutConfirmText(session.studentNombre)));
        actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
    }

    private void handleEnrollmentAbortConfirm(String from, String text, SessionData session, List<BotAction> actions) {
        EnrollmentAbortAction action = session.pendingEnrollmentAbortAction;
        ChatState returnState = session.pendingEnrollmentAbortReturnState;

        String cmd = normalizeCommandText(text);
        if ("si".equals(cmd) || "1".equals(cmd) || "confirmar".equals(cmd)) {
            session.pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;
            session.pendingEnrollmentAbortReturnState = null;

            if (action == EnrollmentAbortAction.END_CONVERSATION) {
                sessions.remove(from);
                actions.add(textMsg(conversationEndedText()));
                return;
            }

            resetToMain(session);
            actions.add(textMsg(
                    "✅ Proceso de matricula terminado.\n\n" +
                            "Si deseas iniciar de nuevo, escribe MATRICULA o MENU.\n\n" +
                            mainMenuText()
            ));
            return;
        }

        if ("no".equals(cmd) || "2".equals(cmd) || isStudentBackCommand(cmd)) {
            session.pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;
            session.pendingEnrollmentAbortReturnState = null;

            ChatState resume = returnState != null ? returnState : ChatState.MAIN_MENU;
            session.state = resume;
            actions.add(textMsg("✅ Perfecto. Continuemos con tu matricula."));
            renderEnrollmentResumePrompt(resume, session, actions);
            return;
        }

        actions.add(textMsg(enrollmentAbortConfirmText(action == EnrollmentAbortAction.END_CONVERSATION)));
        actions.add(textMsg("Opciones: SI | NO | VOLVER | MENU"));
    }

    private void renderEnrollmentResumePrompt(ChatState state, SessionData session, List<BotAction> actions) {
        if (state == null) {
            actions.add(textMsg(mainMenuText()));
            return;
        }

        switch (state) {
            case ENROLLMENT_DATA_AUTH_WAIT -> {
                addEnrollmentIntro(actions);
                actions.add(textMsg("Opciones: SI | NO | MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_CAPTURE -> {
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("Despues de ese primer mensaje te pedire tu edad, correo, telefono, direccion y la sede."));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_AGE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 2 de 8: envía tu edad en años.\n\n" +
                                "Ejemplo: 18\n\n" +
                                "Importante: debes tener mínimo 16 años para matricularte."
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_EMAIL_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 3 de 8: envia tu correo electronico.\n" +
                                "Ejemplo: usuario@correo.com"
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_PHONE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 4 de 8: envia tu telefono de contacto.\n" +
                                "Ejemplo: 573001112233"
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_ADDRESS_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 5 de 8: envia tu direccion de residencia.\n\n" +
                                "Escribela completa con barrio, nomenclatura o apartamento si aplica.\n\n" +
                                "Ejemplo: Cra 80 #12-45 Apto 302, Kennedy, Bogota"
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_SEDE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 6 de 8: selecciona tu sede.\n\n" +
                                "1) Kennedy - Av. 1 de Mayo #68D-23 Piso 2\n" +
                                "2) CC El Eden - Local L2-094A\n\n" +
                                "Responde con 1 o 2."
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case ENROLLMENT_CONFIRM -> {
                actions.add(textMsg(
                        "Paso 7 de 8: revisa tus datos y confirma.\n\n" +
                                "Nombre: " + safe(session.nombre) + "\n" +
                                "Documento: " + safe(session.documento) + "\n" +
                                "Categoria: " + safe(session.categoria) + "\n" +
                                "Edad: " + (session.edad == null ? "N/A" : (session.edad + " años")) + "\n" +
                                "Correo: " + safe(session.email) + "\n" +
                                "Telefono: " + safe(session.telefono) + "\n" +
                                "Direccion: " + safe(session.direccion) + "\n" +
                                "Sede: " + safe(session.sedeSeleccionada) + "\n\n" +
                                "Responde:\n" +
                                "1) Confirmar y continuar\n" +
                                "2) Corregir datos\n" +
                                "3) Cancelar"
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            case PAYMENT_METHOD_SELECT -> {
                actions.add(textMsg(
                        "Paso 8 de 8: selecciona tu metodo de pago.\n\n" +
                                "1) Pagar por ePayco (en linea)\n" +
                                "2) Pagar en efectivo en la academia\n\n" +
                                "Responde 1 o 2."
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
            }
            case PAYMENT_PLAN_SELECT -> {
                actions.add(textMsg(
                        "Elige como deseas pagar por ePayco:\n\n" +
                                "1) Pagar completo (100%)\n" +
                                "2) Pagar por la mitad (50%)\n\n" +
                                "Responde 1 o 2."
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
            }
            case PAYMENT_WAIT -> {
                actions.add(textMsg(
                        "💳 Estamos esperando la confirmacion de tu pago.\n\n" +
                                "Puedes escribir:\n" +
                                "🔹 LINK para ver el enlace de pago\n" +
                                "🔹 YA PAGUE si ya realizaste el pago"
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
            }
            case PAYMENT_CASH_WAIT -> {
                actions.add(textMsg(
                        "⏳ Estamos esperando la confirmacion del pago en efectivo.\n\n" +
                                "Cuando se confirme, te enviaremos por este chat el enlace para firmar los contratos."
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
            }
            case CONTRACT_WAIT -> {
                actions.add(textMsg(
                        "📝 Estamos esperando que firmes tus contratos.\n\n" +
                                "Cuando termines, responde *LISTO* para continuar."
                ));
                actions.add(textMsg("Opciones: MENU | TERMINAR"));
            }
            case SEDE_SELECTION -> {
                actions.add(textMsg(
                        "📍 Selecciona tu sede:\n\n" +
                                "1️⃣ Kennedy\n" +
                                "2️⃣ CC El Edén"
                ));
                actions.add(textMsg("Opciones: MENU | CANCELAR | TERMINAR"));
            }
            default -> actions.add(textMsg("ℹ️ Continuemos. Responde con la informacion solicitada en este paso."));
        }
    }

    // =========================
    // LINKS / PARSING / FORMAT
    // =========================

    private static final ZoneId CONTRACT_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter CONTRACT_EXPIRES_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CONTRACT_ZONE);

    private record ContractUserLink(String url, Instant expiresAt) {}
    private record ContractAccessParams(String email, String code) {}

    private ContractUserLink createAndStoreContractLink(String documento) {
        ChatbotMatriculaProceso proceso = procesoService.findProcesoByDocumento(documento)
                .orElseThrow(() -> new IllegalStateException("No existe proceso de matrícula para generar contrato"));

        VerificationService.ContractLinkResult out = verificationService.createContractVerificationLink(
                proceso.getEmail(),
                contractBaseUrl
        );
        String userLink = buildContractUserLink(out);
        procesoService.markContractLinkSent(documento, userLink);
        return new ContractUserLink(userLink, out == null ? null : out.expiresAt());
    }

    private VerificationService.ContractAccessResult peekContractLinkAccess(String contractLinkRaw) {
        ContractAccessParams params = parseContractAccessParams(contractLinkRaw);
        if (params.email().isBlank() || params.code().isBlank()) {
            return new VerificationService.ContractAccessResult(false, "Codigo requerido", null);
        }
        return verificationService.peekContractAccessCode(params.email(), params.code());
    }

    private ContractAccessParams parseContractAccessParams(String contractLinkRaw) {
        String link = trim(contractLinkRaw);
        if (link.isBlank()) {
            return new ContractAccessParams("", "");
        }
        return new ContractAccessParams(
                readQueryParam(link, "email"),
                readQueryParam(link, "code")
        );
    }

    private String readQueryParam(String urlRaw, String keyRaw) {
        String url = trim(urlRaw);
        String key = trim(keyRaw);
        if (url.isBlank() || key.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return "";
            }
            for (String part : rawQuery.split("&")) {
                if (part == null || part.isBlank()) continue;
                int idx = part.indexOf('=');
                String k = idx >= 0 ? part.substring(0, idx) : part;
                String v = idx >= 0 ? part.substring(idx + 1) : "";
                String dk = URLDecoder.decode(k, StandardCharsets.UTF_8);
                if (!dk.equalsIgnoreCase(key)) continue;
                return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String buildContractExpiryHint(Instant expiresAt) {
        if (expiresAt == null) {
            return "⏳ Este enlace es temporal. Si se vence, escribe *LINK* para generar otro.";
        }
        String until = CONTRACT_EXPIRES_FMT.format(expiresAt);
        return "⏳ Vigente hasta: " + until + " (hora Colombia). Si se vence, escribe *LINK* para generar otro.";
    }

    private String buildContractUserLink(VerificationService.ContractLinkResult out) {
        if (out == null) return "";
        String ui = resolveContractUiUrl(out);
        if (ui.isBlank()) return trim(out.url());
        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        if (looksLikeBackendBaseUrl(contractBaseUrl)) link = appendQueryParam(link, "apiBase", contractBaseUrl);
        return link;
    }

    private String resolveContractUiUrl(VerificationService.ContractLinkResult out) {
        String ui = normalizeContractUiUrl(contractUiUrl);
        if (!ui.isBlank()) return ui;

        String base = trim(contractBaseUrl);
        if (!base.isBlank() && !looksLikeBackendBaseUrl(base)) {
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/Contratos/contrato.html";
        }

        String verificationUrl = out == null ? "" : trim(out.url());
        if (verificationUrl.isBlank()) return "";
        try {
            java.net.URL u = new java.net.URL(verificationUrl);
            return u.getProtocol() + "://" + u.getAuthority() + "/Contratos/contrato.html";
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean looksLikeBackendBaseUrl(String raw) {
        String v = trim(raw).toLowerCase(java.util.Locale.ROOT);
        if (v.isBlank()) return false;
        return v.contains("run.app")
                || v.contains("localhost")
                || v.matches(".*:\\d{2,5}$");
    }

    private boolean shouldRefreshContractLink(String contractLinkRaw) {
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (contractLink.isBlank()) {
            return true;
        }

        // Regenerar si el codigo del link ya no es vigente (expirado/consumido/no encontrado).
        try {
            VerificationService.ContractAccessResult peek = peekContractLinkAccess(contractLink);
            if (peek == null || !peek.ok()) {
                return true;
            }
        } catch (Exception ex) {
            // Si falla el "peek", no forzamos refresh para evitar loops.
        }

        String expectedUi = normalizeContractUiUrl(contractUiUrl);
        if (expectedUi.isBlank()) {
            return false;
        }
        return !contractLink.startsWith(expectedUi);
    }

    private String normalizeContractUiUrl(String rawUiUrl) {
        String ui = trim(rawUiUrl);
        if (ui.isBlank()) {
            return ui;
        }
        return ui.replaceFirst("(?i)(?:/Contratos)*/contrato\\.html(?=($|[?#]))", "/Contratos/contrato.html");
    }

    private String normalizeStoredContractLink(String rawContractLink) {
        return normalizeContractUiUrl(trim(rawContractLink));
    }

    private String appendQueryParam(String baseUrl, String key, String value) {
        String base = trim(baseUrl);
        if (base.isBlank() || trim(key).isBlank() || trim(value).isBlank()) {
            return base;
        }
        String sep = base.contains("?") ? "&" : "?";
        return base + sep
                + URLEncoder.encode(trim(key), StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(trim(value), StandardCharsets.UTF_8);
    }

    private EnrollmentBasic parseEnrollmentBasic(String rawText) {
        Matcher matcher = ENROLLMENT_BASIC_PATTERN.matcher(trim(rawText));
        if (!matcher.matches()) return null;

        String nombre = collapseSpaces(matcher.group(1));
        String documento = matcher.group(2);
        String categoria = normalizeCategory(matcher.group(3));

        if (nombre.length() < 3) return null;
        return new EnrollmentBasic(nombre, documento, categoria);
    }

    private BookingSlot parseBookingSlot(String rawText) {
        Matcher matcher = BOOKING_SLOT_PATTERN.matcher(trim(rawText));
        if (!matcher.matches()) return null;

        String fecha = matcher.group(1);
        String hora = matcher.group(2);

        try {
            LocalDate.parse(fecha);
            LocalTime.parse(hora);
        } catch (DateTimeParseException e) {
            return null;
        }
        return new BookingSlot(fecha, hora);
    }

    private LocalDate parseBookingDate(String rawText) {
        String n = normalizeInput(rawText);
        if ("hoy".equals(n)) return LocalDate.now();
        if ("manana".equals(n)) return LocalDate.now().plusDays(1);

        String value = trim(rawText);
        if (value.isBlank()) return null;

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    private LocalTime parseBookingTime(String rawText) {
        String n = normalizeInput(rawText);
        return switch (n) {
            case "1" -> LocalTime.of(6, 0);
            case "2" -> LocalTime.of(8, 0);
            case "3" -> LocalTime.of(10, 0);
            case "4" -> LocalTime.of(14, 0);
            case "5" -> LocalTime.of(16, 0);
            case "6" -> LocalTime.of(18, 0);
            default -> parseHourValue(rawText);
        };
    }

    private LocalTime parseHourValue(String rawText) {
        String value = trim(rawText);
        if (value.isBlank()) return null;
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String bookingSlotsAvailabilityText(SessionData session, LocalDate fecha) {
        try {
            Long studentId = session == null ? null : session.studentId;
            String tipoPase = session == null ? null : session.studentBookingTipoPase;
            if (studentId == null) {
                return "⚠️ No pude identificar el estudiante para consultar disponibilidad. Escribe MENU e ingresa de nuevo como estudiante.";
            }
            List<ChatbotProcesoService.SlotAvailability> slots =
                    procesoService.listPracticalSlotAvailabilityByStudentId(studentId, fecha, tipoPase);
            if (slots.isEmpty()) {
                return "No hay horarios disponibles para esa fecha.\nEscribe otra fecha para consultar.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Disponibilidad del día:\n");
            boolean anyAvailable = false;
            for (ChatbotProcesoService.SlotAvailability slot : slots) {
                boolean available = slot.disponible();
                if (available) anyAvailable = true;
                sb.append(slot.opcion())
                        .append(") ")
                        .append(slot.hora())
                        .append(" ")
                        .append(available ? "✅ Disponible" : "❌ Ocupada")
                        .append("\n");
            }

            if (anyAvailable) {
                sb.append("\nEscribe el número de una hora disponible o envía una hora en formato HH:mm.");
            } else {
                sb.append("\n⚠️ Todas las horas están ocupadas. Escribe otra fecha.");
            }
            return sb.toString();
        } catch (Exception e) {
            return "⚠️ No pude consultar disponibilidad en este momento: " + e.getMessage();
        }
    }

    private String formatCancelableClasses(List<Clase> clases) {
        if (clases == null || clases.isEmpty()) {
            return "No hay clases futuras para cancelar.";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(clases.size(), 12);
        for (int i = 0; i < limit; i++) {
            Clase c = clases.get(i);
            sb.append("ID ")
                    .append(c.getId())
                    .append(" - ")
                    .append(c.getFecha())
                    .append(" ")
                    .append(c.getHoraInicio())
                    .append(" | Estado: ")
                    .append(safe(c.getEstado()))
                    .append("\n");
        }
        if (clases.size() > limit) {
            sb.append("... ").append(clases.size() - limit).append(" clases adicionales");
        }
        return sb.toString();
    }

    private String formatMoney(java.math.BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private void completeStudentBooking(LocalDate fecha,
                                        LocalTime hora,
                                        SessionData session,
                                        List<BotAction> actions) {
        ChatbotProcesoService.BookingResult booking =
                procesoService.bookPracticalClassByStudentId(session.studentId, fecha, hora, session.studentBookingTipoPase);
        Clase clase = booking.clase();
        String calendarLink = booking.reunionCalendario() == null ? "" : trim(booking.reunionCalendario().htmlLink());
        String meetLink = booking.reunionCalendario() == null ? "" : trim(booking.reunionCalendario().meetLink());
        String calendarStatus = booking.reunionCalendario() == null ? "" : trim(booking.reunionCalendario().estado());
        boolean manualCalendar = "PENDIENTE_MANUAL".equalsIgnoreCase(calendarStatus);

        StringBuilder out = new StringBuilder();
        if (manualCalendar) {
            out.append("Clase practica agendada. No fue posible crear automaticamente la cita en Calendar.\n\n");
        } else {
            out.append("Clase practica agendada y cita creada en Calendar.\n\n");
        }
        out.append("ID: ").append(clase.getId()).append("\n")
                .append("Fecha: ").append(clase.getFecha()).append("\n")
                .append("Inicio: ").append(clase.getHoraInicio()).append("\n")
                .append("Fin: ").append(clase.getHoraFin());

        String sede = firstNotBlank(trim(session.studentSede), "No asignada");
        String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
        out.append("\nSede: ").append(sede)
                .append("\nCategoria: ").append(categoria);
        String tipo = trim(session.studentBookingTipoPase).toLowerCase(Locale.ROOT);
        if ("carro".equals(tipo) || "moto".equals(tipo)) {
            out.append("\nTipo practica: ").append("carro".equals(tipo) ? "CARRO" : "MOTO");
        }
        if (!calendarLink.isBlank()) {
            out.append(manualCalendar ? "\nAgregar al calendario: " : "\nEvento: ").append(calendarLink);
        }
        if (!meetLink.isBlank()) {
            out.append("\nMeet: ").append(meetLink);
        }

        session.state = ChatState.STUDENT_MENU;
        session.pendingStudentAction = StudentAction.NONE;
        session.studentBookingDate = null;
        session.studentBookingTipoPase = null;

        actions.add(textMsg(out.toString()));

        String verifyHint = "✅ Para verificar que tu clase quedó agendada:\n"
                + "1️⃣ Responde 1️⃣ para consultar tu calendario de prácticas.\n"
                + "3️⃣ Responde 3️⃣ para consultar tu horario.\n\n"
                + (manualCalendar
                ? "ℹ️ Nota: la cita en Google Calendar quedó pendiente. Si necesitas ayuda, escribe ASESOR."
                : "📩 Revisa tu correo: te llegará la invitación de Google Calendar.");

        actions.add(textMsg(verifyHint));
        actions.add(textMsg(studentMenuText()));
        actions.add(textMsg(studentNavigationOptionsText()));
    }

    private String formatAgenda(String documento, List<Clase> agenda) {
        if (agenda == null || agenda.isEmpty()) {
            return "📅 No encontramos clases programadas para el documento " + documento + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 Agenda de clases para documento ").append(documento).append(":\n\n");

        int limit = Math.min(agenda.size(), 12);
        for (int i = 0; i < limit; i++) {
            Clase c = agenda.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(c.getFecha())
                    .append(" ")
                    .append(c.getHoraInicio())
                    .append(" - ")
                    .append(c.getHoraFin())
                    .append(" | Estado: ")
                    .append(safe(c.getEstado()))
                    .append("\n");
        }

        if (agenda.size() > limit) {
            sb.append("\n...").append(agenda.size() - limit).append(" clases adicionales");
        }

        return sb.toString();
    }

    // =========================
    // SESSION MGMT
    // =========================

    private void evictStaleSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(Math.max(1, inactivityTimeoutMinutes)));
        sessions.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));

        if (sessions.size() <= MAX_SESSIONS) return;

        int excess = sessions.size() - MAX_SESSIONS;
        sessions.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().lastSeen))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(sessions::remove);
    }

    private void resetToMain(SessionData session) {
        session.state = ChatState.MAIN_MENU;
        session.courseOptionsExpanded = false;
        clearEnrollmentData(session);
        session.pendingEnrollmentAbortReturnState = null;
        session.pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;
    }

    private void resetToMainAndClearStudent(SessionData session) {
        resetToMain(session);
        clearStudentAccessData(session);
    }

    private void clearEnrollmentData(SessionData session) {
        session.nombre = null;
        session.documento = null;
        session.categoria = null;
        session.edad = null;
        session.email = null;
        session.telefono = null;
        session.direccion = null;
        session.sedeSeleccionada = null;
    }

    private void clearStudentAccessData(SessionData session) {
        session.pendingStudentAction = StudentAction.NONE;
        session.studentId = null;
        session.studentDocumento = null;
        session.studentEmail = null;
        session.studentNombre = null;
        session.studentCategoria = null;
        session.studentTipoPase = null;
        session.studentSede = null;
        session.studentBookingDate = null;
        session.studentBookingTipoPase = null;
        session.studentOtpVerified = false;
        session.pendingStudentLogoutReturnState = null;
    }
    private String practicalProcessText() {
        return "Proceso practico:\n" +
                "1) Tu pago debe estar aprobado y tu contrato firmado.\n" +
                "2) Ingresas como estudiante y validas tu identidad con OTP.\n" +
                "3) Eliges fecha y luego ves solo horas con profesor y vehiculo disponibles.\n" +
                "4) Cada practica dura " + Math.max(30, bookingDurationMinutes) + " minutos.\n" +
                "5) Si cancelas con menos de " + Math.max(1, bookingCancelMinHours) + " horas, se aplica multa.";
    }

    private boolean isMenuCommand(String text) {
        String cmd = normalizeCommandText(text);
        return "menu".equals(cmd) || "inicio".equals(cmd) || "start".equals(cmd);
    }

    private boolean isHelpCommand(String text) {
        String cmd = normalizeCommandText(text);
        return "ayuda".equals(cmd) || "help".equals(cmd);
    }

    private boolean isAdvisorCommand(String text) {
        String cmd = normalizeCommandText(text);
        return "asesor".equals(cmd)
                || "contactar asesor".equals(cmd)
                || "contactar un asesor".equals(cmd)
                || "hablar con asesor".equals(cmd);
    }

    private boolean isCancelCommand(String text) {
        String cmd = normalizeCommandText(text);
        return "cancelar".equals(cmd) || "salir".equals(cmd);
    }

    private boolean isStudentLogoutCommand(String text) {
        String cmd = normalizeCommandText(text);
        if (cmd.isBlank()) return false;
        return "cerrar sesion".equals(cmd)
                || "cerrar sesion estudiante".equals(cmd)
                || "cerrar sesion de estudiante".equals(cmd)
                || "cerrar mi sesion".equals(cmd)
                || "logout".equals(cmd)
                || "salir de estudiante".equals(cmd);
    }

    private boolean isStudentBackCommand(String text) {
        String cmd = normalizeCommandText(text);
        if (cmd.isBlank()) return false;
        return "volver".equals(cmd)
                || "atras".equals(cmd)
                || "regresar".equals(cmd)
                || "menu estudiante".equals(cmd)
                || "volver al menu estudiante".equals(cmd);
    }

    private boolean isStudentState(ChatState state) {
        if (state == null) return false;
        return switch (state) {
            case STUDENT_MENU,
                    STUDENT_DOC_CAPTURE,
                    STUDENT_OTP_VERIFY,
                    STUDENT_BOOKING_TYPE,
                    STUDENT_BOOKING_SLOT,
                    STUDENT_CANCEL_CLASS_PICK,
                    STUDENT_LOGOUT_CONFIRM -> true;
            default -> false;
        };
    }

    private boolean shouldConfirmEnrollmentAbort(ChatState state) {
        if (state == null) return false;
        return switch (state) {
            case ENROLLMENT_DATA_AUTH_WAIT,
                    ENROLLMENT_CAPTURE,
                    ENROLLMENT_EMAIL_CAPTURE,
                    ENROLLMENT_PHONE_CAPTURE,
                    ENROLLMENT_ADDRESS_CAPTURE,
                    ENROLLMENT_SEDE_CAPTURE,
                    ENROLLMENT_CONFIRM,
                    PAYMENT_PLAN_SELECT,
                    PAYMENT_WAIT,
                    CONTRACT_WAIT,
                    SEDE_SELECTION -> true;
            default -> false;
        };
    }

    private boolean isEnrollmentCommand(String cmd) {
        if (cmd == null) return false;
        String normalized = normalizeCommandText(cmd);
        if (normalized.isBlank()) return false;

        if (normalized.startsWith("matric")) return true;
        if (normalized.startsWith("inscrib")) return true;
        if (normalized.startsWith("inscripcion")) return true;
        return false;
    }

    private boolean isEnrollmentDataAuthAccepted(String cmd) {
        return "si".equals(cmd);
    }

    private boolean isEnrollmentDataAuthRejected(String cmd) {
        return "no".equals(cmd);
    }

    private boolean isEndCommand(String text) {
        String cmd = normalizeCommandText(text);
        if (cmd.isBlank()) return false;

        // Comandos exactos
        if ("terminar".equals(cmd)
                || "finalizar".equals(cmd)
                || "fin".equals(cmd)
                || "cerrar".equals(cmd)
                || "adios".equals(cmd)
                || "chao".equals(cmd)
                || "bye".equals(cmd)) {
            return true;
        }

        // Variantes comunes
        return cmd.startsWith("terminar ")
                || cmd.startsWith("finalizar ")
                || cmd.startsWith("cerrar ")
                || "salir del chat".equals(cmd)
                || "cerrar conversacion".equals(cmd)
                || "cerrar chat".equals(cmd);
    }

    private String normalizeCommandText(String text) {
        String lower = trim(text).toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return "";
        }

        String withoutDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return collapseSpaces(withoutDiacritics.replaceAll("[^a-z0-9\\s]+", " "));
    }

    private boolean isConversationExpired(SessionData session, Instant now) {
        if (session == null || now == null) return false;
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return now.isAfter(session.lastSeen.plus(Duration.ofMinutes(minutes)));
    }

    private String inactivityTimeoutText() {
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return "⏰ Conversación expirada por inactividad (" + minutes + " minutos).\n\n" +
                "Escribe MENU para iniciar de nuevo.";
    }

    private String conversationEndedText() {
        return "👋 Conversación finalizada.\n\n" +
                "Gracias por escribir a CEA HARO.\n" +
                "Si deseas iniciar de nuevo, escribe MENU.";
    }

    private String studentGreetingText(String studentName) {
        String displayName = collapseSpaces(trim(studentName));
        if (displayName.isBlank()) {
            displayName = "estudiante";
        }
        return "👋 Hola " + displayName + ", ¿qué quieres hacer hoy?";
    }

    private String studentNavigationOptionsText() {
        return "Opciones: VOLVER | MENU | CERRAR SESION";
    }

    private String studentLogoutConfirmText(String studentName) {
        String displayName = collapseSpaces(trim(studentName));
        if (displayName.isBlank()) {
            displayName = "estudiante";
        }
        return "🔒 Cerrar sesion de estudiante\n\n" +
                "Sesion activa: " + displayName + "\n\n" +
                "¿Seguro que desea cerrar su sesion?\n" +
                "Si confirma, debera validar OTP nuevamente para ingresar como estudiante.\n\n" +
                "Responda: SI o NO.";
    }

    private String enrollmentAbortConfirmText(boolean endsConversation) {
        if (endsConversation) {
            return "⚠️ ¿Seguro que desea terminar su proceso de matricula y finalizar la conversacion?\n\n" +
                    "Su progreso no se guardara.\n\n" +
                    "Responda SI para confirmar o NO para continuar.";
        }
        return "⚠️ ¿Seguro que desea terminar su proceso de matricula?\n\n" +
                "Su progreso no se guardara.\n\n" +
                "Responda SI para confirmar o NO para continuar.";
    }

    private String advisorContactText() {
        return advisorContactText(null);
    }

    private String advisorContactText(String predefinedMessage) {
        String suggestedMessage = buildAdvisorSuggestedMessage(predefinedMessage);
        String link = buildAdvisorLink(predefinedMessage);
        return "💬 Contactar asesor:\n" + link + "\n\n" +
                "Mensaje sugerido:\n" + suggestedMessage;
    }

    private String buildAdvisorLink(String predefinedMessage) {
        String suggestedMessage = buildAdvisorSuggestedMessage(predefinedMessage);
        return ADVISOR_WHATSAPP_LINK + "?text=" + URLEncoder.encode(suggestedMessage, StandardCharsets.UTF_8);
    }

    private String buildAdvisorSuggestedMessage(String predefinedMessage) {
        String normalized = collapseSpaces(predefinedMessage);
        String baseMessage = normalized.isBlank()
                ? "Necesito tu ayuda, por favor."
                : normalized;
        return "🤖: " + baseMessage +
                " Por favor, no borrar el emoji del robot para tener en cuenta el origen de la solicitud.";
    }
    // =========================
    // PROSPECTOS (NO ESTUDIANTE)
    // =========================

    private void trackProspectServiceSafe(String from, SessionData session, String servicio) {
        if (from == null || from.isBlank()) return;
        String srv = servicio == null ? "" : servicio.trim();
        if (srv.isBlank()) return;

        // Si esta en sesion de estudiante verificada, no lo contamos como prospecto.
        if (session != null && session.studentOtpVerified) {
            return;
        }

        try {
            prospectoService.registrarConsulta(from, srv);
        } catch (Exception e) {
            log.warn("No se pudo guardar prospecto from={} servicio={}", maskPhone(from), srv, e);
        }
    }

    private String prospectServiceFromCategoria(String categoria) {
        String cat = normalizeCategory(categoria);
        return switch (cat) {
            case "A2" -> "LICENCIA_A2";
            case "B1" -> "LICENCIA_B1";
            case "C1" -> "LICENCIA_C1";
            case "A2 y B1" -> "LICENCIA_A2_B1";
            case "A2, B1 y C1" -> "LICENCIA_A2_B1_C1";
            case "A2 y C1" -> "LICENCIA_A2_C1";
            case "B1 y C1" -> "LICENCIA_B1_C1";
            default -> "LICENCIA_" + cat.replaceAll("[^A-Z0-9]+", "_");
        };
    }

    // =========================
    // NORMALIZATION HELPERS
    // =========================

    private String normalizeInput(String text) {
        String base = trim(text).toLowerCase(Locale.ROOT);
        if (base.isBlank()) return "";

        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return collapseSpaces(normalized);
    }

    private String normalizeCategory(String value) {
        String n = normalizeInput(value)
                .replace(" ", "")
                .replace("+", "y")
                .replace("/", "y")
                .replace(",", "y");

        while (n.contains("yy")) n = n.replace("yy", "y");

        if (n.equals("a2")) return "A2";
        if (n.equals("b1")) return "B1";
        if (n.equals("c1")) return "C1";

        boolean hasA2 = n.contains("a2");
        boolean hasB1 = n.contains("b1");
        boolean hasC1 = n.contains("c1");

        if (hasA2 && hasB1 && hasC1) return "A2, B1 y C1";
        if (hasA2 && hasB1) return "A2 y B1";
        if (hasA2 && hasC1) return "A2 y C1";
        if (hasB1 && hasC1) return "B1 y C1";

        return "A2";
    }

    private String collapseSpaces(String value) {
        return trim(value).replaceAll("\\s+", " ");
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String v = trim(value);
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String maskPhone(String phone) {
        String digits = trim(phone).replaceAll("\\D+", "");
        if (digits.length() <= 4) return "****";
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private String maskEmail(String email) {
        String e = trim(email).toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.charAt(0) + "***" + e.substring(at);
    }

    private BotAction textMsg(String body) {
        return new BotAction("text", normalizeOutboundText(body), null, null, null);
    }

    private String normalizeOutboundText(String textRaw) {
        String text = trim(textRaw);
        if (text.isBlank()) {
            return text;
        }

        String fixed = text;
        fixed = fixed.replaceAll("(?m)^No pude\\b", "⚠️ No pude");
        fixed = fixed.replaceAll("(?m)^Responde\\b", "✍️ Responde");
        fixed = fixed.replaceAll("(?m)^Escribe MENU\\b", "🧭 Escribe MENU");
        fixed = fixed.replaceAll("(?m)^Opciones:\\s*", "\uD83D\uDCCC *Opciones:* ");
        fixed = fixed.replaceAll("(?m)^Comandos:\\s*", "\uD83D\uDEE0\uFE0F *Comandos:* ");
        return fixed;
    }

    private BotAction imageMsg(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return new BotAction("image", imageUrl, null, null, null);
    }

    private void addEnrollmentIntro(List<BotAction> actions) {
        if (includeImageActionInInbound) {
            BotAction image = imageMsg(enrollmentWelcomeImageUrl);
            if (image != null) {
                actions.add(image);
            }
        }
        actions.add(textMsg(
                "👋 ¡Gracias por elegir CEA HARO!\n\n" +
                        "Para brindarte un servicio personalizado, necesitamos algunos datos personales.\n" +
                        "Puedes revisar nuestra política de tratamiento de datos en www.ceaharo.com\n\n" +
                        "🔐 ¿Autorizas el tratamiento de tus datos?\n" +
                        "Responde: *SI* o *NO*."
        ));
    }

    // =========================
    // TEXTOS (PLANTILLAS + QUITAR REPETICIÓN DE "CATEGORÍA ...")
    // =========================

    private String mainMenuText() {
        return "👋 Hola, soy Ha-Rot, asistente virtual de CEA HARO.\n\n" +
                "¿Qué deseas hacer hoy?\n\n" +
                "1️⃣ Nuestros servicios (cursos, refuerzos y recategorización)\n" +
                "2️⃣ Iniciar matrícula\n" +
                "3️⃣ Horarios de atención y sedes\n" +
                "4️⃣ Soy un estudiante (consultas y reservas)\n" +
                "5️⃣ Contactar un asesor\n\n" +
                "✍️ Responde con el número de la opción.\n" +
                "Comandos: MENU | AYUDA | CANCELAR | TERMINAR";
    }

    private String coursesMenuText() {
        return "🧾 Nuestros Servicios\n" +
                "Selecciona el servicio que deseas conocer o solicitar.\n\n" +
                "1) 🏍️ Licencia A2\n" +
                "2) 🚗 Licencia B1\n" +
                "3) 🚕 Licencia C1\n" +
                "4) 🏍️🚗 A2 y B1\n" +
                "5) 🏍️🚗🚕 A2, B1 y C1\n" +
                "6) 🚘 Clase de refuerzo - Carro\n" +
                "7) 🏍️ Clase de refuerzo - Moto\n" +
                "8) 🔄 Recategorización B1 a C1\n\n" +
                "✍️ Responde con un número del 1 al 8.";
    }

    private String allCategoriesMenuText() {
        return "🧾 Nuestros Servicios\n" +
                "Selecciona el servicio que deseas conocer o solicitar.\n\n" +
                "1) 🏍️ Categoría A2\n" +
                "2) 🚗 Categoría B1\n" +
                "3) 🚕 Categoría C1\n" +
                "4) 🏍️🚗 Categoría A2 y B1\n" +
                "5) 🏍️🚗🚕 Categoría A2, B1 y C1\n" +
                "6) 🚘 Clase de refuerzo - Carro\n" +
                "7) 🏍️ Clase de refuerzo - Moto\n" +
                "8) 🔄 Recategorización B1 a C1\n\n" +
                "✍️ Responde con un número del 1 al 8.";
    }

    // Quitado: "Categoría A2"
    private String courseA2Text() {
        return "🏍️ *Categoría A2*\n\n" +
                "Para motocicletas de cualquier tipo de cilindraje.\n\n" +
                "💰 *Valor curso:* $920.000 (incluye examen médico)\n" +
                "🪪 *Valor licencia:* $272.800 en ventanilla única\n\n" +
                "✅ *Incluye:*\n" +
                "• 25 horas de teoría 👩‍🏫\n" +
                "• 3 horas de taller 🛠\n" +
                "• 15 horas de práctica 🚦\n" +
                "• Certificado 👨🏻‍🎓";
    }

    // Quitado: "Categoría B1"
    private String courseB1Text() {
        return "🚗 *Categoría B1*\n\n" +
                "Para vehículos de placa amarilla.\n\n" +
                "💰 *Valor curso:* $1.210.000 (incluye examen médico)\n" +
                "🪪 *Valor licencia:* $329.900 en ventanilla única\n\n" +
                "✅ *Incluye:*\n" +
                "• 25 horas de teoría 👩‍🏫\n" +
                "• 5 horas de taller 🛠\n" +
                "• 20 horas de práctica (16 ciudad + 4 carretera) 🚘\n" +
                "• Certificado 👨🏻‍🎓";
    }

    // Quitado: "Categoría C1"
    private String courseC1Text() {
        return "🚕 *Categoría C1*\n\n" +
                "Para vehículos de placa blanca y amarilla.\n\n" +
                "💰 *Valor curso:* $1.350.000 (incluye examen médico)\n" +
                "🪪 *Valor licencia:* $329.900 en ventanilla única\n\n" +
                "✅ *Incluye:*\n" +
                "• 30 horas de teoría 👩‍🏫\n" +
                "• 5 horas de taller 🛠\n" +
                "• 30 horas de práctica (26 ciudad + 4 carretera) 🚘\n" +
                "• Certificado 👨🏻‍🎓";
    }

    // Quitado: "Categoría A2 y B1"
    private String courseA2B1Text() {
        return "🏍️🚗 *Categoría A2 y B1*\n\n" +
                "Para motocicleta y vehículo particular.\n\n" +
                "💰 *Valor curso:* $1.990.000 (incluye examen médico)\n" +
                "🪪 *Valor licencias:* Moto $272.800 y carro $329.900 en ventanilla única\n\n" +
                "✅ *Incluye:*\n" +
                "• 30 horas teóricas 👩‍🏫\n" +
                "• 20 horas prácticas carro 🚘\n" +
                "• 15 horas prácticas moto 🏍\n" +
                "• 5 horas taller 🛠\n" +
                "• Certificado 👨🏻‍🎓";
    }

    // Quitado: "Categoría A2, B1 y C1"
    private String courseA2B1C1Text() {
        return "🏍️🚗🚕 *Categoría A2, B1 y C1*\n\n" +
                "Para moto, servicio particular y público.\n\n" +
                "💰 *Valor curso:* $2.150.000 (incluye examen médico)\n" +
                "🪪 *Valor licencias:* Moto $272.800 y carro $329.900 en ventanilla única\n\n" +
                "✅ *Incluye:*\n" +
                "• 30 horas de teoría 👩‍🏫\n" +
                "• 5 horas de taller 🛠\n" +
                "• 15 horas de práctica carro 🚘\n" +
                "• 30 horas de práctica moto 🏍\n" +
                "• Certificado 👨🏻‍🎓";
    }

    // Quitado: título duplicado ("Recategorización..." dos veces)
    private String courseRecategorizacionText() {
        return "🔄 *Recategorización B1 a C1*\n\n" +
                "Es para servicio particular B1 a servicio público C1\n\n" +
                "📌 Valor curso: $950.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 5 horas de Teoría 👩‍🏫\n" +
                "⏰ 10 horas de práctica. 🚘\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    private String serviceRefuerzoCarroText() {
        return "🚘 *Clase de refuerzo - Carro*\n\n" +
                "Refuerza tus habilidades al volante 🚘\n\n" +
                "📌 Duración: 45 minutos\n" +
                "📌 Valor por hora: $45.000\n\n" +
                "Importante:\n" +
                "✅ Al solicitar, dirígete al carrito para agregar la cantidad de clases que desees.";
    }

    private String serviceRefuerzoMotoText() {
        return "🏍️ *Clase de refuerzo - Moto*\n\n" +
                "Refuerza tus habilidades de conducción 🏍\n\n" +
                "📌 Duración: 40 minutos\n" +
                "📌 Valor por hora: $40.000\n\n" +
                "Importante:\n" +
                "✅ Al solicitar, dirígete al carrito para agregar la cantidad de clases que desees.";
    }

    private String enrollmentInitialPromptText() {
        return "📝 Perfecto. Vamos a iniciar tu matrícula en CEA HARO.\n\n" +
                "Para continuar, envíame esta información *en un solo mensaje*:\n\n" +
                "1) Nombre completo (como aparece en tu documento)\n" +
                "2) Número de documento (sin puntos ni comas)\n" +
                "3) Categoría que deseas realizar (A2, B1, C1, A2 y B1, A2, B1 y C1)\n\n" +
                "✅ Ejemplo:\n" +
                "Juan Perez 12345678 A2\n\n" +
                "Luego te pediré tu edad, correo, teléfono, dirección y la sede (Kennedy o CC El Eden).";
    }

    private String infoText() {
        return "📌 *Horarios de Atención*\n\n" +
                "*🗂️ Administrativo*\n" +
                "Lunes a Sábado\n" +
                "🕗 8:00 AM - 6:00 PM\n\n" +

                "*📚 Clases Teóricas*\n" +
                "🗓️ Lunes y Viernes: 6:00 AM - 2:00 PM\n" +
                "🗓️ Martes, Miércoles y Jueves: 2:00 PM - 10:00 PM\n" +
                "⏳ Puedes ver mínimo 2 y máximo 8 horas al día\n\n" +

                "*🚗 Clases Prácticas*\n" +
                "🗓️ Domingo a Domingo\n" +
                "🕕 6:00 AM - 10:00 PM\n" +
                "(Sujeto a disponibilidad)\n\n" +

                "✨ ¡Nos adaptamos a tu tiempo!\n\n" +

                "*📍 Sedes disponibles:*\n" +
                "1️⃣ Kennedy – Av. 1 de Mayo #68D-23 Piso 2\n" +
                "2️⃣ CC El Edén – Local L2-094A";
    }

    private String studentMenuText() {
        return "🎓✨ Soy estudiante CEA HARO\n\n" +
                "1️⃣ Consultar calendario de prácticas\n" +
                "2️⃣ Agendar clase práctica\n" +
                "3️⃣ Consultar mi horario\n" +
                "4️⃣ Cancelar clase práctica\n" +
                "5️⃣ ⬅️ Volver al menú principal\n" +
                "6️⃣ 🔒 Cerrar sesión de estudiante\n\n" +
                "✍️ Responde con un número del 1 al 6.\n" +
                "Tip: escribe VOLVER para ver este menú en cualquier momento.";
    }

    private String helpText(ChatState state) {
        return "🆘 *Ayuda*\n\n" +
                "🧭 Estado actual: " + state + "\n\n" +
                "Comandos disponibles:\n" +
                "• MENU: volver al inicio\n" +
                "• AYUDA: ver esta ayuda\n" +
                "• ASESOR: contactar un asesor\n" +
                "• CANCELAR: cancelar el proceso actual\n" +
                "• TERMINAR: finalizar la conversación\n\n" +
                "⏱️ Inactividad:\n" +
                "• Si no respondes en " + Math.max(1, inactivityTimeoutMinutes) + " minutos, la conversación expira y debes iniciar de nuevo.";
    }
}
