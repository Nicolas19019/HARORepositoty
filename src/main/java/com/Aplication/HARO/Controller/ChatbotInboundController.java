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
/**
 * Punto de entrada del chatbot inbound.
 *
 * Coordina el menu principal, el flujo de matricula, la firma de contratos,
 * la validacion de estudiante por OTP y las operaciones sobre clases practicas.
 * La clase tambien conserva el estado temporal de cada conversacion en memoria.
 */
public class ChatbotInboundController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotInboundController.class);
    private static final String ADVISOR_WHATSAPP_LINK = "https://wa.me/573202114876";

    // NavegaciÃ³n por opciones numÃ©ricas: el usuario solo escribe texto cuando el bot pida datos.
    // Atajos globales:
    //   9 = Volver al menÃº principal
    //   0 = Volver (solo en el flujo de estudiante)
    private static final String CMD_MENU = "9";
    private static final String CMD_BACK = "0";
    private static final String FEEDBACK_FORM_URL = "https://forms.cloud.microsoft/r/AMvUFPUL2X";

    private static final int MAX_SESSIONS = 5000;
    /**
     * Formato esperado (un solo mensaje):
     *   Nombre(s) + Documento
     *
     * Luego el bot mostrarÃ¡ un menÃº para elegir la categorÃ­a.
     *
     * Formato legacy (si envÃ­as todo en un solo mensaje):
     *   Nombre(s) + Documento + CategorÃ­a (A2, B1, C1, A2 y B1, A2, B1 y C1)
     *
     * Ejemplos:
     *   Juan Perez 12345678
     *   Juan Perez 12345678 A2
     *   Juan Perez 12345678 A2 y B1
     *   Juan Perez 12345678 A2, B1 y C1
     */
    private static final Pattern ENROLLMENT_BASIC_PATTERN = Pattern.compile(
            // Documento puede venir con separadores (puntos, espacios, guiones, comas); luego se normaliza a solo digitos.
            "(?i)^\\s*(.+?)\\s+([0-9][0-9\\s\\.,-]{3,40}[0-9])\\s+(" +
                    "A2\\s*(?:Y|,|\\+|/|-)\\s*B1\\s*(?:Y|,|\\+|/|-)\\s*C1" + // A2 y B1 y C1
                    "|A2\\s*(?:Y|,|\\+|/|-)\\s*B1" +                         // A2 y B1
                    "|A2\\s*(?:Y|,|\\+|/|-)\\s*C1" +                         // A2 y C1 (incluye A2 - C1)
                    "|B1\\s*(?:Y|,|\\+|/|-)\\s*C1" +                         // B1 y C1
                    "|A2|B1|C1" +
                    ")\\s*$"
    );

    // Formato recomendado: Nombre + Documento (sin categorÃ­a)
    private static final Pattern ENROLLMENT_NAME_DOC_PATTERN = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+([0-9][0-9\\s\\.,-]{3,40}[0-9])\\s*$"
    );

    // Formato opcional: Nombre + Documento + opciÃ³n de categorÃ­a (1..5)
    private static final Pattern ENROLLMENT_NAME_DOC_OPTION_PATTERN = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+([0-9][0-9\\s\\.,-]{3,40}[0-9])\\s+([1-5])\\s*$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    // Normalizamos el telefono a solo digitos (sin +57) y exigimos 10 digitos para Colombia.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

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

    /**
     * Horas minimas de anticipacion para permitir cancelar una clase.
     */
    @Value("${chatbot.booking.cancel.min-hours:48}")
    private long bookingCancelMinHours;

    /**
     * URL opcional de imagen para el mensaje inicial de matricula.
     */
    @Value("${chatbot.enrollment.welcome-image-url:}")
    private String enrollmentWelcomeImageUrl;
    /**
     * Define si la respuesta inbound incluye acciones de imagen.
     */
    @Value("${chatbot.inbound.include-image-action:false}")
    private boolean includeImageActionInInbound;

    /**
     * Inyecta las dependencias necesarias del controlador.
     */
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

   /** Mensaje normalizado que llega desde el proveedor de WhatsApp/chat. */
   public record InboundMessage(
        String from,
        String text,
        String messageId,
        String timestamp,
        String phoneNumberId,
        Boolean newConversation
) {}
    /** Accion de salida que luego el integrador convierte en mensaje real al usuario. */
    public record BotAction(
            String type,
            String body,
            String name,
            String lang,
            List<String> params
    ) {}

    /** Respuesta agrupada del bot para un turno de conversacion. */
    public record BotResponse(List<BotAction> actions) {}

    /** Datos minimos de matricula capturados en un solo mensaje. */
    private record EnrollmentBasic(String nombre, String documento, String categoria) {}
    /** Variante reducida para capturar nombre y documento antes de elegir categoria. */
    private record EnrollmentNameDoc(String nombre, String documento) {}
    /** Fecha y hora ya separadas para agendar una clase. */
    private record BookingSlot(String fecha, String hora) {}

    /** Estados principales del flujo conversacional. */
    enum ChatState {
        MAIN_MENU,
        COURSES_MENU,

        ENROLLMENT_DATA_AUTH_WAIT,
        ENROLLMENT_CAPTURE,
        ENROLLMENT_CATEGORY_SELECT,
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

    /** Acciones disponibles dentro del subflujo de estudiante autenticado. */
    enum StudentAction {
        NONE,
        VIEW_CALENDAR,
        VIEW_SCHEDULE,
        BOOK_CLASS,
        CANCEL_CLASS
    }

    /** Acciones diferidas cuando el usuario confirma cancelar o salir del flujo. */
    enum EnrollmentAbortAction {
        NONE,
        CANCEL_TO_MENU,
        END_CONVERSATION
    }

    /** Estado en memoria de una conversacion activa. */
    static class SessionData {
        ChatState state = ChatState.MAIN_MENU;
        boolean courseOptionsExpanded = false;

        // Captura matrÃ­cula
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

    /**
     * Sesiones activas en memoria del chatbot por numero de telefono.
     */
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Endpoint principal del chatbot.
     *
     * Resuelve la sesion activa, enruta el mensaje al handler correspondiente
     * y devuelve la lista de acciones que el canal debe entregar al usuario.
     */
    @PostMapping(value = "/inbound", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<BotResponse> inbound(@RequestBody InboundMessage msg) {
        evictStaleSessions();

        String from = trim(msg == null ? null : msg.from());
        String rawText = trim(msg == null ? null : msg.text());
        String text = normalizeInput(rawText);

        if (from.isBlank()) {
            return ResponseEntity.ok(new BotResponse(List.of(
                    textMsg("âš ï¸ No pude identificar tu nÃºmero.\n\nResponde " + CMD_MENU + " para volver al inicio.")
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

            // MenÃº principal (opciÃ³n global)
            if (isMenuCommand(text)) {
                if (shouldConfirmEnrollmentAbort(session.state)) {
                    session.pendingEnrollmentAbortReturnState = session.state;
                    session.pendingEnrollmentAbortAction = EnrollmentAbortAction.CANCEL_TO_MENU;
                    session.state = ChatState.ENROLLMENT_ABORT_CONFIRM;
                    actions.add(textMsg(enrollmentAbortConfirmText(false)));
                    actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
                    session.lastSeen = now;
                    return ResponseEntity.ok(new BotResponse(actions));
                }

                resetToMain(session);
                actions.add(textMsg(mainMenuText()));
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
                case ENROLLMENT_CATEGORY_SELECT -> handleEnrollmentCategorySelect(from, text, session, actions);
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
                actions.add(textMsg("âœ… Tu proceso ya fue completado.\n\nResponde " + CMD_MENU + " para iniciar una nueva solicitud."));
                actions.add(textMsg(feedbackSurveyText()));
            }
            }

            session.lastSeen = now;
        }

        return ResponseEntity.ok(new BotResponse(actions));
    }

    // =========================
    // HANDLERS
    // =========================

    /** Atiende las opciones del menu principal y abre el flujo seleccionado. */
    private void handleMainMenu(String text, String from, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);

        switch (cmd) {
            case "1" -> {
                trackProspectServiceSafe(from, session, "SERVICIOS_MENU");
                session.state = ChatState.COURSES_MENU;
                session.courseOptionsExpanded = false;
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case "2" -> {
                trackProspectServiceSafe(from, session, "INICIAR_MATRICULA");
                startEnrollmentAuthorization(session, actions);
            }
            case "3" -> {
                trackProspectServiceSafe(from, session, "HORARIOS_Y_SEDES");
                actions.add(textMsg(infoText()));
                actions.add(textMsg(practicalProcessText()));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case "4" -> {
                if (session.studentOtpVerified && session.studentId != null) {
                    session.state = ChatState.STUDENT_MENU;
                    actions.add(textMsg("ðŸŽ“ Ya tienes una sesion de estudiante activa."));
                    actions.add(textMsg(studentGreetingText(session.studentNombre)));
                    actions.add(textMsg(studentMenuText()));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                if (session.studentId != null && !session.studentOtpVerified && !trim(session.studentEmail).isBlank()) {
                    session.state = ChatState.STUDENT_OTP_VERIFY;
                    actions.add(textMsg(
                            "ðŸ” Ya iniciamos tu verificacion como estudiante.\n\n" +
                                    "Escribe el codigo OTP que enviamos al correo " + maskEmail(session.studentEmail) + ".\n\n" +
                                    "Si no lo encuentras, revisa Spam/No deseado o escribe tu documento nuevamente para solicitar otro codigo."
                    ));
                    actions.add(textMsg("Opciones: " + CMD_MENU));
                    return;
                }

                clearStudentAccessData(session);
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg(
                        "ðŸŽ“ Â¡Perfecto! Vamos a validar tu acceso como estudiante.\n\n" +
                                "1) Escribe tu numero de documento (solo numeros).\n" +
                                "2) Te enviaremos un OTP al correo registrado.\n" +
                                "3) Con una sola validacion podras navegar por el menu de estudiante hasta que cierres sesion o expire por inactividad.\n\n" +
                                "Ejemplo: 12345678"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case "5" -> {
                trackProspectServiceSafe(from, session, "CONTACTAR_ASESOR");
                actions.add(textMsg(advisorContactText()));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case "6" -> {
                trackProspectServiceSafe(from, session, "REANUDAR_PROCESO");
                try {
                    Optional<ChatbotMatriculaProceso> procesoOpt = procesoService.findLatestProcesoByPhone(from);
                    if (procesoOpt.isEmpty()) {
                        actions.add(textMsg(
                                "âš ï¸ No encuentro un proceso reciente asociado a este nÃºmero.\n\n" +
                                        "Responde 2 para iniciar tu matrÃ­cula."
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
                                    "ðŸ’³ AÃºn no tenemos el pago confirmado.\n\n" +
                                            "Enlace de pago:\n" + payLink
                            ));
                        } else {
                            actions.add(textMsg(
                                    "âš ï¸ AÃºn no hay un enlace de contratos disponible porque el pago no estÃ¡ confirmado.\n\n" +
                                            "Responde 2 para iniciar tu matrÃ­cula o continÃºa tu proceso."
                            ));
                        }
                        actions.add(textMsg("Opciones: " + CMD_MENU));
                        return;
                    }

                    // En este punto el pago estÃ¡ aprobado: entregamos (y si aplica regeneramos) el enlace de contrato.
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
                        actions.add(textMsg("âš ï¸ AÃºn no hay un enlace de contrato disponible."));
                        actions.add(textMsg("Opciones: " + CMD_MENU));
                        return;
                    }

                    actions.add(textMsg(
                            "ðŸ“„ *Enlace de contrato*\n\n" +
                                    link + "\n\n" +
                                    buildContractExpiryHint(expiresAt) + "\n\n" +
                                    "Cuando termines de firmar, responde 1.\n" +
                                    "Si necesitas el enlace nuevamente, responde 2."
                    ));
                    actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
                } catch (Exception e) {
                    log.error("No se pudo resolver link de contrato desde menu principal from={}: {}", maskPhone(from), e.getMessage(), e);
                    actions.add(textMsg("âš ï¸ No pude obtener el enlace de contrato en este momento. Intenta nuevamente."));
                    actions.add(textMsg("Opciones: " + CMD_MENU));
                }
            }
            default -> {
                actions.add(textMsg("âš ï¸ OpciÃ³n no reconocida en este menÃº. Responde un nÃºmero del 1 al 6."));
                actions.add(textMsg(mainMenuText()));
            }
        }
    }

    /** Atiende el submenu de cursos, categorias y servicios complementarios. */
    private void handleCoursesMenu(String text, String from, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        // "ver todas/combos" por compatibilidad
        if (false && ("todos".equals(cmd) || "todas".equals(cmd) || "ver todas".equals(cmd) || "combos".equals(cmd) || "ver todos".equals(cmd))) {
            session.courseOptionsExpanded = true;
            actions.add(textMsg(allCategoriesMenuText()));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        // MenÃº expandido
        if (session.courseOptionsExpanded) {
            switch (cmd) {
                case "1" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2");
                    actions.add(textMsg(courseA2Text()));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "2" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_B1");
                    actions.add(textMsg(courseB1Text()));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "3" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_C1");
                    actions.add(textMsg(courseC1Text()));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "4" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2_B1");
                    actions.add(textMsg(courseA2B1Text()));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "5" -> {
                    trackProspectServiceSafe(from, session, "LICENCIA_A2_B1_C1");
                    actions.add(textMsg(courseA2B1C1Text()));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "6" -> {
                    trackProspectServiceSafe(from, session, "REFUERZO_CARRO");
                    actions.add(textMsg(serviceRefuerzoCarroText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de carro por favor.")));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "7" -> {
                    trackProspectServiceSafe(from, session, "REFUERZO_MOTO");
                    actions.add(textMsg(serviceRefuerzoMotoText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de moto por favor.")));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
                case "8" -> {
                    trackProspectServiceSafe(from, session, "RECATEGORIZACION_B1_C1");
                    actions.add(textMsg(courseRecategorizacionText()));
                    actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de la recategorizacion B1 a C1 por favor.")));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
            default -> {
                actions.add(textMsg("âš ï¸ OpciÃ³n no reconocida para este listado de cursos."));
                actions.add(textMsg(allCategoriesMenuText()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            }
            return;
        }

        // MenÃº principal de cursos (1..8)
        switch (cmd) {
            case "1" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2");
                actions.add(textMsg(courseA2Text()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "2" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_B1");
                actions.add(textMsg(courseB1Text()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "3" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_C1");
                actions.add(textMsg(courseC1Text()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "4" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2_B1");
                actions.add(textMsg(courseA2B1Text()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "5" -> {
                trackProspectServiceSafe(from, session, "LICENCIA_A2_B1_C1");
                actions.add(textMsg(courseA2B1C1Text()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "6" -> {
                trackProspectServiceSafe(from, session, "REFUERZO_CARRO");
                actions.add(textMsg(serviceRefuerzoCarroText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de carro por favor.")));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "7" -> {
                trackProspectServiceSafe(from, session, "REFUERZO_MOTO");
                actions.add(textMsg(serviceRefuerzoMotoText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de las clases de refuerzo de moto por favor.")));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            case "8" -> {
                trackProspectServiceSafe(from, session, "RECATEGORIZACION_B1_C1");
                actions.add(textMsg(courseRecategorizacionText()));
                actions.add(textMsg(advisorContactText("Hola, quiero mas informacion de la recategorizacion B1 a C1 por favor.")));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
            default -> {
                actions.add(textMsg("âš ï¸ OpciÃ³n no reconocida para este menÃº. Escoge un nÃºmero del 1 al 8."));
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            }
        }
    }

    /** Inicia la autorizacion para tratar datos antes de capturar la matricula. */
    private void startEnrollmentAuthorization(SessionData session, List<BotAction> actions) {
        clearEnrollmentData(session);
        session.state = ChatState.ENROLLMENT_DATA_AUTH_WAIT;
        addEnrollmentIntro(actions);
    }

    /** Procesa la respuesta del usuario frente a la autorizacion de datos. */
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
            actions.add(textMsg("DespuÃ©s de ese primer mensaje te mostrarÃ© un menÃº para elegir la categorÃ­a, y luego te pedirÃ© tu edad, correo, telÃ©fono, direcciÃ³n y la sede."));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        actions.add(textMsg("Para continuar, responde 1 (SI) o 2 (NO) sobre el tratamiento de datos."));
        actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
    }

    /** Captura nombre y documento del aspirante desde un solo mensaje. */
    private void handleEnrollmentCapture(String from, String rawText, SessionData session, List<BotAction> actions) {
        EnrollmentBasic basic = parseEnrollmentBasicOption(rawText);

        // Legacy: si el usuario envÃ­a todo junto (incluyendo categorÃ­a), lo aceptamos.
        if (basic != null) {
            session.nombre = basic.nombre();
            session.documento = basic.documento();
            session.categoria = basic.categoria();
            session.state = ChatState.ENROLLMENT_AGE_CAPTURE;

            trackProspectServiceSafe(from, session, prospectServiceFromCategoria(session.categoria));

            actions.add(textMsg(
                    "Paso 2 de 8: envÃ­a tu edad en aÃ±os.\n\n" +
                            "Ejemplo: 18\n\n" +
                            "Importante: debes tener mÃ­nimo 16 aÃ±os para matricularte."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        // Nuevo: primero capturamos nombre + documento, luego se elige categorÃ­a por menÃº.
        EnrollmentNameDoc nameDoc = parseEnrollmentNameDoc(rawText);
        if (nameDoc == null) {
            actions.add(textMsg(
                    "âš ï¸ No pude leer tus datos.\n\n" +
                            "EnvÃ­a tu *nombre completo* y *nÃºmero de documento* en un solo mensaje:\n\n" +
                            "âœ… Ejemplo:\n" +
                            "Juan Perez 12345678\n\n" +
                            "Luego te mostrarÃ© un menÃº para seleccionar la categorÃ­a."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        session.nombre = nameDoc.nombre();
        session.documento = nameDoc.documento();
        session.state = ChatState.ENROLLMENT_CATEGORY_SELECT;

        actions.add(textMsg(enrollmentCategoryMenuText()));
        actions.add(textMsg("Opciones: 1 | 2 | 3 | 4 | 5 | " + CMD_MENU));
    }

    /** Guarda la categoria elegida y mueve el flujo al siguiente dato obligatorio. */
    private void handleEnrollmentCategorySelect(String from, String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if (cmd.isBlank()) {
            actions.add(textMsg("âš ï¸ OpciÃ³n invÃ¡lida. Responde con un nÃºmero del 1 al 5."));
            actions.add(textMsg(enrollmentCategoryMenuText()));
            actions.add(textMsg("Opciones: 1 | 2 | 3 | 4 | 5 | " + CMD_MENU));
            return;
        }

        String categoria = switch (cmd) {
            case "1" -> "A2";
            case "2" -> "B1";
            case "3" -> "C1";
            case "4" -> "A2 y B1";
            case "5" -> "A2, B1 y C1";
            default -> "";
        };

        if (categoria.isBlank()) {
            actions.add(textMsg("âš ï¸ OpciÃ³n no reconocida. Responde con un nÃºmero del 1 al 5."));
            actions.add(textMsg(enrollmentCategoryMenuText()));
            actions.add(textMsg("Opciones: 1 | 2 | 3 | 4 | 5 | " + CMD_MENU));
            return;
        }

        session.categoria = categoria;
        session.state = ChatState.ENROLLMENT_AGE_CAPTURE;

        trackProspectServiceSafe(from, session, prospectServiceFromCategoria(session.categoria));

        actions.add(textMsg(
                "Paso 2 de 8: envÃ­a tu edad en aÃ±os.\n\n" +
                        "Ejemplo: 18\n\n" +
                        "Importante: debes tener mÃ­nimo 16 aÃ±os para matricularte."
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Valida y guarda la edad para completar el perfil de matricula. */
    private void handleEnrollmentAgeCapture(String text, SessionData session, List<BotAction> actions) {
        String digits = trim(text).replaceAll("\\D+", "");
        if (digits.isBlank()) {
            actions.add(textMsg(
                    "âš ï¸ Edad invÃ¡lida.\n\n" +
                            "EnvÃ­a solo tu edad en aÃ±os (nÃºmero).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        final int age;
        try {
            age = Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            actions.add(textMsg(
                    "âš ï¸ Edad invÃ¡lida.\n\n" +
                            "EnvÃ­a solo tu edad en aÃ±os (nÃºmero).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        if (age < 16) {
            resetToMain(session);
            actions.add(textMsg(
                    "âš ï¸ En este momento no podemos continuar con la matrÃ­cula.\n\n" +
                            "La matrÃ­cula estÃ¡ disponible desde los 16 aÃ±os.\n\n" +
                            "Si necesitas ayuda, puedes contactar un asesor."
            ));
            actions.add(textMsg(advisorContactText()));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        if (age > 100) {
            actions.add(textMsg(
                    "âš ï¸ Edad invÃ¡lida.\n\n" +
                            "EnvÃ­a solo tu edad en aÃ±os (nÃºmero).\n" +
                            "Ejemplo: 18"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        session.edad = age;
        session.state = ChatState.ENROLLMENT_EMAIL_CAPTURE;

        actions.add(textMsg(
                "Paso 3 de 8: envia tu correo electronico.\n" +
                        "Ejemplo: usuario@correo.com"
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Valida y guarda el correo del aspirante. */
    private void handleEnrollmentEmailCapture(String text, SessionData session, List<BotAction> actions) {
        String email = trim(text).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            actions.add(textMsg(
                    "âš ï¸ Correo invÃ¡lido.\n\n" +
                            "EnvÃ­a solo el correo, por ejemplo: usuario@correo.com"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        session.email = email;
        session.state = ChatState.ENROLLMENT_PHONE_CAPTURE;

        actions.add(textMsg(
                "Paso 4 de 8: envia tu telefono de contacto.\n" +
                        "Ejemplo: 3001112233 (sin +57)"
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Valida y normaliza el telefono del aspirante. */
    private void handleEnrollmentPhoneCapture(String text, SessionData session, List<BotAction> actions) {
        String digits = trim(text).replaceAll("\\D+", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        // Si viene con prefijo 57, nos quedamos con el numero local (ultimos 10 digitos).
        if (digits.startsWith("57") && digits.length() > 10) {
            digits = digits.substring(Math.max(0, digits.length() - 10));
        }
        if (!PHONE_PATTERN.matcher(digits).matches()) {
            actions.add(textMsg(
                    "âš ï¸ TelÃ©fono invÃ¡lido.\n\n" +
                            "EnvÃ­alo sin +57, solo 10 dÃ­gitos.\n" +
                            "Ejemplo: 3001112233\n\n" +
                            "Tip: si lo envÃ­as como +57 3001112233 o 573001112233, yo lo limpio automÃ¡ticamente."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        session.telefono = digits;
        session.state = ChatState.ENROLLMENT_ADDRESS_CAPTURE;
        actions.add(textMsg(
                "Paso 5 de 8: envia tu direccion de residencia.\n\n" +
                        "EscrÃ­bela completa con barrio, nomenclatura o apartamento si aplica.\n\n" +
                        "Ejemplo: Cra 80 #12-45 Apto 302, Av. 1 de Mayo, Bogota"
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Guarda la direccion reportada por el usuario. */
    private void handleEnrollmentAddressCapture(String rawText, SessionData session, List<BotAction> actions) {
        String address = collapseSpaces(rawText);
        if (address.length() < 8) {
            actions.add(textMsg(
                    "âš ï¸ Direccion invalida.\n\n" +
                            "Enviala con mas detalle para poder registrarla correctamente.\n" +
                            "Ejemplo: Cra 80 #12-45 Apto 302, Av. 1 de Mayo, Bogota"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        session.direccion = address;
        session.state = ChatState.ENROLLMENT_SEDE_CAPTURE;
        actions.add(textMsg(
                "Paso 6 de 8: selecciona tu sede.\n\n" +
                        "1) Av. 1 de Mayo #68D-23 Piso 2\n" +
                        "2) El Eden - Local L2-094A\n\n" +
                        "Responde con 1 o 2."
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Guarda la sede seleccionada y presenta el resumen final del proceso. */
    private void handleEnrollmentSedeCapture(String from, String text, SessionData session, List<BotAction> actions) {
        String sede = resolveSedeSelection(text);
        if (sede.isBlank()) {
            actions.add(textMsg(
                    "Debes seleccionar una sede valida.\n\n" +
                            "1) Av. 1 de Mayo\n" +
                            "2) El Eden\n\n" +
                            "Responde con 1 o 2."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
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
            actions.add(textMsg("âš ï¸ No pude guardar tu pre-registro en este momento. Intenta nuevamente."));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        session.state = ChatState.ENROLLMENT_CONFIRM;

        actions.add(textMsg(
                "Paso 7 de 8: revisa tus datos y confirma.\n\n" +
                        "Nombre: " + safe(session.nombre) + "\n" +
                        "Documento: " + safe(session.documento) + "\n" +
                        "Categoria: " + safe(session.categoria) + "\n" +
                        "Edad: " + (session.edad == null ? "N/A" : (session.edad + " aÃ±os")) + "\n" +
                        "Correo: " + safe(session.email) + "\n" +
                        "Telefono: " + safe(session.telefono) + "\n" +
                        "Direccion: " + safe(session.direccion) + "\n" +
                        "Sede: " + safe(session.sedeSeleccionada) + "\n\n" +
                        "Responde:\n" +
                        "1) Confirmar y continuar\n" +
                         "2) Corregir datos\n" +
                          "3) Cancelar"
        ));
        actions.add(textMsg("Opciones: 1 | 2 | 3 | " + CMD_MENU));
    }
    /** Confirma la matricula y abre el paso de pago. */
    private void handleEnrollmentConfirm(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        switch (cmd) {
            case "1" -> {
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
                        actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                        return;
                    }

                    session.state = ChatState.PAYMENT_METHOD_SELECT;

                    actions.add(textMsg(
                            "Paso 8 de 8: selecciona tu metodo de pago.\n\n" +
                                    "1) Pagar por ePayco (en linea)\n" +
                                    "2) Pagar en efectivo en la academia\n\n" +
                                    "Responde 1 o 2."
                    ));
                    actions.add(textMsg("Opciones: " + CMD_MENU));
                } catch (Exception e) {
                    log.error("No se pudo iniciar pago from={} doc={} email={}",
                            maskPhone(session.telefono),
                            safe(session.documento),
                            maskEmail(session.email),
                            e);
                    actions.add(textMsg("âš ï¸ No pude iniciar el pago en este momento. Intenta de nuevo en 1 minuto."));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                }
            }
            case "2" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("DespuÃ©s de ese primer mensaje te mostrarÃ© un menÃº para elegir la categorÃ­a, y luego te pedirÃ© tu edad, correo, telÃ©fono, direcciÃ³n y la sede."));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case "3" -> {
                resetToMain(session);
                actions.add(textMsg("âŒ Proceso cancelado.\n\n" + mainMenuText()));
            }
            default -> {
                actions.add(textMsg("Responde 1, 2 o 3."));
                actions.add(textMsg("Opciones: 1 | 2 | 3 | " + CMD_MENU));
            }
        }
    }

    /** Decide el medio de pago con el que continuara el aspirante. */
    private void handlePaymentMethodSelect(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);

        if ("1".equals(cmd)) {
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
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        if ("2".equals(cmd)) {
            try {
                procesoService.markCashPaymentPending(session.documento);
            } catch (Exception ex) {
                log.error("No se pudo marcar pago en efectivo doc={}: {}", safe(session.documento), ex.getMessage(), ex);
                actions.add(textMsg("âš ï¸ No pude registrar el pago en efectivo en este momento. Intenta nuevamente."));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                return;
            }

            session.state = ChatState.PAYMENT_CASH_WAIT;
            actions.add(textMsg(
                    "âœ… Tu proceso quedo registrado correctamente.\n\n" +
                            "â³ El pago en efectivo debe ser confirmado por la academia antes de continuar.\n\n" +
                            "ðŸ“© Cuando validemos tu pago, te enviaremos por este chat el enlace para firmar los contratos."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
            return;
        }

        actions.add(textMsg(
                "Selecciona el metodo de pago para continuar:\n\n" +
                        "1) Pagar por ePayco (en linea)\n" +
                        "2) Pagar en efectivo en la academia"
        ));
        actions.add(textMsg("Opciones: " + CMD_MENU));
    }

    /** Define el plan de pago y registra el contexto para retomar el flujo despues. */
    private void handlePaymentPlanSelect(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        final String plan;
        final String planLabel;

        if ("1".equals(cmd)) {
            plan = "FULL";
            planLabel = "completo (100%)";
        } else if ("2".equals(cmd)) {
            plan = "HALF";
            planLabel = "por la mitad (50%)";
        } else {
            actions.add(textMsg(
                    "Responde 1 o 2 para continuar con el pago.\n\n" +
                            "1) Pagar completo (100%)\n" +
                            "2) Pagar por la mitad (50%)"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU));
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
                            "Si necesitas el enlace otra vez, responde 1.\n" +
                            "Si ya pagaste, responde 2."
            ));
            actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
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
                                "Puedes pagar completo respondiendo 1, o responder " + CMD_MENU + "."
                ));
                actions.add(textMsg("Opciones: 1 | " + CMD_MENU));
                return;
            }

            actions.add(textMsg("âš ï¸ No pude iniciar el pago en este momento. Intenta de nuevo en 1 minuto."));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        }
    }

    /** Mantiene informado al usuario mientras la academia confirma un pago en efectivo. */
    private void handlePaymentCashWait(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if ("1".equals(cmd)) {
            actions.add(textMsg(
                    "â³ Aun no podemos continuar.\n\n" +
                            "El pago en efectivo debe ser confirmado por la academia.\n\n" +
                            "Cuando sea confirmado te enviaremos por este chat el enlace para firmar contratos."
            ));
            actions.add(textMsg("Opciones: 1 | " + CMD_MENU));
            return;
        }

        actions.add(textMsg(
                "â³ Estamos esperando la confirmacion del pago en efectivo.\n\n" +
                        "Cuando se confirme, te enviaremos por este chat el enlace para firmar los contratos.\n\n" +
                        "Responde 1 para ver el estado o " + CMD_MENU + " para volver al inicio."
        ));
        actions.add(textMsg("Opciones: 1 | " + CMD_MENU));
    }

    /** Mantiene el flujo a la espera de confirmacion del pago electronico. */
    private void handlePaymentWait(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);

        if ("1".equals(cmd)) {
            try {
                String link = procesoService.getPaymentLink(session.documento);
                capturePaymentContextFromChat(link, session, "chatbot_inbound_relink");

                actions.add(textMsg(
                        "ðŸ’³ *Enlace de pago*\n\n" +
                                link + "\n\n" +
                                "DespuÃ©s de pagar vuelve a este chat."
                ));
            } catch (Exception e) {
                actions.add(textMsg("âš ï¸ No pude encontrar el enlace de pago para este proceso."));
            }
            actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            return;
        }

        if ("2".equals(cmd)) {
            actions.add(textMsg(
                    "âœ… Perfecto.\n\n" +
                            "Estamos validando tu pago con la pasarela.\n\n" +
                            "ðŸ“© Cuando el pago sea confirmado te enviaremos el siguiente paso en este chat."
            ));
            actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            return;
        }

        actions.add(textMsg(
                "ðŸ’³ Estamos esperando la confirmaciÃ³n de tu pago.\n\n" +
                        "Responde:\n" +
                        "1) Ver enlace de pago\n" +
                        "2) Ya paguÃ©\n\n" +
                        "Si ya pagaste, no necesitas enviar nada: te avisaremos apenas se confirme."
        ));
        actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
    }

    /** Extrae datos utiles desde el link de pago para poder sincronizar estados luego. */
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

    /** Lee un query param puntual desde una URL sin depender del orden del query string. */
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

    /** Decide si el mensaje recibido amerita rehidratar el flujo de matricula desde backend. */
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

    /** Recompone la sesion despues de un pago o de una reentrada al proceso. */
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
                                "Responde " + CMD_MENU + " para continuar."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
                actions.add(textMsg(feedbackSurveyText()));
                return true;
            }

            session.state = ChatState.SEDE_SELECTION;
            actions.add(textMsg(
                    "Contrato validado.\n\n" +
                            "Antes de finalizar, selecciona tu sede:\n\n" +
                            "1) Av. 1 de Mayo - Av. 1 de Mayo #68D-23 Piso 2\n" +
                            "2) El Eden - Local L2-094A\n\n" +
                            "Responde con 1 o 2."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
                                "Si no lo encuentras, responde 2 para recibirlo nuevamente.\n\n" +
                                "Cuando termines, responde 1 para activar la matrÃ­cula."
                ));
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU + ""));
                return true;
            }

            String expiryHint = buildContractExpiryHint(contractExpiresAt);
            actions.add(textMsg(
                    "Pago confirmado.\n\n" +
                            "Siguiente paso: firma tus contratos en este enlace:\n" + contractLink + "\n\n" +
                            expiryHint + "\n\n" +
                            "Cuando termines, responde 1 para activar la matrÃ­cula.\n" +
                            "Si necesitas el enlace nuevamente, responde 2."
            ));
        } else {
            actions.add(textMsg(
                    "Pago confirmado.\n\n" +
                            "Estamos generando tu enlace de contratos.\n" +
                            "En unos minutos te lo enviaremos por este chat."
            ));
        }
        actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        return true;
    }

    /** Controla el estado del paso en el que el usuario debe firmar contratos. */
    private void handleContractWait(String from, String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);

        switch (cmd) {
            case "1" -> {
                try {
                    boolean signed = procesoService.isContractSigned(session.documento);

                    if (!signed) {
                        actions.add(textMsg(
                                "â³ AÃºn no vemos el contrato firmado.\n\n" +
                                        "1) Abre el enlace del contrato\n" +
                                        "2) Firma el documento\n" +
                                        "3) Luego responde 1 nuevamente."
                        ));
                        actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
                        return;
                    }

                    session.state = ChatState.SEDE_SELECTION;

                    actions.add(textMsg(
                            "âœ… Contrato validado correctamente.\n\n" +
                                    "Ahora selecciona tu sede:\n\n" +
                                    "1) 1 de mayo\n" +
                                    "2) CC El EdÃ©n"
                    ));
                    actions.add(textMsg("Opciones: " + CMD_MENU));

                } catch (Exception e) {
                    actions.add(textMsg(
                            "âš ï¸ No pude validar el contrato.\n\n" +
                                    "Detalle: " + e.getMessage()
                    ));
                    actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
                }
            }
            case "2" -> {
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
                        actions.add(textMsg("âš ï¸ AÃºn no hay un enlace de contrato disponible."));
                    } else {
                        actions.add(textMsg(
                                "ðŸ“„ *Enlace de contrato*\n\n" +
                                        link + "\n\n" +
                                        buildContractExpiryHint(expiresAt)
                        ));
                    }

                    actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));

                } catch (Exception e) {
                    actions.add(textMsg("âš ï¸ No pude obtener el enlace del contrato."));
                    actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
                }
            }
            default -> {
                actions.add(textMsg(
                        "ðŸ“„ Debes completar la firma del contrato.\n\n" +
                                "Cuando termines, responde 1.\n" +
                                "Si necesitas el enlace nuevamente, responde 2."
                ));
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            }
        }
    }

    /** Traduce la opcion numerica de sede al valor que se persiste en el proceso. */
    private String resolveSedeSelection(String text) {
        String t = normalizeCommandText(text);
        if ("1".equals(t)) {
            return "Av. 1 de Mayo";
        }
        if ("2".equals(t)) {
            return "El Eden";
        }
        return "";
    }

        /** Crea el estudiante al final del flujo cuando la firma ya esta completa. */
        private void handleSedeSelection(String from, String text, SessionData session, List<BotAction> actions) {

        String sede = resolveSedeSelection(text);

        if (sede.isBlank()) {

            actions.add(textMsg(
                    "âš ï¸ Debes seleccionar una sede vÃ¡lida.\n\n" +
                    "1) Av. 1 de Mayo\n" +
                    "2) CC El EdÃ©n"
            ));

            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        session.sedeSeleccionada = sede;

        try {

            Long studentId =
                    procesoService.createStudentFromSignedContract(session.documento, sede);

            session.state = ChatState.DONE;

            actions.add(textMsg(
                    "ðŸŽ‰ *MatrÃ­cula finalizada correctamente*\n\n" +
                    "ðŸ“ Sede asignada: " + sede + "\n" +
                    "ðŸ†” Referencia estudiante: " + studentId + "\n\n" +
                    "Gracias por completar tu proceso con *CEA HARO*."
            ));
            actions.add(textMsg(feedbackSurveyText()));

            sessions.remove(from);

        } catch (Exception e) {

            actions.add(textMsg(
                    "âš ï¸ No pude crear el estudiante.\n\n" +
                    "Detalle: " + e.getMessage()
            ));

            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        }
    }

    /** Router principal del submenu de estudiante autenticado. */
    private void handleStudentMenu(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "ðŸ” Para continuar como estudiante, primero valida tu identidad.\n\n" +
                            "Escribe tu nÃºmero de documento (solo nÃºmeros) y te enviaremos un OTP."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        if (!session.studentOtpVerified) {
            session.state = ChatState.STUDENT_OTP_VERIFY;
            actions.add(textMsg(
                    "ðŸ” Aun falta validar tu OTP para ingresar al menu de estudiante.\n\n" +
                            "Escribe el codigo OTP enviado al correo " + maskEmail(session.studentEmail) + "."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        String cmd = normalizeCommandText(text);
        switch (cmd) {
            case "1" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "2" -> {
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
                            "âš ï¸ No encuentro una sede asignada para tu matrÃ­cula, por lo que no puedo agendar prÃ¡cticas.\n\n" +
                                    "ComunÃ­cate con la academia para registrar tu sede y vuelve a intentarlo.\n\n" +
                                    "ðŸ“Œ Datos registrados:\n" +
                                    "ðŸªª CategorÃ­a: " + categoria + "\n" +
                                    "ðŸ« Sede asignada: " + sede
                    ));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                actions.add(textMsg(practicalProcessText()));
                if (dual) {
                    session.state = ChatState.STUDENT_BOOKING_TYPE;
                    actions.add(textMsg(
                            "ðŸš˜ Vamos a reservar tu prÃ¡ctica.\n\n" +
                                    "ðŸ“Œ Datos registrados:\n" +
                                    "ðŸªª CategorÃ­a: " + categoria + "\n" +
                                    "ðŸ« Sede asignada: " + sede + "\n\n" +
                                    "âœ… Como tienes prÃ¡cticas para *carro* y *moto*, elige quÃ© deseas agendar:\n" +
                                    "1) Carro\n" +
                                    "2) Moto\n\n" +
                                    "âœï¸ Responde 1 o 2."
                     ));
                 } else {
                    session.state = ChatState.STUDENT_BOOKING_SLOT;
                    if ("carro".equals(tipoPase) || "moto".equals(tipoPase)) {
                        session.studentBookingTipoPase = tipoPase;
                    }
                    actions.add(textMsg(
                            "ðŸš˜ Vamos a reservar tu prÃ¡ctica.\n\n" +
                                    "ðŸ“Œ Datos registrados:\n" +
                                    "ðŸªª CategorÃ­a: " + categoria + "\n" +
                                    "ðŸ« Sede asignada: " + sede + "\n\n" +
                                    "Pasos a seguir:\n" +
                                    "1) Escribe la fecha de la clase.\n" +
                                    "2) Puedes escribir: hoy, maÃ±ana o YYYY-MM-DD.\n" +
                                    "3) DespuÃ©s te mostrarÃ© opciones de hora para elegir.\n\n" +
                                    "Ejemplos de fecha: hoy | maÃ±ana | 2026-03-15"
                    ));
                }
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "3" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "4" -> {
                List<Clase> agenda = procesoService.listUpcomingClassesForCancellationByStudentId(session.studentId);
                if (agenda.isEmpty()) {
                    actions.add(textMsg(
                            "â„¹ï¸ No tienes clases futuras para cancelar.\n\n" +
                                     "Si agendas una nueva clase, podrÃ¡s verla aquÃ­."
                    ));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }

                session.pendingStudentAction = StudentAction.NONE;
                session.state = ChatState.STUDENT_CANCEL_CLASS_PICK;
                actions.add(textMsg(
                        "ðŸ›‘ Estas son tus clases futuras:\n\n" +
                                formatCancelableClasses(agenda) + "\n\n" +
                                 "Escribe el ID de la clase que deseas cancelar.\n" +
                                 "Regla: con menos de 48 horas se aplica multa."
                ));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
            case "5" -> {
                resetToMain(session); // no cierra sesion de estudiante
                actions.add(textMsg(mainMenuText()));
                actions.add(textMsg("â„¹ï¸ Tu sesiÃ³n de estudiante sigue activa. Para volver al menÃº de estudiante, responde 4."));
            }
            case "6" -> {
                session.pendingStudentLogoutReturnState = ChatState.STUDENT_MENU;
                session.state = ChatState.STUDENT_LOGOUT_CONFIRM;
                actions.add(textMsg(studentLogoutConfirmText(session.studentNombre)));
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU + ""));
            }
            default -> {
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg(studentNavigationOptionsText()));
            }
        }
    }

    /** Captura el documento del estudiante y dispara el OTP al correo registrado. */
    private void handleStudentDocCapture(String text, SessionData session, List<BotAction> actions) {
        String doc = trim(text).replaceAll("\\D+", "");
        if (!DOCUMENT_PATTERN.matcher(doc).matches()) {
            actions.add(textMsg(
                    "âš ï¸ Documento invÃ¡lido.\n\n" +
                            "Pasos para corregirlo:\n" +
                            "1) Escribe solo nÃºmeros.\n" +
                            "2) Usa entre 5 y 20 dÃ­gitos.\n" +
                            "3) EnvÃ­alo de nuevo en un solo mensaje.\n\n" +
                            "Ejemplo: 12345678"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
                            "âš ï¸ No pude enviar el cÃ³digo OTP al correo " + access.emailMasked() + ".\n\n" +
                                    "Intenta nuevamente en unos minutos o contacta un asesor si el problema persiste.\n\n" +
                                    "Detalle: " + firstNotBlank(msg, e.getClass().getSimpleName())
                    ));
                    actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
                    ? ("ðŸ” Ya existe un cÃ³digo OTP reciente enviado al correo " + access.emailMasked() + ".\n\n" +
                       (cooldownSeconds != null
                               ? ("Espera " + cooldownSeconds + "s para solicitar otro cÃ³digo si lo necesitas.\n\n")
                               : "Espera un momento antes de solicitar otro cÃ³digo.\n\n"))
                    : ("ðŸ” Listo. Te enviamos un cÃ³digo OTP al correo " + access.emailMasked() + ".\n\n");

            actions.add(textMsg(
                    otpIntro +
                            "Pasos a seguir:\n" +
                            "1) Abre tu correo (revisa tambiÃ©n Spam o No deseado).\n" +
                            "2) Busca el mensaje con tu cÃ³digo OTP.\n" +
                            "3) Copia solo los nÃºmeros del cÃ³digo.\n" +
                            "4) EscrÃ­belo aquÃ­ en el chat.\n\n" +
                             "â±ï¸ Si no llega de inmediato, espera hasta 1 minuto."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        } catch (Exception e) {
            actions.add(textMsg("âš ï¸ No pude iniciar verificaciÃ³n OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        }
    }

    /** Verifica el OTP y habilita el acceso al menu del estudiante. */
    private void handleStudentOtpVerify(String text, SessionData session, List<BotAction> actions) {
        String code = trim(text);
        if (!OTP_PATTERN.matcher(code).matches()) {
            actions.add(textMsg(
                    "âš ï¸ Ese cÃ³digo no es vÃ¡lido.\n\n" +
                            "Pasos para enviarlo bien:\n" +
                            "1) EnvÃ­a solo nÃºmeros.\n" +
                            "2) No uses letras ni espacios.\n" +
                            "3) No agregues puntos ni sÃ­mbolos.\n\n" +
                            "Ejemplo: 123456"
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
            return;
        }

        try {
            boolean ok = verificationService.verifyEmailOtp(session.studentEmail, code);
            if (!ok) {
                actions.add(textMsg(
                        "âŒ El cÃ³digo es incorrecto o ya venciÃ³.\n\n" +
                                "Pasos para pedir uno nuevo:\n" +
                                "1) Escribe tu documento nuevamente.\n" +
                                "2) Te enviaremos un nuevo OTP (si aplica cooldown, espera unos segundos).\n" +
                                "3) Escribe el nuevo OTP aquÃ­."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU + ""));
                return;
            }

            actions.add(textMsg(studentGreetingText(session.studentNombre)));
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            session.studentOtpVerified = true;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg(studentNavigationOptionsText()));
        } catch (Exception e) {
            actions.add(textMsg("âš ï¸ No pude validar OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
        }
    }

    /** Permite escoger el tipo de pase antes de consultar o reservar. */
    private void handleStudentBookingType(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "ðŸ” Tu sesion de estudiante expirÃ³ o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
        if ("1".equals(cmd)) {
            choice = "carro";
        } else if ("2".equals(cmd)) {
            choice = "moto";
        }

        if (choice.isBlank()) {
            String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
            String sede = firstNotBlank(trim(session.studentSede), "No asignada");
            actions.add(textMsg(
                    "âš ï¸ No entendÃ­ tu elecciÃ³n.\n\n" +
                            "ðŸ“Œ Datos registrados:\n" +
                            "ðŸªª CategorÃ­a: " + categoria + "\n" +
                            "ðŸ« Sede asignada: " + sede + "\n\n" +
                            "Elige quÃ© deseas agendar:\n" +
                            "1) Carro\n" +
                            "2) Moto\n\n" +
                            "âœï¸ Responde 1 o 2."
            ));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        session.studentBookingTipoPase = choice;
        session.studentBookingDate = null;
        session.state = ChatState.STUDENT_BOOKING_SLOT;

        String categoria = firstNotBlank(trim(session.studentCategoria), "No registrada");
        String sede = firstNotBlank(trim(session.studentSede), "No asignada");
        String choiceLabel = "carro".equals(choice) ? "CARRO" : "MOTO";
        actions.add(textMsg(
                "âœ… Listo. Agendaremos prÃ¡ctica de *" + choiceLabel + "*.\n\n" +
                        "ðŸ“Œ Datos registrados:\n" +
                        "ðŸªª CategorÃ­a: " + categoria + "\n" +
                        "ðŸ« Sede asignada: " + sede + "\n\n" +
                        "Ahora escribe la fecha de la clase.\n" +
                        "Puedes escribir: hoy, maÃ±ana o YYYY-MM-DD.\n\n" +
                        "Ejemplos: hoy | maÃ±ana | 2026-03-15"
        ));
        actions.add(textMsg(studentNavigationOptionsText()));
    }

    /** Procesa la fecha y la hora de una reserva de clase practica. */
    private void handleStudentBookingSlot(String rawText, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "ðŸ” Tu sesion de estudiante expirÃ³ o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
            // Compatibilidad: si el usuario envÃ­a fecha y hora juntas, tambiÃ©n se procesa.
            BookingSlot fullSlot = parseBookingSlot(trimmed);
            if (fullSlot != null) {
                try {
                    LocalDate fecha = LocalDate.parse(fullSlot.fecha());
                    LocalTime hora = LocalTime.parse(fullSlot.hora());
                    completeStudentBooking(fecha, hora, session, actions);
                    return;
                } catch (Exception e) {
                    actions.add(textMsg("âš ï¸ No pude agendar la clase: " + e.getMessage()));
                    actions.add(textMsg(studentNavigationOptionsText()));
                    return;
                }
            }

            LocalDate fecha = parseBookingDate(trimmed);
            if (fecha == null) {
                actions.add(textMsg(
                        "âš ï¸ No entendÃ­ la fecha.\n\n" +
                                "Pasos para enviarla bien:\n" +
                                "1) Escribe hoy o maÃ±ana.\n" +
                                "2) O usa formato YYYY-MM-DD.\n" +
                                "3) EnvÃ­ala en un solo mensaje.\n\n" +
                                "Ejemplos: hoy | maÃ±ana | 2026-03-15"
                ));
                actions.add(textMsg(studentNavigationOptionsText()));
                return;
            }

            if (fecha.isBefore(LocalDate.now())) {
                actions.add(textMsg(
                        "âš ï¸ La fecha no puede ser pasada.\n\n" +
                                "Escribe hoy, maÃ±ana o una fecha futura."
                ));
                actions.add(textMsg(studentNavigationOptionsText()));
                return;
            }

            session.studentBookingDate = fecha.toString();
            actions.add(textMsg(
                    "ðŸ“… Fecha registrada: " + fecha + "\n\n" +
                            bookingSlotsAvailabilityText(session, fecha)
            ));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        LocalDate fecha = LocalDate.parse(session.studentBookingDate);
        LocalTime hora = parseBookingTime(trimmed);
        if (hora == null) {
            actions.add(textMsg(
                    "âš ï¸ No entendÃ­ la hora.\n\n" +
                            "Pasos para enviarla bien:\n" +
                            "1) Escribe un nÃºmero del 1 al 6.\n" +
                            "2) O escribe la hora en formato HH:mm.\n\n" +
                            "Ejemplos: 2 | 08:30"
            ));
            actions.add(textMsg(bookingSlotsAvailabilityText(session, fecha)));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        try {
            completeStudentBooking(fecha, hora, session, actions);
        } catch (Exception e) {
            actions.add(textMsg(
                    "âš ï¸ No pude agendar la clase: " + e.getMessage() + "\n\n" +
                            bookingSlotsAvailabilityText(session, fecha)
            ));
            actions.add(textMsg(studentNavigationOptionsText()));
        }
    }

    /** Permite escoger una clase ya agendada para cancelarla. */
    private void handleStudentCancelClassPick(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null || !session.studentOtpVerified) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "ðŸ” Tu sesion de estudiante expirÃ³ o no esta verificada.\n\n" +
                            "Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: " + CMD_MENU + ""));
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
                actions.add(textMsg("â„¹ï¸ Ya no hay clases futuras para cancelar."));
                actions.add(textMsg(studentNavigationOptionsText()));
                return;
            }

            actions.add(textMsg(
                    "âš ï¸ Debes escribir el ID de la clase a cancelar.\n\n" +
                            "Clases futuras:\n\n" +
                            formatCancelableClasses(agenda) + "\n\n" +
                            "Ejemplo: 123"
            ));
            actions.add(textMsg(studentNavigationOptionsText()));
            return;
        }

        try {
            Long idClase = Long.parseLong(input);
            ChatbotProcesoService.CancellationResult result =
                    procesoService.cancelPracticalClassByStudentId(session.studentId, idClase);

            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;

            StringBuilder out = new StringBuilder();
            out.append("âœ… Clase cancelada correctamente.\n\n")
                    .append("ID: ").append(result.clase().getId()).append("\n")
                    .append("Fecha: ").append(result.clase().getFecha()).append("\n")
                    .append("Inicio: ").append(result.clase().getHoraInicio());

            if (result.multaAplicada()) {
                out.append("\n\nâš ï¸ Se aplicÃ³ multa por cancelar con menos de 48 horas.\n")
                        .append("Valor multa: $").append(formatMoney(result.valorMulta())).append("\n")
                        .append("Tiempo restante al cancelar: ").append(result.horasRestantes()).append(" horas\n")
                        .append("Multas acumuladas: $").append(formatMoney(result.multasAcumuladas()));
            } else {
                out.append("\n\nâœ… Cancelaste con 48 horas o mÃ¡s. No se aplicÃ³ multa.");
            }

            actions.add(textMsg(out.toString()));
            actions.add(textMsg(studentNavigationOptionsText()));
        } catch (Exception e) {
            actions.add(textMsg("âš ï¸ No pude cancelar la clase: " + e.getMessage()));
            actions.add(textMsg(studentNavigationOptionsText()));
        }
    }

    /** Confirma el cierre de sesion del estudiante autenticado. */
    private void handleStudentLogoutConfirm(String text, SessionData session, List<BotAction> actions) {
        String cmd = normalizeCommandText(text);
        if ("si".equals(cmd) || "1".equals(cmd) || "confirmar".equals(cmd)) {
            ChatState returnState = session.pendingStudentLogoutReturnState;
            session.pendingStudentLogoutReturnState = null;

            clearStudentAccessData(session);

            // Si el usuario estaba en el subflujo de estudiante, lo llevamos al menu principal.
            if (isStudentState(returnState)) {
                resetToMain(session);
                actions.add(textMsg("âœ… Sesion de estudiante cerrada."));
                actions.add(textMsg(mainMenuText()));
                return;
            }

            // Si estaba en otro flujo, solo cerramos sesion y continuamos donde iba.
            if (returnState != null) {
                session.state = returnState;
            } else {
                resetToMain(session);
            }
            actions.add(textMsg("âœ… Sesion de estudiante cerrada."));
            if (session.state == ChatState.MAIN_MENU) {
                actions.add(textMsg(mainMenuText()));
            } else {
                actions.add(textMsg("â„¹ï¸ Continuemos con el paso actual."));
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
                actions.add(textMsg("âœ… Listo. Sigamos con tu reserva.\n\nEscribe la fecha: hoy | maÃ±ana | YYYY-MM-DD"));
                actions.add(textMsg(studentNavigationOptionsText()));
            } else if (session.state == ChatState.STUDENT_CANCEL_CLASS_PICK && session.studentOtpVerified) {
                actions.add(textMsg("âœ… Listo. Sigamos con la cancelacion.\n\nEscribe el ID de la clase que deseas cancelar."));
                actions.add(textMsg(studentNavigationOptionsText()));
            } else {
                actions.add(textMsg("âœ… Perfecto. Continuemos."));
            }
            return;
        }

        actions.add(textMsg(studentLogoutConfirmText(session.studentNombre)));
        actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU + ""));
    }

    /** Confirma si el usuario realmente quiere abortar o salir del proceso actual. */
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
                    "âœ… Proceso de matricula terminado.\n\n" +
                            "Si deseas iniciar de nuevo, responde 2 en el menÃº principal.\n\n" +
                            mainMenuText()
            ));
            return;
        }

        if ("no".equals(cmd) || "2".equals(cmd) || isStudentBackCommand(cmd)) {
            session.pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;
            session.pendingEnrollmentAbortReturnState = null;

            ChatState resume = returnState != null ? returnState : ChatState.MAIN_MENU;
            session.state = resume;
            actions.add(textMsg("âœ… Perfecto. Continuemos con tu matricula."));
            renderEnrollmentResumePrompt(resume, session, actions);
            return;
        }

        actions.add(textMsg(enrollmentAbortConfirmText(action == EnrollmentAbortAction.END_CONVERSATION)));
        actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU + ""));
    }

    /** Genera el mensaje de reanudacion cuando el usuario retoma una matricula incompleta. */
    private void renderEnrollmentResumePrompt(ChatState state, SessionData session, List<BotAction> actions) {
        if (state == null) {
            actions.add(textMsg(mainMenuText()));
            return;
        }

        switch (state) {
            case ENROLLMENT_DATA_AUTH_WAIT -> {
                addEnrollmentIntro(actions);
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            }
            case ENROLLMENT_CAPTURE -> {
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("DespuÃ©s de ese primer mensaje te mostrarÃ© un menÃº para elegir la categorÃ­a, y luego te pedirÃ© tu edad, correo, telÃ©fono, direcciÃ³n y la sede."));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_CATEGORY_SELECT -> {
                actions.add(textMsg(enrollmentCategoryMenuText()));
                actions.add(textMsg("Opciones: 1 | 2 | 3 | 4 | 5 | " + CMD_MENU));
            }
            case ENROLLMENT_AGE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 2 de 8: envÃ­a tu edad en aÃ±os.\n\n" +
                                "Ejemplo: 18\n\n" +
                                "Importante: debes tener mÃ­nimo 16 aÃ±os para matricularte."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_EMAIL_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 3 de 8: envia tu correo electronico.\n" +
                                "Ejemplo: usuario@correo.com"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_PHONE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 4 de 8: envia tu telefono de contacto.\n" +
                                "Ejemplo: 3001112233 (sin +57)"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_ADDRESS_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 5 de 8: envia tu direccion de residencia.\n\n" +
                                "Escribela completa con barrio, nomenclatura o apartamento si aplica.\n\n" +
                                "Ejemplo: Cra 80 #12-45 Apto 302, Av. 1 de Mayo, Bogota"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_SEDE_CAPTURE -> {
                actions.add(textMsg(
                        "Paso 6 de 8: selecciona tu sede.\n\n" +
                                "1) Av. 1 de Mayo - Av. 1 de Mayo #68D-23 Piso 2\n" +
                                "2) El Eden - Local L2-094A\n\n" +
                                "Responde con 1 o 2."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case ENROLLMENT_CONFIRM -> {
                actions.add(textMsg(
                        "Paso 7 de 8: revisa tus datos y confirma.\n\n" +
                                "Nombre: " + safe(session.nombre) + "\n" +
                                "Documento: " + safe(session.documento) + "\n" +
                                "Categoria: " + safe(session.categoria) + "\n" +
                                "Edad: " + (session.edad == null ? "N/A" : (session.edad + " aÃ±os")) + "\n" +
                                "Correo: " + safe(session.email) + "\n" +
                                "Telefono: " + safe(session.telefono) + "\n" +
                                "Direccion: " + safe(session.direccion) + "\n" +
                                "Sede: " + safe(session.sedeSeleccionada) + "\n\n" +
                                "Responde:\n" +
                                "1) Confirmar y continuar\n" +
                                "2) Corregir datos\n" +
                                "3) Cancelar"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case PAYMENT_METHOD_SELECT -> {
                actions.add(textMsg(
                        "Paso 8 de 8: selecciona tu metodo de pago.\n\n" +
                                "1) Pagar por ePayco (en linea)\n" +
                                "2) Pagar en efectivo en la academia\n\n" +
                                "Responde 1 o 2."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case PAYMENT_PLAN_SELECT -> {
                actions.add(textMsg(
                        "Elige como deseas pagar por ePayco:\n\n" +
                                "1) Pagar completo (100%)\n" +
                                "2) Pagar por la mitad (50%)\n\n" +
                                "Responde 1 o 2."
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            case PAYMENT_WAIT -> {
                actions.add(textMsg(
                        "ðŸ’³ Estamos esperando la confirmacion de tu pago.\n\n" +
                                "Responde:\n" +
                                "1) Ver enlace de pago\n" +
                                "2) Ya paguÃ©"
                ));
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            }
            case PAYMENT_CASH_WAIT -> {
                actions.add(textMsg(
                        "â³ Estamos esperando la confirmacion del pago en efectivo.\n\n" +
                                "Cuando se confirme, te enviaremos por este chat el enlace para firmar los contratos.\n\n" +
                                "Responde 1 para ver el estado."
                ));
                actions.add(textMsg("Opciones: 1 | " + CMD_MENU));
            }
            case CONTRACT_WAIT -> {
                actions.add(textMsg(
                        "ðŸ“ Estamos esperando que firmes tus contratos.\n\n" +
                                "Cuando termines, responde 1 para continuar.\n" +
                                "Si necesitas el enlace, responde 2."
                ));
                actions.add(textMsg("Opciones: 1 | 2 | " + CMD_MENU));
            }
            case SEDE_SELECTION -> {
                actions.add(textMsg(
                        "ðŸ“ Selecciona tu sede:\n\n" +
                                "1) Av. 1 de Mayo\n" +
                                "2) CC El EdÃ©n"
                ));
                actions.add(textMsg("Opciones: " + CMD_MENU));
            }
            default -> actions.add(textMsg("â„¹ï¸ Continuemos. Responde con la informacion solicitada en este paso."));
        }
    }

    // =========================
    // LINKS / PARSING / FORMAT
    // =========================

    // Zona horaria usada para mostrar vigencia de enlaces de contrato.
    /**
     * Zona horaria usada para calcular y mostrar vencimientos contractuales.
     */
    private static final ZoneId CONTRACT_ZONE = ZoneId.of("America/Bogota");
    /**
     * Formato usado para mostrar la expiracion del enlace contractual.
     */
    private static final DateTimeFormatter CONTRACT_EXPIRES_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CONTRACT_ZONE);

    /** Representa el enlace final del usuario y su expiraciÃ³n. */
    private record ContractUserLink(String url, Instant expiresAt) {}
    /** Contiene los datos mÃ­nimos requeridos para validar acceso al contrato. */
    private record ContractAccessParams(String email, String code) {}

    /** Genera un enlace nuevo de contratos y lo registra sobre el proceso activo. */
    private ContractUserLink createAndStoreContractLink(String documento) {
        ChatbotMatriculaProceso proceso = procesoService.findProcesoByDocumento(documento)
                .orElseThrow(() -> new IllegalStateException("No existe proceso de matrÃ­cula para generar contrato"));

        VerificationService.ContractLinkResult out = verificationService.createContractVerificationLink(
                proceso.getEmail(),
                contractBaseUrl
        );
        String userLink = buildContractUserLink(out);
        procesoService.markContractLinkSent(documento, userLink);
        return new ContractUserLink(userLink, out == null ? null : out.expiresAt());
    }

    /** Verifica si un enlace existente sigue siendo vÃ¡lido sin consumirlo. */
    private VerificationService.ContractAccessResult peekContractLinkAccess(String contractLinkRaw) {
        ContractAccessParams params = parseContractAccessParams(contractLinkRaw);
        if (params.email().isBlank() || params.code().isBlank()) {
            return new VerificationService.ContractAccessResult(false, "Codigo requerido", null);
        }
        return verificationService.peekContractAccessCode(params.email(), params.code());
    }

    /** Extrae email y cÃ³digo desde la URL del contrato. */
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

    /** Lee un parÃ¡metro puntual del query string sin depender del frontend. */
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

    /** Devuelve el texto que informa la vigencia del enlace enviado al usuario. */
    private String buildContractExpiryHint(Instant expiresAt) {
        if (expiresAt == null) {
            return "â³ Este enlace es temporal. Si se vence, responde 2 para generar otro.";
        }
        String until = CONTRACT_EXPIRES_FMT.format(expiresAt);
        return "â³ Vigente hasta: " + until + " (hora Colombia). Si se vence, responde 2 para generar otro.";
    }

    /** Convierte el resultado del backend en el enlace final que abre el HTML de contratos. */
    private String buildContractUserLink(VerificationService.ContractLinkResult out) {
        if (out == null) return "";
        String ui = resolveContractUiUrl(out);
        if (ui.isBlank()) return trim(out.url());
        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        if (looksLikeBackendBaseUrl(contractBaseUrl)) link = appendQueryParam(link, "apiBase", contractBaseUrl);
        return link;
    }

    /** Resuelve la URL pÃºblica del frontend de contratos a partir de la configuraciÃ³n disponible. */
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

    /** Distingue entre una URL del backend y una URL del sitio web pÃºblico. */
    private boolean looksLikeBackendBaseUrl(String raw) {
        String v = trim(raw).toLowerCase(java.util.Locale.ROOT);
        if (v.isBlank()) return false;
        return v.contains("run.app")
                || v.contains("localhost")
                || v.matches(".*:\\d{2,5}$");
    }

    /** Decide si el enlace guardado debe regenerarse por expiraciÃ³n o desalineaciÃ³n de UI. */
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

    /** Normaliza la ruta esperada del HTML de contratos. */
    private String normalizeContractUiUrl(String rawUiUrl) {
        String ui = trim(rawUiUrl);
        if (ui.isBlank()) {
            return ui;
        }
        return ui.replaceFirst("(?i)(?:/Contratos)*/contrato\\.html(?=($|[?#]))", "/Contratos/contrato.html");
    }

    /** Aplica la misma normalizaciÃ³n al enlace persistido. */
    private String normalizeStoredContractLink(String rawContractLink) {
        return normalizeContractUiUrl(trim(rawContractLink));
    }

    /** AÃ±ade parÃ¡metros al enlace final conservando el query ya existente. */
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

    /** Interpreta la captura libre con nombre, documento y categorÃ­a. */
    private EnrollmentBasic parseEnrollmentBasic(String rawText) {
        Matcher matcher = ENROLLMENT_BASIC_PATTERN.matcher(trim(rawText));
        if (!matcher.matches()) return null;

        String nombre = collapseSpaces(matcher.group(1));
        if (containsDigit(nombre)) return null;

        String documento = trim(matcher.group(2)).replaceAll("\\D+", "");
        if (!DOCUMENT_PATTERN.matcher(documento).matches()) return null;

        String categoria = normalizeCategory(matcher.group(3));

        if (nombre.length() < 3) return null;
        return new EnrollmentBasic(nombre, documento, categoria);
    }

    /** Interpreta la variante guiada donde la categorÃ­a llega como opciÃ³n numÃ©rica. */
    private EnrollmentBasic parseEnrollmentBasicOption(String rawText) {
        Matcher matcher = ENROLLMENT_NAME_DOC_OPTION_PATTERN.matcher(trim(rawText));
        if (!matcher.matches()) return null;

        String nombre = collapseSpaces(matcher.group(1));
        if (containsDigit(nombre)) return null;

        String documento = trim(matcher.group(2)).replaceAll("\\D+", "");
        if (!DOCUMENT_PATTERN.matcher(documento).matches()) return null;

        String opt = matcher.group(3);

        if (nombre.length() < 3) return null;
        String categoria = switch (opt) {
            case "1" -> "A2";
            case "2" -> "B1";
            case "3" -> "C1";
            case "4" -> "A2 y B1";
            case "5" -> "A2, B1 y C1";
            default -> "";
        };
        if (categoria.isBlank()) return null;
        return new EnrollmentBasic(nombre, documento, categoria);
    }

    /** Extrae solo nombre y documento cuando la categorÃ­a se solicita aparte. */
    private EnrollmentNameDoc parseEnrollmentNameDoc(String rawText) {
        Matcher matcher = ENROLLMENT_NAME_DOC_PATTERN.matcher(trim(rawText));
        if (!matcher.matches()) return null;

        String nombre = collapseSpaces(matcher.group(1));
        if (containsDigit(nombre)) return null;

        String documento = trim(matcher.group(2)).replaceAll("\\D+", "");
        if (!DOCUMENT_PATTERN.matcher(documento).matches()) return null;

        if (nombre.length() < 3) return null;
        return new EnrollmentNameDoc(nombre, documento);
    }

    /** Valida un mensaje que trae fecha y hora para agendamiento. */
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

    /** Convierte una fecha ingresada por el usuario en LocalDate. */
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

    /** Traduce opciones numÃ©ricas de horario o una hora escrita manualmente. */
    private LocalTime parseBookingTime(String rawText) {
        String n = normalizeCommandText(rawText);
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

    /** Intenta leer una hora manual en formato H:mm. */
    private LocalTime parseHourValue(String rawText) {
        String value = trim(rawText);
        if (value.isBlank()) return null;
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Construye el resumen de disponibilidad de una fecha especÃ­fica. */
    private String bookingSlotsAvailabilityText(SessionData session, LocalDate fecha) {
        try {
            Long studentId = session == null ? null : session.studentId;
            String tipoPase = session == null ? null : session.studentBookingTipoPase;
            if (studentId == null) {
                return "âš ï¸ No pude identificar el estudiante para consultar disponibilidad. Responde " + CMD_MENU + " e ingresa de nuevo como estudiante.";
            }
            List<ChatbotProcesoService.SlotAvailability> slots =
                    procesoService.listPracticalSlotAvailabilityByStudentId(studentId, fecha, tipoPase);
            if (slots.isEmpty()) {
                return "No hay horarios disponibles para esa fecha.\nEscribe otra fecha para consultar.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Disponibilidad del dÃ­a:\n");
            boolean anyAvailable = false;
            for (ChatbotProcesoService.SlotAvailability slot : slots) {
                boolean available = slot.disponible();
                if (available) anyAvailable = true;
                sb.append(slot.opcion())
                        .append(") ")
                        .append(slot.hora())
                        .append(" ")
                        .append(available ? "âœ… Disponible" : "âŒ Ocupada")
                        .append("\n");
            }

            if (anyAvailable) {
                sb.append("\nEscribe el nÃºmero de una hora disponible o envÃ­a una hora en formato HH:mm.");
            } else {
                sb.append("\nâš ï¸ Todas las horas estÃ¡n ocupadas. Escribe otra fecha.");
            }
            return sb.toString();
        } catch (Exception e) {
            return "âš ï¸ No pude consultar disponibilidad en este momento: " + e.getMessage();
        }
    }

    /** Formatea las clases futuras que el estudiante puede cancelar. */
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

    /** Muestra valores monetarios sin decimales innecesarios. */
    private String formatMoney(java.math.BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    /** Finaliza el flujo de agendamiento y devuelve el resumen al estudiante. */
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

        String verifyHint = "âœ… Para verificar que tu clase quedÃ³ agendada:\n"
                + "1) Responde 1 para consultar tu calendario de prÃ¡cticas.\n"
                + "3) Responde 3 para consultar tu horario.\n\n"
                + (manualCalendar
                ? "â„¹ï¸ Nota: la cita en Google Calendar quedÃ³ pendiente. Si necesitas ayuda, responde 9 y luego 5 para contactar un asesor."
                : "ðŸ“© Revisa tu correo: te llegarÃ¡ la invitaciÃ³n de Google Calendar.");

        actions.add(textMsg(verifyHint));
        actions.add(textMsg(studentMenuText()));
        actions.add(textMsg(studentNavigationOptionsText()));
    }

    /** Genera un texto legible con la agenda actual del estudiante. */
    private String formatAgenda(String documento, List<Clase> agenda) {
        if (agenda == null || agenda.isEmpty()) {
            return "ðŸ“… No encontramos clases programadas para el documento " + documento + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ðŸ“… Agenda de clases para documento ").append(documento).append(":\n\n");

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

    /** Elimina sesiones vencidas y recorta el mapa cuando supera el lÃ­mite permitido. */
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

    /** Regresa la conversaciÃ³n al menÃº principal conservando solo lo imprescindible. */
    private void resetToMain(SessionData session) {
        session.state = ChatState.MAIN_MENU;
        session.courseOptionsExpanded = false;
        clearEnrollmentData(session);
        session.pendingEnrollmentAbortReturnState = null;
        session.pendingEnrollmentAbortAction = EnrollmentAbortAction.NONE;
    }

    /** Regresa al menÃº principal y limpia tambiÃ©n el acceso del estudiante autenticado. */
    private void resetToMainAndClearStudent(SessionData session) {
        resetToMain(session);
        clearStudentAccessData(session);
    }

    /** Limpia los datos temporales del proceso de matrÃ­cula. */
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

    /** Limpia la identidad y el contexto operativo del estudiante autenticado. */
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
    /** Resume al estudiante cÃ³mo funciona el proceso de clases prÃ¡cticas. */
    private String practicalProcessText() {
        return "Proceso practico:\n" +
                "1) Tu pago debe estar aprobado y tu contrato firmado.\n" +
                "2) Ingresas como estudiante y validas tu identidad con OTP.\n" +
                "3) Eliges fecha y luego ves solo horas con profesor y vehiculo disponibles.\n" +
                "4) Cada practica dura " + Math.max(30, bookingDurationMinutes) + " minutos.\n" +
                "5) Si cancelas con menos de " + Math.max(1, bookingCancelMinHours) + " horas, se aplica multa.";
    }

    /** Detecta el comando global para volver al menÃº principal. */
    private boolean isMenuCommand(String text) {
        return CMD_MENU.equals(normalizeCommandText(text));
    }

    /** Detecta el comando para regresar dentro del flujo de estudiante. */
    private boolean isStudentBackCommand(String text) {
        return CMD_BACK.equals(normalizeCommandText(text));
    }

    /** Indica si el estado actual pertenece al mÃ³dulo de estudiantes. */
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

    /** Define en quÃ© pasos conviene pedir confirmaciÃ³n antes de abortar matrÃ­cula. */
    private boolean shouldConfirmEnrollmentAbort(ChatState state) {
        if (state == null) return false;
        return switch (state) {
            case ENROLLMENT_DATA_AUTH_WAIT,
                    ENROLLMENT_CAPTURE,
                    ENROLLMENT_CATEGORY_SELECT,
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

    /** EvalÃºa si el usuario autorizÃ³ el tratamiento de datos. */
    private boolean isEnrollmentDataAuthAccepted(String cmd) {
        return "si".equals(cmd) || "1".equals(cmd);
    }

    /** EvalÃºa si el usuario rechazÃ³ el tratamiento de datos. */
    private boolean isEnrollmentDataAuthRejected(String cmd) {
        return "no".equals(cmd) || "2".equals(cmd);
    }

    /** Normaliza texto libre para compararlo como comando del bot. */
    private String normalizeCommandText(String text) {
        String lower = trim(text).toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return "";
        }

        String withoutDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return collapseSpaces(withoutDiacritics.replaceAll("[^a-z0-9\\s]+", " "));
    }

    /** Revisa si la sesiÃ³n ya superÃ³ el tiempo mÃ¡ximo de inactividad. */
    private boolean isConversationExpired(SessionData session, Instant now) {
        if (session == null || now == null) return false;
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return now.isAfter(session.lastSeen.plus(Duration.ofMinutes(minutes)));
    }

    /** Mensaje estÃ¡ndar cuando la sesiÃ³n caduca por inactividad. */
    private String inactivityTimeoutText() {
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return "â° ConversaciÃ³n expirada por inactividad (" + minutes + " minutos).\n\n" +
                "Responde " + CMD_MENU + " para iniciar de nuevo.";
    }

    /** Mensaje estÃ¡ndar cuando la conversaciÃ³n termina de forma explÃ­cita. */
    private String conversationEndedText() {
        return "ðŸ‘‹ ConversaciÃ³n finalizada.\n\n" +
                "Gracias por escribir a CEA HARO.\n" +
                "Si deseas iniciar de nuevo, responde " + CMD_MENU + ".";
    }

    /** Invita al usuario a diligenciar la encuesta opcional de experiencia. */
    private String feedbackSurveyText() {
        return "ðŸ“‹ Encuesta (opcional)\n\n" +
                "Â¿Quieres contarnos cÃ³mo te fue en el proceso?\n" +
                "Tu respuesta nos ayuda a mejorar.\n\n" +
                "Formulario:\n" +
                FEEDBACK_FORM_URL;
    }

    /** Personaliza el saludo inicial del menÃº de estudiante. */
    private String studentGreetingText(String studentName) {
        String displayName = collapseSpaces(trim(studentName));
        if (displayName.isBlank()) {
            displayName = "estudiante";
        }
        return "ðŸ‘‹ Hola " + displayName + ", Â¿quÃ© quieres hacer hoy?";
    }

    /** Muestra los atajos de navegaciÃ³n vÃ¡lidos en el mÃ³dulo de estudiante. */
    private String studentNavigationOptionsText() {
        return "Opciones: " + CMD_BACK + " | " + CMD_MENU;
    }

    /** Construye la confirmaciÃ³n de cierre de sesiÃ³n del estudiante. */
    private String studentLogoutConfirmText(String studentName) {
        String displayName = collapseSpaces(trim(studentName));
        if (displayName.isBlank()) {
            displayName = "estudiante";
        }
        return "ðŸ”’ Cerrar sesion de estudiante\n\n" +
                "Sesion activa: " + displayName + "\n\n" +
                "Â¿Seguro que desea cerrar su sesion?\n" +
                "Si confirma, debera validar OTP nuevamente para ingresar como estudiante.\n\n" +
                "Responda: 1 (SI) o 2 (NO).";
    }

    /** Construye la confirmaciÃ³n para cancelar matrÃ­cula en curso. */
    private String enrollmentAbortConfirmText(boolean endsConversation) {
        if (endsConversation) {
            return "âš ï¸ Â¿Seguro que desea terminar su proceso de matricula y finalizar la conversacion?\n\n" +
                    "Su progreso no se guardara.\n\n" +
                    "Responda 1 (SI) para confirmar o 2 (NO) para continuar.";
        }
        return "âš ï¸ Â¿Seguro que desea terminar su proceso de matricula?\n\n" +
                "Su progreso no se guardara.\n\n" +
                "Responda 1 (SI) para confirmar o 2 (NO) para continuar.";
    }

    /** Devuelve el acceso rÃ¡pido al asesor con mensaje sugerido por defecto. */
    private String advisorContactText() {
        return advisorContactText(null);
    }

    /** Devuelve el acceso rÃ¡pido al asesor con un mensaje sugerido configurable. */
    private String advisorContactText(String predefinedMessage) {
        String suggestedMessage = buildAdvisorSuggestedMessage(predefinedMessage);
        String link = buildAdvisorLink(predefinedMessage);
        return "ðŸ’¬ Contactar asesor:\n" + link + "\n\n" +
                "Mensaje sugerido:\n" + suggestedMessage;
    }

    /** Genera el enlace de WhatsApp del asesor con el texto precargado. */
    private String buildAdvisorLink(String predefinedMessage) {
        String suggestedMessage = buildAdvisorSuggestedMessage(predefinedMessage);
        return ADVISOR_WHATSAPP_LINK + "?text=" + URLEncoder.encode(suggestedMessage, StandardCharsets.UTF_8);
    }

    /** Construye el mensaje base que el usuario enviarÃ¡ al asesor. */
    private String buildAdvisorSuggestedMessage(String predefinedMessage) {
        String normalized = collapseSpaces(predefinedMessage);
        String baseMessage = normalized.isBlank()
                ? "Necesito tu ayuda, por favor."
                : normalized;
        return "ðŸ¤–: " + baseMessage +
                " Por favor, no borrar el emoji del robot para tener en cuenta el origen de la solicitud.";
    }
    // =========================
    // PROSPECTOS (NO ESTUDIANTE)
    // =========================

    /** Registra consultas de prospectos sin afectar sesiones ya autenticadas como estudiante. */
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

    /** Traduce una categorÃ­a comercial al cÃ³digo interno usado en prospectos. */
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

    /** Normaliza texto libre para validaciones funcionales. */
    private String normalizeInput(String text) {
        String base = trim(text).toLowerCase(Locale.ROOT);
        if (base.isBlank()) return "";

        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return collapseSpaces(normalized);
    }

    /** Lleva mÃºltiples variantes de escritura a una categorÃ­a canÃ³nica. */
    private String normalizeCategory(String value) {
        String n = normalizeInput(value)
                .replace(" ", "")
                .replace("+", "y")
                .replace("/", "y")
                .replace(",", "y")
                .replace("-", "y");

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

    /** Detecta si un campo supuestamente alfabÃ©tico trae nÃºmeros. */
    private boolean containsDigit(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Reduce espacios repetidos y recorta extremos. */
    private String collapseSpaces(String value) {
        return trim(value).replaceAll("\\s+", " ");
    }

    /** Retorna el primer valor Ãºtil dentro de una lista de candidatos. */
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

    /** Evita nulos al recortar texto. */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Reemplaza nulos por cadena vacÃ­a para respuestas y logs. */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** Oculta parcialmente un telÃ©fono antes de enviarlo a logs. */
    private String maskPhone(String phone) {
        String digits = trim(phone).replaceAll("\\D+", "");
        if (digits.length() <= 4) return "****";
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /** Oculta parcialmente un correo antes de enviarlo a logs. */
    private String maskEmail(String email) {
        String e = trim(email).toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.charAt(0) + "***" + e.substring(at);
    }

    /** Crea una acciÃ³n de texto aplicando normalizaciÃ³n de salida. */
    private BotAction textMsg(String body) {
        return new BotAction("text", normalizeOutboundText(body), null, null, null);
    }

    /** Ajusta prefijos y formato visual de los mensajes salientes. */
    private String normalizeOutboundText(String textRaw) {
        String text = trim(textRaw);
        if (text.isBlank()) {
            return text;
        }

        String fixed = text;
        fixed = fixed.replaceAll("(?m)^No pude\\b", "âš ï¸ No pude");
        fixed = fixed.replaceAll("(?m)^Responde\\b", "âœï¸ Responde");
        fixed = fixed.replaceAll("(?m)^Opciones:\\s*", "\uD83D\uDCCC *Opciones:* ");
        return fixed;
    }

    /** Crea una acciÃ³n de imagen solo cuando existe una URL vÃ¡lida. */
    private BotAction imageMsg(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return new BotAction("image", imageUrl, null, null, null);
    }

    /** Inserta la introducciÃ³n visual y textual del flujo de matrÃ­cula. */
    private void addEnrollmentIntro(List<BotAction> actions) {
        if (includeImageActionInInbound) {
            BotAction image = imageMsg(enrollmentWelcomeImageUrl);
            if (image != null) {
                actions.add(image);
            }
        }
        actions.add(textMsg(
                "ðŸ‘‹ Â¡Gracias por elegir CEA HARO!\n\n" +
                        "Para brindarte un servicio personalizado, necesitamos algunos datos personales.\n" +
                        "Puedes revisar nuestra polÃ­tica de tratamiento de datos en www.ceaharo.com\n\n" +
                        "ðŸ” Â¿Autorizas el tratamiento de tus datos?\n" +
                        "Responde: 1 (SI) o 2 (NO)."
        ));
    }

    // =========================
    // TEXTOS (PLANTILLAS + QUITAR REPETICIÃ“N DE "CATEGORÃA ...")
    // =========================

    /** Plantilla del menÃº principal del bot. */
    private String mainMenuText() {
        return "ðŸ‘‹ Hola, soy Ha-Rot, asistente virtual de CEA HARO.\n\n" +
                "Â¿QuÃ© deseas hacer hoy?\n\n" +
                "1) Nuestros servicios (cursos, refuerzos y recategorizaciÃ³n)\n" +
                "2) Iniciar matrÃ­cula\n" +
                "3) Horarios de atenciÃ³n y sedes\n" +
                "4) Soy un estudiante (consultas y reservas)\n" +
                "5) Contactar un asesor\n" +
                "6) Continuar mi proceso (reenviar enlace de pago/contratos)\n\n" +
                "âœï¸ Responde con el nÃºmero de la opciÃ³n.";
    }

    /** Plantilla del menÃº de servicios y cursos disponibles. */
    private String coursesMenuText() {
        return "ðŸ§¾ Nuestros Servicios\n" +
                "Selecciona el servicio que deseas conocer o solicitar.\n\n" +
                "1) ðŸï¸ Licencia A2\n" +
                "2) ðŸš— Licencia B1\n" +
                "3) ðŸš• Licencia C1\n" +
                "4) ðŸï¸ðŸš— A2 y B1\n" +
                "5) ðŸï¸ðŸš—ðŸš• A2, B1 y C1\n" +
                "6) ðŸš˜ Clase de refuerzo - Carro\n" +
                "7) ðŸï¸ Clase de refuerzo - Moto\n" +
                "8) ðŸ”„ RecategorizaciÃ³n B1 a C1\n\n" +
                "âœï¸ Responde con un nÃºmero del 1 al 8.";
    }

    /** Variante del menÃº de servicios usada cuando se necesita reiterar todas las categorÃ­as. */
    private String allCategoriesMenuText() {
        return "ðŸ§¾ Nuestros Servicios\n" +
                "Selecciona el servicio que deseas conocer o solicitar.\n\n" +
                "1) ðŸï¸ CategorÃ­a A2\n" +
                "2) ðŸš— CategorÃ­a B1\n" +
                "3) ðŸš• CategorÃ­a C1\n" +
                "4) ðŸï¸ðŸš— CategorÃ­a A2 y B1\n" +
                "5) ðŸï¸ðŸš—ðŸš• CategorÃ­a A2, B1 y C1\n" +
                "6) ðŸš˜ Clase de refuerzo - Carro\n" +
                "7) ðŸï¸ Clase de refuerzo - Moto\n" +
                "8) ðŸ”„ RecategorizaciÃ³n B1 a C1\n\n" +
                "âœï¸ Responde con un nÃºmero del 1 al 8.";
    }

    // Quitado: "CategorÃ­a A2"
    /** Ficha comercial breve del curso A2. */
    private String courseA2Text() {
        return "ðŸï¸ *CategorÃ­a A2*\n\n" +
                "Para motocicletas de cualquier tipo de cilindraje.\n\n" +
                "ðŸ’° *Valor curso:* $920.000 (incluye examen mÃ©dico)\n" +
                "ðŸªª *Valor licencia:* $272.800 en ventanilla Ãºnica\n\n" +
                "âœ… *Incluye:*\n" +
                "â€¢ 25 horas de teorÃ­a ðŸ‘©â€ðŸ«\n" +
                "â€¢ 3 horas de taller ðŸ› \n" +
                "â€¢ 15 horas de prÃ¡ctica ðŸš¦\n" +
                "â€¢ Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    // Quitado: "CategorÃ­a B1"
    /** Ficha comercial breve del curso B1. */
    private String courseB1Text() {
        return "ðŸš— *CategorÃ­a B1*\n\n" +
                "Para vehÃ­culos de placa amarilla.\n\n" +
                "ðŸ’° *Valor curso:* $1.210.000 (incluye examen mÃ©dico)\n" +
                "ðŸªª *Valor licencia:* $329.900 en ventanilla Ãºnica\n\n" +
                "âœ… *Incluye:*\n" +
                "â€¢ 25 horas de teorÃ­a ðŸ‘©â€ðŸ«\n" +
                "â€¢ 5 horas de taller ðŸ› \n" +
                "â€¢ 20 horas de prÃ¡ctica (16 ciudad + 4 carretera) ðŸš˜\n" +
                "â€¢ Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    // Quitado: "CategorÃ­a C1"
    /** Ficha comercial breve del curso C1. */
    private String courseC1Text() {
        return "ðŸš• *CategorÃ­a C1*\n\n" +
                "Para vehÃ­culos de placa blanca y amarilla.\n\n" +
                "ðŸ’° *Valor curso:* $1.350.000 (incluye examen mÃ©dico)\n" +
                "ðŸªª *Valor licencia:* $329.900 en ventanilla Ãºnica\n\n" +
                "âœ… *Incluye:*\n" +
                "â€¢ 30 horas de teorÃ­a ðŸ‘©â€ðŸ«\n" +
                "â€¢ 5 horas de taller ðŸ› \n" +
                "â€¢ 30 horas de prÃ¡ctica (26 ciudad + 4 carretera) ðŸš˜\n" +
                "â€¢ Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    // Quitado: "CategorÃ­a A2 y B1"
    /** Ficha comercial breve del paquete A2 y B1. */
    private String courseA2B1Text() {
        return "ðŸï¸ðŸš— *CategorÃ­a A2 y B1*\n\n" +
                "Para motocicleta y vehÃ­culo particular.\n\n" +
                "ðŸ’° *Valor curso:* $1.990.000 (incluye examen mÃ©dico)\n" +
                "ðŸªª *Valor licencias:* Moto $272.800 y carro $329.900 en ventanilla Ãºnica\n\n" +
                "âœ… *Incluye:*\n" +
                "â€¢ 30 horas teÃ³ricas ðŸ‘©â€ðŸ«\n" +
                "â€¢ 20 horas prÃ¡cticas carro ðŸš˜\n" +
                "â€¢ 15 horas prÃ¡cticas moto ðŸ\n" +
                "â€¢ 5 horas taller ðŸ› \n" +
                "â€¢ Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    // Quitado: "CategorÃ­a A2, B1 y C1"
    /** Ficha comercial breve del paquete A2, B1 y C1. */
    private String courseA2B1C1Text() {
        return "ðŸï¸ðŸš—ðŸš• *CategorÃ­a A2, B1 y C1*\n\n" +
                "Para moto, servicio particular y pÃºblico.\n\n" +
                "ðŸ’° *Valor curso:* $2.150.000 (incluye examen mÃ©dico)\n" +
                "ðŸªª *Valor licencias:* Moto $272.800 y carro $329.900 en ventanilla Ãºnica\n\n" +
                "âœ… *Incluye:*\n" +
                "â€¢ 30 horas de teorÃ­a ðŸ‘©â€ðŸ«\n" +
                "â€¢ 5 horas de taller ðŸ› \n" +
                "â€¢ 15 horas de prÃ¡ctica carro ðŸš˜\n" +
                "â€¢ 30 horas de prÃ¡ctica moto ðŸ\n" +
                "â€¢ Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    // Quitado: tÃ­tulo duplicado ("RecategorizaciÃ³n..." dos veces)
    /** Ficha comercial breve de la recategorizaciÃ³n B1 a C1. */
    private String courseRecategorizacionText() {
        return "ðŸ”„ *RecategorizaciÃ³n B1 a C1*\n\n" +
                "Es para servicio particular B1 a servicio pÃºblico C1\n\n" +
                "ðŸ“Œ Valor curso: $950.000 (incluye examen mÃ©dico)\n" +
                "ðŸ“Œ Valor licencia: $329.900 en ventanilla Ãºnica\n\n" +
                "INCLUYE:\n" +
                "â° 5 horas de TeorÃ­a ðŸ‘©â€ðŸ«\n" +
                "â° 10 horas de prÃ¡ctica. ðŸš˜\n" +
                "â° + Certificado ðŸ‘¨ðŸ»â€ðŸŽ“";
    }

    /** InformaciÃ³n de la clase de refuerzo para carro. */
    private String serviceRefuerzoCarroText() {
        return "ðŸš˜ *Clase de refuerzo - Carro*\n\n" +
                "Refuerza tus habilidades al volante ðŸš˜\n\n" +
                "ðŸ“Œ DuraciÃ³n: 45 minutos\n" +
                "ðŸ“Œ Valor por hora: $45.000\n\n" +
                "Importante:\n" +
                "âœ… Al solicitar, dirÃ­gete al carrito para agregar la cantidad de clases que desees.";
    }

    /** InformaciÃ³n de la clase de refuerzo para moto. */
    private String serviceRefuerzoMotoText() {
        return "ðŸï¸ *Clase de refuerzo - Moto*\n\n" +
                "Refuerza tus habilidades de conducciÃ³n ðŸ\n\n" +
                "ðŸ“Œ DuraciÃ³n: 40 minutos\n" +
                "ðŸ“Œ Valor por hora: $40.000\n\n" +
                "Importante:\n" +
                "âœ… Al solicitar, dirÃ­gete al carrito para agregar la cantidad de clases que desees.";
    }

    /** Primer mensaje operativo del flujo de matrÃ­cula. */
    private String enrollmentInitialPromptText() {
        return "ðŸ“ Perfecto. Vamos a iniciar tu matrÃ­cula en CEA HARO.\n\n" +
                "Para continuar, envÃ­ame esta informaciÃ³n *en un solo mensaje*:\n\n" +
                "1) Nombre completo (solo letras, sin nÃºmeros)\n" +
                "2) NÃºmero de documento (puede incluir puntos o espacios; yo lo limpio)\n" +
                "âœ… Ejemplo:\n" +
                "Juan Perez 12345678\n\n" +
                "DespuÃ©s te mostrarÃ© un menÃº para seleccionar la categorÃ­a (A2, B1, C1, A2 y B1, A2, B1 y C1).";
    }

    /** MenÃº de categorÃ­as mostrado durante la captura de matrÃ­cula. */
    private String enrollmentCategoryMenuText() {
        return "ðŸ“Œ Selecciona la categorÃ­a que deseas realizar:\n\n" +
                "1) ðŸï¸ A2\n" +
                "2) ðŸš— B1\n" +
                "3) ðŸš• C1\n" +
                "4) ðŸï¸ðŸš— A2 y B1\n" +
                "5) ðŸï¸ðŸš—ðŸš• A2, B1 y C1\n\n" +
                "âœï¸ Responde con un nÃºmero del 1 al 5.";
    }

    /** Resumen de horarios de atenciÃ³n y sedes. */
    private String infoText() {
        return "ðŸ“Œ *Horarios de AtenciÃ³n*\n\n" +
                "*ðŸ—‚ï¸ Administrativo*\n" +
                "Lunes a SÃ¡bado\n" +
                "ðŸ•— 8:00 AM - 6:00 PM\n\n" +

                "*ðŸ“š Clases TeÃ³ricas*\n" +
                "ðŸ—“ï¸ Lunes y Viernes: 6:00 AM - 2:00 PM\n" +
                "ðŸ—“ï¸ Martes, MiÃ©rcoles y Jueves: 2:00 PM - 10:00 PM\n" +
                "â³ Puedes ver mÃ­nimo 2 y mÃ¡ximo 8 horas al dÃ­a\n\n" +

                "*ðŸš— Clases PrÃ¡cticas*\n" +
                "ðŸ—“ï¸ Domingo a Domingo\n" +
                "ðŸ•• 6:00 AM - 10:00 PM\n" +
                "(Sujeto a disponibilidad)\n\n" +

                "âœ¨ Â¡Nos adaptamos a tu tiempo!\n\n" +

                "*ðŸ“ Sedes disponibles:*\n" +
                "1) Av. 1 de Mayo â€“ Av. 1 de Mayo #68D-23 Piso 2\n" +
                "2) CC El EdÃ©n â€“ Local L2-094A";
    }

    /** MenÃº principal disponible para estudiantes autenticados. */
    private String studentMenuText() {
        return "ðŸŽ“âœ¨ Soy estudiante CEA HARO\n\n" +
                "1) Consultar calendario de prÃ¡cticas\n" +
                "2) Agendar clase prÃ¡ctica\n" +
                "3) Consultar mi horario\n" +
                "4) Cancelar clase prÃ¡ctica\n" +
                "5) â¬…ï¸ Volver al menÃº principal\n" +
                "6) ðŸ”’ Cerrar sesiÃ³n de estudiante\n\n" +
                "âœï¸ Responde con un nÃºmero del 1 al 6.";
    }
}

