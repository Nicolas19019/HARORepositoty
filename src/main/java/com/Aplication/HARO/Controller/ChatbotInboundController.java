package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.text.Normalizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotInboundController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotInboundController.class);
    private static final String ADVISOR_WHATSAPP_LINK = "https://wa.me/573202114876";

    private static final Duration SESSION_TTL = Duration.ofHours(6);
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
    private static final Pattern DOCUMENT_PATTERN = Pattern.compile("^\\d{5,20}$");

    private final ChatbotProcesoService procesoService;
    private final VerificationService verificationService;
    private final WhatsAppTemplateService waService;

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.inactivity.timeout.minutes:20}")
    private long inactivityTimeoutMinutes;

    @Value("${chatbot.enrollment.welcome-image-url:}")
    private String enrollmentWelcomeImageUrl;
    @Value("${chatbot.inbound.include-image-action:false}")
    private boolean includeImageActionInInbound;

    public ChatbotInboundController(ChatbotProcesoService procesoService,
                                    VerificationService verificationService,
                                    WhatsAppTemplateService waService) {
        this.procesoService = procesoService;
        this.verificationService = verificationService;
        this.waService = waService;
    }

    public record InboundMessage(
            String from,
            String text,
            String messageId,
            String timestamp,
            String phoneNumberId
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

        ENROLLMENT_CAPTURE,
        ENROLLMENT_EMAIL_CAPTURE,
        ENROLLMENT_PHONE_CAPTURE,
        ENROLLMENT_CONFIRM,
        PAYMENT_WAIT,
        CONTRACT_WAIT,

        SEDE_SELECTION,

        STUDENT_MENU,
        STUDENT_DOC_CAPTURE,
        STUDENT_OTP_VERIFY,
        STUDENT_BOOKING_SLOT,
        STUDENT_CANCEL_CLASS_PICK,

        DONE
    }

    enum StudentAction {
        NONE,
        VIEW_CALENDAR,
        VIEW_SCHEDULE,
        BOOK_CLASS,
        CANCEL_CLASS
    }

    static class SessionData {
        ChatState state = ChatState.MAIN_MENU;
        boolean courseOptionsExpanded = false;

        // Captura matrícula
        String nombre;
        String documento;
        String categoria;
        String email;
        String telefono;

        // Sede seleccionada
        String sedeSeleccionada;

        // Subflujo estudiante + OTP
        StudentAction pendingStudentAction = StudentAction.NONE;
        Long studentId;
        String studentDocumento;
        String studentEmail;
        String studentNombre;
        String studentBookingDate;

        Instant lastSeen = Instant.now();
    }

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    @PostMapping("/inbound")
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

        SessionData session = sessions.computeIfAbsent(from, k -> new SessionData());
        log.debug("CHATBOT INBOUND from={} state={} textLength={}", maskPhone(from), session.state, rawText.length());

        List<BotAction> actions = new ArrayList<>();

        synchronized (session) {
            Instant now = Instant.now();

            // Inactividad (no aplica si está en menú principal)
            if (isConversationExpired(session, now)) {
                log.info("Session expired by inactivity from={} state={}", maskPhone(from), session.state);
                resetToMain(session);
                session.lastSeen = now;
                actions.add(textMsg(inactivityTimeoutText()));
                return ResponseEntity.ok(new BotResponse(actions));
            }

            // Finalizar
            if (isEndCommand(text)) {
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

            switch (session.state) {
                case MAIN_MENU -> handleMainMenu(text, from, session, actions);
                case COURSES_MENU -> handleCoursesMenu(text, from, session, actions);

                case ENROLLMENT_CAPTURE -> handleEnrollmentCapture(rawText, session, actions);
                case ENROLLMENT_EMAIL_CAPTURE -> handleEnrollmentEmailCapture(text, session, actions);
                case ENROLLMENT_PHONE_CAPTURE -> handleEnrollmentPhoneCapture(from, text, session, actions);
                case ENROLLMENT_CONFIRM -> handleEnrollmentConfirm(text, from, session, actions);
                case PAYMENT_WAIT -> handlePaymentWait(text, session, actions);
                case CONTRACT_WAIT -> handleContractWait(text, session, actions);

                case SEDE_SELECTION -> handleSedeSelection(text, session, actions);

                case STUDENT_MENU -> handleStudentMenu(text, session, actions);
                case STUDENT_DOC_CAPTURE -> handleStudentDocCapture(text, session, actions);
                case STUDENT_OTP_VERIFY -> handleStudentOtpVerify(text, session, actions);
                case STUDENT_BOOKING_SLOT -> handleStudentBookingSlot(rawText, session, actions);
                case STUDENT_CANCEL_CLASS_PICK -> handleStudentCancelClassPick(text, session, actions);

                case DONE -> actions.add(textMsg("✅ Tu proceso ya fue completado.\n\nEscribe MENU para volver al inicio."));
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
            clearEnrollmentData(session);
            session.state = ChatState.ENROLLMENT_CAPTURE;
            addEnrollmentIntro(from, actions);
            actions.add(textMsg(enrollmentInitialPromptText()));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        switch (cmd) {
            case "1", "cursos", "curso", "categorias", "categoria" -> {
                session.state = ChatState.COURSES_MENU;
                session.courseOptionsExpanded = false;
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "2" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                addEnrollmentIntro(from, actions);
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("Opciones: MENU | CANCELAR"));
            }
            case "3", "informacion", "info", "horarios" -> {
                actions.add(textMsg(infoText()));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "4", "estudiante", "soy estudiante" -> {
                clearStudentAccessData(session);
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg(
                        "🎓 ¡Perfecto! Vamos a validar tu acceso como estudiante.\n\n" +
                                "Primero escribe tu número de documento (solo números) y te enviaremos un OTP a tu correo.\n\n" +
                                "Ejemplo: 12345678"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "5", "asesor", "contactar asesor", "contactar un asesor", "hablar con asesor" -> {
                actions.add(textMsg(advisorContactText()));
                actions.add(textMsg("Opciones: MENU"));
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
            clearEnrollmentData(session);
            session.state = ChatState.ENROLLMENT_CAPTURE;
            addEnrollmentIntro(from, actions);
            actions.add(textMsg(enrollmentInitialPromptText()));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        // “ver todas/combos” por compatibilidad
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
                    actions.add(textMsg(courseA2Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "2", "b1" -> {
                    actions.add(textMsg(courseB1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "3", "c1" -> {
                    actions.add(textMsg(courseC1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "4", "a2 y b1", "a2+b1", "a2/b1", "a2,b1" -> {
                    actions.add(textMsg(courseA2B1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "5", "a2, b1 y c1", "a2 b1 y c1", "a2+b1+c1", "a2/b1/c1", "a2,b1,c1" -> {
                    actions.add(textMsg(courseA2B1C1Text()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
                case "6", "recategorizacion", "recategorización", "b1 a c1", "b1->c1" -> {
                    actions.add(textMsg(courseRecategorizacionText()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
            default -> {
                actions.add(textMsg("⚠️ Opción no reconocida para este listado de cursos."));
                actions.add(textMsg(allCategoriesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            }
            return;
        }

        // Menú principal de cursos (1..6)
        switch (cmd) {
            case "1", "a2" -> {
                actions.add(textMsg(courseA2Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "2", "b1" -> {
                actions.add(textMsg(courseB1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "3", "c1" -> {
                actions.add(textMsg(courseC1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "4", "a2 y b1", "a2+b1", "a2/b1", "a2,b1" -> {
                actions.add(textMsg(courseA2B1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "5", "a2, b1 y c1", "a2 b1 y c1", "a2+b1+c1", "a2/b1/c1", "a2,b1,c1" -> {
                actions.add(textMsg(courseA2B1C1Text()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "6", "recategorizacion", "recategorización", "b1 a c1", "b1->c1" -> {
                actions.add(textMsg(courseRecategorizacionText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            default -> {
                actions.add(textMsg("⚠️ Opción no reconocida para este menú. Escoge 1 a 6, MATRICULA o MENU."));
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
        }
    }

    private void handleEnrollmentCapture(String rawText, SessionData session, List<BotAction> actions) {
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
        session.state = ChatState.ENROLLMENT_EMAIL_CAPTURE;

        actions.add(textMsg(
                "📧 Paso 2 de 5: envía tu correo electrónico.\n" +
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
                "📱 Paso 3 de 5: envía tu teléfono de contacto.\n" +
                        "Ejemplo: 573001112233"
        ));
        actions.add(textMsg("Opciones: MENU | CANCELAR"));
    }

    private void handleEnrollmentPhoneCapture(String from, String text, SessionData session, List<BotAction> actions) {
        String phone = trim(text).replaceAll("\\s+", "");
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            actions.add(textMsg(
                    "⚠️ Teléfono inválido.\n\n" +
                            "Debes enviar entre 8 y 15 dígitos (puede iniciar con +).\n" +
                            "Ejemplo: 573001112233"
            ));
            actions.add(textMsg("Opciones: MENU | CANCELAR"));
            return;
        }

        session.telefono = phone;

        try {
            procesoService.upsertDraft(
                    from,
                    session.nombre,
                    session.documento,
                    session.categoria,
                    session.email,
                    session.telefono
            );
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude guardar tu pre-registro: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        session.state = ChatState.ENROLLMENT_CONFIRM;

        actions.add(textMsg(
                "✅ Paso 4 de 5: revisa tus datos y confirma.\n\n" +
                        "👤 Nombre: " + safe(session.nombre) + "\n" +
                        "🆔 Documento: " + safe(session.documento) + "\n" +
                        "🚗 Categoría: " + safe(session.categoria) + "\n" +
                        "📧 Correo: " + safe(session.email) + "\n" +
                        "📱 Teléfono: " + safe(session.telefono) + "\n\n" +
                        "Responde:\n" +
                        "1) Confirmar y continuar\n" +
                        "2) Corregir datos\n" +
                        "3) Cancelar"
        ));
        actions.add(textMsg("Opciones: MENU"));
    }

    private void handleEnrollmentConfirm(String text, String from, SessionData session, List<BotAction> actions) {
        switch (text) {
            case "1", "si", "sí", "confirmar", "continuar" -> {
                try {
                    procesoService.markPaymentPending(session.documento);
                    String link = procesoService.getPaymentLink(session.documento);
                    session.state = ChatState.PAYMENT_WAIT;

                    actions.add(textMsg(
                            "💳 Paso 5 de 5: realiza el pago para continuar.\n\n" +
                                    "Enlace de pago:\n" + link + "\n\n" +
                                    "Cuando lo realices, vuelve a este chat.\n" +
                                    "Si necesitas el enlace otra vez escribe: LINK\n\n" +
                                    paymentFlowInfo()
                    ));
                    actions.add(textMsg("Opciones: MENU | TERMINAR"));
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No pude iniciar el pago: " + e.getMessage()));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            case "2", "corregir" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                addEnrollmentIntro(from, actions);
                actions.add(textMsg(enrollmentInitialPromptText()));
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

    private void handlePaymentWait(String text, SessionData session, List<BotAction> actions) {
        switch (text) {
            case "pendiente" -> {
                actions.add(textMsg(
                        "⏳ Tu pago está *PENDIENTE*.\n\n" +
                                "Esto puede tardar unos minutos según tu entidad.\n" +
                                "Si deseas ver el enlace otra vez escribe: LINK"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "aprobado" -> {
                try {
                    procesoService.markPaymentApproved(session.documento);
                    String contractLink = createAndStoreContractLink(session.documento);
                    session.state = ChatState.CONTRACT_WAIT;

                    actions.add(textMsg(
                            "✅ Pago aprobado.\n\n" +
                                    "Siguiente paso: completa y firma los contratos del curso en el portal.\n\n" +
                                    "Enlace único e intransferible:\n" + contractLink + "\n\n" +
                                    "Cuando termines escribe: LISTO"
                    ));
                    actions.add(textMsg("Opciones: MENU"));
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No pude confirmar el pago: " + e.getMessage()));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            case "ya pague", "ya pagué", "pague", "pagué" -> {
                actions.add(textMsg(
                        "✅ Gracias. Estamos validando tu pago.\n\n" +
                                "Cuando se confirme te enviaremos el enlace de contratos.\n" +
                                "Si necesitas el enlace de pago nuevamente escribe: LINK"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "pagar", "link" -> {
                try {
                    String link = procesoService.getPaymentLink(session.documento);
                    actions.add(textMsg("🔗 Enlace de pago:\n" + link + "\n\n" + paymentFlowInfo()));
                    actions.add(textMsg("Opciones: MENU"));
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No encuentro un enlace de pago para este proceso."));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            default -> {
                actions.add(textMsg(
                        "💳 Estamos esperando confirmación de pago.\n\n" +
                                "Puedes escribir:\n" +
                                "- LINK (ver enlace de pago)\n" +
                                "- YA PAGUÉ (si ya realizaste el pago)\n" +
                                "- PENDIENTE / APROBADO (modo prueba)"
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

        StringBuilder out = new StringBuilder("ConfiguraciÃ³n del flujo de pago:\n");
        if (!confirmation.isBlank()) {
            out.append("- ConfirmaciÃ³n: ").append(confirmation).append("\n");
        }
        if (!response.isBlank()) {
            out.append("- Retorno: ").append(response).append("\n");
        }
        return out.toString().trim();
    }

    private void handleContractWait(String text, SessionData session, List<BotAction> actions) {
        switch (text) {
            case "listo", "firmado", "hecho" -> {
                try {
                    boolean signed = procesoService.isContractSigned(session.documento);
                    if (!signed) {
                        actions.add(textMsg(
                                "⏳ Aún no vemos la firma validada.\n\n" +
                                        "1) Abre el enlace de contratos\n" +
                                        "2) Completa el proceso\n" +
                                        "3) Luego escribe LISTO nuevamente"
                        ));
                        actions.add(textMsg("Opciones: MENU"));
                        return;
                    }

                    session.state = ChatState.SEDE_SELECTION;
                    actions.add(textMsg(
                            "✅ Contratos validados.\n\n" +
                                    "📍 Antes de finalizar, selecciona tu sede:\n\n" +
                                    "1) Kennedy – Av. 1 de Mayo #68D-23 Piso 2\n" +
                                    "2) CC El Edén – Local L2-094A\n\n" +
                                    "Responde con 1 o 2."
                    ));
                    actions.add(textMsg("Opciones: MENU"));
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No pude validar el contrato: " + e.getMessage()));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            case "link", "contrato", "contratos" -> {
                try {
                    Optional<ChatbotMatriculaProceso> proceso = procesoService.findProcesoByDocumento(session.documento);
                    String link = proceso.map(ChatbotMatriculaProceso::getContractLink).orElse("");
                    if (link == null || link.isBlank()) {
                        actions.add(textMsg("⚠️ Aún no hay enlace de contratos para este proceso."));
                    } else {
                        actions.add(textMsg("🔗 Enlace de contratos:\n" + link));
                    }
                    actions.add(textMsg("Opciones: MENU"));
                } catch (Exception e) {
                    actions.add(textMsg("⚠️ No pude obtener el enlace de contratos."));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
            default -> {
                actions.add(textMsg(
                        "📄 En este paso debes firmar los contratos.\n\n" +
                                "Cuando termines escribe: LISTO\n" +
                                "Si necesitas el enlace otra vez escribe: LINK"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
        }
    }

    private void handleSedeSelection(String text, SessionData session, List<BotAction> actions) {
        String t = normalizeInput(text);

        if ("1".equals(t) || "kennedy".equals(t)) {
            session.sedeSeleccionada = "Kennedy";
        } else if ("2".equals(t) || "eden".equals(t) || "el eden".equals(t) || "cc el eden".equals(t) || "cc eleden".equals(t)) {
            session.sedeSeleccionada = "CC El Edén";
        } else {
            actions.add(textMsg(
                    "⚠️ Debes seleccionar una sede válida.\n\n" +
                            "1) Kennedy\n" +
                            "2) CC El Edén\n\n" +
                            "Responde con 1 o 2."
            ));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            Long studentId = procesoService.createStudentFromSignedContract(session.documento, session.sedeSeleccionada);

            session.state = ChatState.DONE;

            actions.add(textMsg(
                    "🎉 Matrícula finalizada correctamente.\n\n" +
                            "📍 Sede asignada: *" + session.sedeSeleccionada + "*\n" +
                            "✅ Proceso activo: *" + safe(session.categoria) + "*\n\n" +
                            "👥 Grupo de clases teóricas:\n" +
                            "URL_GRUPO_WHATSAPP_AQUI\n\n" +
                            "📌 La teoría no requiere agendamiento.\n" +
                            "⏰ Primera clase: llega 30 minutos antes para biométricos.\n\n" +
                            "Ref: " + studentId
            ));
            actions.add(textMsg("Opciones: MENU | TERMINAR"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude crear el estudiante con sede: " + e.getMessage()));
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

        switch (text) {
            case "1", "calendario" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "2", "reservar" -> {
                session.pendingStudentAction = StudentAction.NONE;
                session.studentBookingDate = null;
                session.state = ChatState.STUDENT_BOOKING_SLOT;
                actions.add(textMsg(
                        "🚘 Vamos a reservar tu práctica.\n\n" +
                                "Pasos a seguir:\n" +
                                "1️⃣ Escribe la fecha de la clase.\n" +
                                "2️⃣ Puedes escribir: hoy, mañana o YYYY-MM-DD.\n" +
                                "3️⃣ Después te mostraré opciones de hora para elegir.\n\n" +
                                "Ejemplos de fecha: hoy | mañana | 2026-03-15"
                ));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "3", "horario" -> {
                List<Clase> agenda = procesoService.listAgendaByStudentId(session.studentId);
                actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "4", "cancelar clase", "cancelar" -> {
                List<Clase> agenda = procesoService.listUpcomingClassesForCancellationByStudentId(session.studentId);
                if (agenda.isEmpty()) {
                    actions.add(textMsg(
                            "ℹ️ No tienes clases futuras para cancelar.\n\n" +
                                    "Si agendas una nueva clase, podrás verla aquí."
                    ));
                    actions.add(textMsg("Opciones: MENU"));
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
                actions.add(textMsg("Opciones: MENU"));
            }
            case "5", "volver" -> {
                resetToMain(session);
                actions.add(textMsg(mainMenuText()));
            }
            default -> {
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg("Opciones: MENU"));
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
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            ChatbotProcesoService.StudentAccessData access = procesoService.requireStudentAccessData(doc);
            sendVerificationEmailAsync(access.email());

            session.studentId = access.studentId();
            session.studentDocumento = access.documento();
            session.studentEmail = access.email();
            session.studentNombre = access.nombreCompleto();
            session.state = ChatState.STUDENT_OTP_VERIFY;

            actions.add(textMsg(
                    "🔐 Listo. Te enviamos un código OTP al correo " + access.emailMasked() + ".\n\n" +
                            "Pasos a seguir:\n" +
                            "1️⃣ Abre tu correo (revisa también Spam o No deseado).\n" +
                            "2️⃣ Busca el mensaje con tu código OTP.\n" +
                            "3️⃣ Copia solo los números del código.\n" +
                            "4️⃣ Escríbelo aquí en el chat.\n\n" +
                            "⏱️ Si no llega de inmediato, espera hasta 1 minuto."
            ));
            actions.add(textMsg("Opciones: MENU"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude iniciar verificación OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU"));
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
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            boolean ok = verificationService.verifyEmailOtp(session.studentEmail, code);
            if (!ok) {
                actions.add(textMsg(
                        "❌ El código es incorrecto o ya venció.\n\n" +
                                "Pasos para pedir uno nuevo:\n" +
                                "1️⃣ Escribe MENU\n" +
                                "2️⃣ Escribe Soy estudiante\n" +
                                "3️⃣ Ingresa tu documento nuevamente"
                ));
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            actions.add(textMsg(studentGreetingText(session.studentNombre)));
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;
            actions.add(textMsg(studentMenuText()));
            actions.add(textMsg("Opciones: MENU"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude validar OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU"));
        }
    }

    private void handleStudentBookingSlot(String rawText, SessionData session, List<BotAction> actions) {
        if (session.studentId == null) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Tu sesión de estudiante expiró. Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        String trimmed = trim(rawText);

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
                    actions.add(textMsg("Opciones: MENU"));
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
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            if (fecha.isBefore(LocalDate.now())) {
                actions.add(textMsg(
                        "⚠️ La fecha no puede ser pasada.\n\n" +
                                "Escribe hoy, mañana o una fecha futura."
                ));
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            session.studentBookingDate = fecha.toString();
            actions.add(textMsg(
                    "📅 Fecha registrada: " + fecha + "\n\n" +
                            bookingSlotsAvailabilityText(session.studentId, fecha)
            ));
            actions.add(textMsg("Opciones: MENU"));
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
            actions.add(textMsg(bookingSlotsAvailabilityText(session.studentId, fecha)));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            completeStudentBooking(fecha, hora, session, actions);
        } catch (Exception e) {
            actions.add(textMsg(
                    "⚠️ No pude agendar la clase: " + e.getMessage() + "\n\n" +
                            bookingSlotsAvailabilityText(session.studentId, fecha)
            ));
            actions.add(textMsg("Opciones: MENU"));
        }
    }

    private void handleStudentCancelClassPick(String text, SessionData session, List<BotAction> actions) {
        if (session.studentId == null) {
            session.state = ChatState.STUDENT_DOC_CAPTURE;
            actions.add(textMsg(
                    "🔐 Tu sesión de estudiante expiró. Escribe tu documento para validar OTP nuevamente."
            ));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        String input = trim(text);
        if (!input.matches("^\\d+$")) {
            List<Clase> agenda = procesoService.listUpcomingClassesForCancellationByStudentId(session.studentId);
            if (agenda.isEmpty()) {
                session.state = ChatState.STUDENT_MENU;
                session.pendingStudentAction = StudentAction.NONE;
                actions.add(textMsg("ℹ️ Ya no hay clases futuras para cancelar."));
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            actions.add(textMsg(
                    "⚠️ Debes escribir el ID de la clase a cancelar.\n\n" +
                            "Clases futuras:\n\n" +
                            formatCancelableClasses(agenda) + "\n\n" +
                            "Ejemplo: 123"
            ));
            actions.add(textMsg("Opciones: MENU"));
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
            actions.add(textMsg("Opciones: MENU"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude cancelar la clase: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU"));
        }
    }

    // =========================
    // LINKS / PARSING / FORMAT
    // =========================

    private String createAndStoreContractLink(String documento) {
        ChatbotMatriculaProceso proceso = procesoService.findProcesoByDocumento(documento)
                .orElseThrow(() -> new IllegalStateException("No existe proceso de matrícula para generar contrato"));

        VerificationService.ContractLinkResult out = verificationService.createContractVerificationLink(
                proceso.getEmail(),
                contractBaseUrl
        );
        procesoService.markContractLinkSent(documento, out.url());
        return out.url();
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

    private String bookingSlotsAvailabilityText(Long studentId, LocalDate fecha) {
        try {
            List<ChatbotProcesoService.SlotAvailability> slots =
                    procesoService.listPracticalSlotAvailabilityByStudentId(studentId, fecha);
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
                procesoService.bookPracticalClassByStudentId(session.studentId, fecha, hora);
        Clase clase = booking.clase();
        String calendarLink = booking.reunionCalendario() == null ? "" : trim(booking.reunionCalendario().htmlLink());
        String meetLink = booking.reunionCalendario() == null ? "" : trim(booking.reunionCalendario().meetLink());

        StringBuilder out = new StringBuilder();
        out.append("✅ Clase práctica agendada y cita creada en Calendar.\n\n")
                .append("ID: ").append(clase.getId()).append("\n")
                .append("Fecha: ").append(clase.getFecha()).append("\n")
                .append("Inicio: ").append(clase.getHoraInicio()).append("\n")
                .append("Fin: ").append(clase.getHoraFin());
        if (!calendarLink.isBlank()) {
            out.append("\nEvento: ").append(calendarLink);
        }
        if (!meetLink.isBlank()) {
            out.append("\nMeet: ").append(meetLink);
        }

        session.state = ChatState.STUDENT_MENU;
        session.pendingStudentAction = StudentAction.NONE;
        session.studentBookingDate = null;

        actions.add(textMsg(out.toString()));
        actions.add(textMsg("Opciones: MENU"));
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
        Instant cutoff = Instant.now().minus(SESSION_TTL);
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
        clearStudentAccessData(session);
    }

    private void clearEnrollmentData(SessionData session) {
        session.nombre = null;
        session.documento = null;
        session.categoria = null;
        session.email = null;
        session.telefono = null;
        session.sedeSeleccionada = null;
    }

    private void clearStudentAccessData(SessionData session) {
        session.pendingStudentAction = StudentAction.NONE;
        session.studentId = null;
        session.studentDocumento = null;
        session.studentEmail = null;
        session.studentNombre = null;
        session.studentBookingDate = null;
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

    private boolean isEnrollmentCommand(String cmd) {
        if (cmd == null) return false;
        String normalized = normalizeCommandText(cmd);
        if (normalized.isBlank()) return false;

        if (normalized.startsWith("matric")) return true;
        if (normalized.startsWith("inscrib")) return true;
        if (normalized.startsWith("inscripcion")) return true;
        return false;
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
        if (session.state == ChatState.MAIN_MENU) return false;
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return now.isAfter(session.lastSeen.plus(Duration.ofMinutes(minutes)));
    }

    private String inactivityTimeoutText() {
        long minutes = Math.max(1, inactivityTimeoutMinutes);
        return "⏰ Conversación finalizada por inactividad (" + minutes + " minutos).\n\n" +
                "Escribe MENU para volver al inicio.";
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

    private String advisorContactText() {
        return "🤝 Para contactar un asesor, escribe aquí:\n" + ADVISOR_WHATSAPP_LINK;
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

    private void sendVerificationEmailAsync(String email) {
        CompletableFuture.runAsync(() -> {
            try {
                verificationService.sendEmailVerification(email);
            } catch (Exception e) {
                log.error("Async OTP send failed email={}", maskEmail(email), e);
            }
        });
    }

    private String maskEmail(String email) {
        String e = trim(email).toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.charAt(0) + "***" + e.substring(at);
    }

    private BotAction textMsg(String body) {
        return new BotAction("text", body, null, null, null);
    }

    private BotAction imageMsg(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return new BotAction("image", imageUrl, null, null, null);
    }

    private void addEnrollmentIntro(String to, List<BotAction> actions) {
        addEnrollmentIntro(actions);
        dispatchEnrollmentIntroViaWhatsApp(to);
    }

    private void dispatchEnrollmentIntroViaWhatsApp(String toRaw) {
        if (!waService.getConfigStatus().ready()) {
            log.warn("CHATBOT inbound: no se envia mensaje de bienvenida por WhatsApp porque no está listo: {}",
                    waService.getConfigStatus().message());
            return;
        }

        if (!org.springframework.util.StringUtils.hasText(toRaw)) {
            log.warn("CHATBOT inbound: no se puede enviar bienvenida porque from viene vacio");
            return;
        }

        String normalizedTo = toRaw.replaceAll("[\\s\\-()]", "");
        if (!normalizedTo.matches("\\+?\\d{8,15}")) {
            log.warn("CHATBOT inbound: no se puede enviar bienvenida a {} porque el numero no parece valido", maskPhone(toRaw));
            return;
        }

        log.info("CHATBOT inbound: iniciando envio de bienvenida a {}", maskPhone(normalizedTo));

        if (org.springframework.util.StringUtils.hasText(enrollmentWelcomeImageUrl)) {
            log.info("CHATBOT inbound: intentando enviar imagen a {} desde {}", maskPhone(toRaw), enrollmentWelcomeImageUrl);
            try {
                waService.sendImageMessage(toRaw, enrollmentWelcomeImageUrl);
                log.info("CHATBOT inbound: imagen enviada a {} desde {}", maskPhone(toRaw), enrollmentWelcomeImageUrl);
            } catch (Exception e) {
                log.error("CHATBOT inbound: no se pudo enviar imagen a {}: {}", maskPhone(toRaw), e.getMessage(), e);
            }
        } else {
            log.warn("CHATBOT inbound: no se envia imagen; chatbot.enrollment.welcome-image-url no configurada");
        }

        try {
            waService.sendTextMessage(
                    toRaw,
                    "Gracias por elegirnos! Para proporcionarte un servicio personalizado, necesitamos algunos datos personales, "
                            + "puedes revisar nuestra política de tratamiento de datos en nuestra página web y www.ceaharo.com"
            );
            waService.sendTextMessage(toRaw, "¿Autorizas el tratamiento de tus datos?");
            log.info("CHATBOT inbound: mensajes de texto de bienvenida enviados a {}", maskPhone(toRaw));
        } catch (Exception e) {
            log.error("CHATBOT inbound: no se pudo enviar texto de bienvenida a {}: {}", maskPhone(toRaw), e.getMessage(), e);
        }
    }

    private void addEnrollmentIntro(List<BotAction> actions) {
        if (includeImageActionInInbound) {
            BotAction image = imageMsg(enrollmentWelcomeImageUrl);
            if (image != null) {
                actions.add(image);
            }
        }
        actions.add(textMsg(
                "Gracias por elegirnos! Para proporcionarte un servicio personalizado, necesitamos algunos datos personales, " +
                        "puedes revisar nuestra política de tratamiento de datos en nuestra página web y www.ceaharo.com"
        ));
        actions.add(textMsg("¿Autorizas el tratamiento de tus datos?"));
    }

    // =========================
    // TEXTOS (PLANTILLAS + QUITAR REPETICIÓN DE "CATEGORÍA ...")
    // =========================

    private String mainMenuText() {
        return "👋 Hola, soy Ha-Rot, asistente virtual de CEA HARO.\n\n" +
                "¿Qué deseas hacer hoy?\n\n" +
                "1️⃣ Nuestros servicios (cursos y recategorización)\n" +
                "2️⃣ Iniciar matrícula\n" +
                "3️⃣ Horarios de atención y sedes\n" +
                "4️⃣ Soy un estudiante (consultas y reservas)\n" +
                "5️⃣ Contactar un asesor\n\n" +
                "Comandos: MENU | AYUDA | CANCELAR | TERMINAR";
    }

    private String coursesMenuText() {
        return "🧾 Nuestros Servicios\n" +
                "Selecciona el servicio que deseas solicitar y agrégalo a tu carrito. 🛒\n\n" +
                "1) 🏍️ Licencia A2\n" +
                "2) 🚗 Licencia B1\n" +
                "3) 🚕 Licencia C1\n" +
                "4) 🏍️🚗 A2 y B1\n" +
                "5) 🏍️🚗🚕 A2, B1 y C1\n" +
                "6) 🔄 Recategorización B1 a C1";
    }

    private String allCategoriesMenuText() {
        return "🧾 Nuestros Servicios\n" +
                "Selecciona el servicio que deseas solicitar y agrégalo a tu carrito. 🛒\n\n" +
                "1) 🏍️ Categoría A2\n" +
                "2) 🚗 Categoría B1\n" +
                "3) 🚕 Categoría C1\n" +
                "4) 🏍️🚗 Categoría A2 y B1\n" +
                "5) 🏍️🚗🚕 Categoría A2, B1 y C1\n" +
                "6) 🔄 Recategorización B1 a C1";
    }

    // ✅ Quitado: "Categoría A2"
    private String courseA2Text() {
        return "Categoría A2\n\n" +
                "Para motocicletas de cualquier tipo de cilindraje 🛵🏍\n\n" +
                "📌 Valor curso: $920.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $272.800 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 25 horas de teoría 👩‍🏫\n" +
                "⏰ 3 horas de taller 🛠\n" +
                "⏰ 15 horas de práctica 🚦\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    // ✅ Quitado: "Categoría B1"
    private String courseB1Text() {
        return "Categoría B1\n\n" +
                "Es para vehículos de placa amarilla 🚗\n\n" +
                "📌 Valor curso: $1.210.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 25 horas de Teoría 👩‍🏫\n" +
                "⏰ 5 horas de Taller🛠\n" +
                "⏰ 20 horas de práctica (16 horas en ciudad y 4 en carretera). 🚘\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    // ✅ Quitado: "Categoría C1"
    private String courseC1Text() {
        return "Categoría C1\n\n" +
                "Es para vehículos de placa blanca y amarilla 🚗🚕\n\n" +
                "📌 Valor curso: $1.350.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 30 horas de Teoría 👩‍🏫\n" +
                "⏰ 5 horas de Taller🛠\n" +
                "⏰ 30 horas de práctica (26 horas en ciudad y 4 en carretera). 🚘\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    // ✅ Quitado: "Categoría A2 y B1"
    private String courseA2B1Text() {
        return "Categoría A2 y B1\n\n" +
                "Es para motocicletas de cualquier tipo de cilindraje y para vehículo particular.\n\n" +
                "📌 Valor curso: $1.990.000 (incluye examen médico)\n" +
                "📌 Valor licencia: Moto $272.800 y carro $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 30 horas teóricas 👩‍🏫\n" +
                "⏰ 20 horas prácticas carro 🚘\n" +
                "⏰ 15 horas practicas moto 🏍\n" +
                "⏰ 5 horas taller 🛠\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    // ✅ Quitado: "Categoría A2, B1 y C1"
    private String courseA2B1C1Text() {
        return "Categoría A2, B1 y C1\n\n" +
                "Es para moto, servicio particular y público 🏍\n\n" +
                "📌 Valor curso: $2.150.000 (incluye examen médico)\n" +
                "📌 Valor licencia: Moto $272.800 y carro $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 30 horas de Teoría 👩‍🏫\n" +
                "⏰ 5 horas de Taller🛠\n" +
                "⏰ 15 horas de práctica. 🚘\n" +
                "⏰ 30 horas de práctica. 🏍\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    // ✅ Quitado: título duplicado ("Recategorización..." dos veces)
    private String courseRecategorizacionText() {
        return "🔄 Recategorización B1 a C1\n\n" +
                "🚗➡️🚕 Es para pasar de servicio particular B1 a servicio público C1\n\n" +
                "📌 Valor curso: $950.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $329.900 en ventanilla única\n\n" +
                "✅ INCLUYE:\n" +
                "⏰ 5 horas de Teoría 👩‍🏫\n" +
                "⏰ 10 horas de práctica 🚘\n" +
                "⏰ + Certificado 👨🏻‍🎓";
    }

    private String enrollmentInitialPromptText() {
        return "📝 ¡Perfecto! Vamos a iniciar tu matrícula en CEA HARO.\n\n" +
                "Para continuar necesito la siguiente información (en un solo mensaje):\n\n" +
                "1️⃣ Nombre completo (como aparece en tu documento)\n" +
                "2️⃣ Número de documento (sin puntos ni comas)\n" +
                "3️⃣ Categoría que deseas realizar (A2, B1, C1, A2 y B1, A2, B1 y C1)\n\n" +
                "Ejemplo:\n" +
                "Juan Perez 12345678 A2";
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
                "5️⃣ ⬅️ Volver al menú";
    }

    private String helpText(ChatState state) {
        return "Ayuda\n\n" +
                "Estado actual: " + state + "\n\n" +
                "Comandos:\n" +
                "- MENU: volver al inicio\n" +
                "- AYUDA: ver esta ayuda\n" +
                "- ASESOR: contactar un asesor\n" +
                "- CANCELAR: cancelar el proceso actual\n" +
                "- TERMINAR: finalizar la conversación\n\n" +
                "Inactividad:\n" +
                "- Si no respondes en " + Math.max(1, inactivityTimeoutMinutes) + " minutos, el flujo se reinicia.";
    }
}
