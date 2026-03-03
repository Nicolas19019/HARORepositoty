package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.VerificationService;
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
import java.time.format.DateTimeParseException;
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

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.inactivity.timeout.minutes:20}")
    private long inactivityTimeoutMinutes;

    public ChatbotInboundController(ChatbotProcesoService procesoService,
                                    VerificationService verificationService) {
        this.procesoService = procesoService;
        this.verificationService = verificationService;
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

        DONE
    }

    enum StudentAction {
        NONE,
        VIEW_CALENDAR,
        VIEW_SCHEDULE,
        BOOK_CLASS
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
        String studentDocumento;
        String studentEmail;

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
                case MAIN_MENU -> handleMainMenu(text, session, actions);
                case COURSES_MENU -> handleCoursesMenu(text, session, actions);

                case ENROLLMENT_CAPTURE -> handleEnrollmentCapture(rawText, session, actions);
                case ENROLLMENT_EMAIL_CAPTURE -> handleEnrollmentEmailCapture(text, session, actions);
                case ENROLLMENT_PHONE_CAPTURE -> handleEnrollmentPhoneCapture(from, text, session, actions);
                case ENROLLMENT_CONFIRM -> handleEnrollmentConfirm(text, session, actions);
                case PAYMENT_WAIT -> handlePaymentWait(text, session, actions);
                case CONTRACT_WAIT -> handleContractWait(text, session, actions);

                case SEDE_SELECTION -> handleSedeSelection(text, session, actions);

                case STUDENT_MENU -> handleStudentMenu(text, session, actions);
                case STUDENT_DOC_CAPTURE -> handleStudentDocCapture(text, session, actions);
                case STUDENT_OTP_VERIFY -> handleStudentOtpVerify(text, session, actions);
                case STUDENT_BOOKING_SLOT -> handleStudentBookingSlot(rawText, session, actions);

                case DONE -> actions.add(textMsg("✅ Tu proceso ya fue completado.\n\nEscribe MENU para volver al inicio."));
            }

            session.lastSeen = now;
        }

        return ResponseEntity.ok(new BotResponse(actions));
    }

    // =========================
    // HANDLERS
    // =========================

    private void handleMainMenu(String text, SessionData session, List<BotAction> actions) {
        switch (text) {
            case "1", "cursos", "curso", "categorias", "categoria" -> {
                session.state = ChatState.COURSES_MENU;
                session.courseOptionsExpanded = false;
                actions.add(textMsg(coursesMenuText()));
                actions.add(textMsg("Opciones: MATRICULA | MENU"));
            }
            case "2", "matricula", "inscripcion" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("Opciones: MENU | CANCELAR"));
            }
            case "3", "informacion", "info", "horarios" -> {
                actions.add(textMsg(infoText()));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "4", "estudiante", "soy estudiante" -> {
                clearStudentAccessData(session);
                session.state = ChatState.STUDENT_MENU;
                actions.add(textMsg(studentMenuText()));
                actions.add(textMsg("Opciones: MENU"));
            }
            default -> actions.add(textMsg(mainMenuText()));
        }
    }

    private void handleCoursesMenu(String text, SessionData session, List<BotAction> actions) {
        // “ver todas/combos” por compatibilidad
        if ("todos".equals(text) || "todas".equals(text) || "ver todas".equals(text) || "combos".equals(text)) {
            session.courseOptionsExpanded = true;
            actions.add(textMsg(allCategoriesMenuText()));
            actions.add(textMsg("Opciones: MATRICULA | MENU"));
            return;
        }

        // Menú expandido
        if (session.courseOptionsExpanded) {
            switch (text) {
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
                case "matricula", "inscripcion" -> {
                    session.courseOptionsExpanded = false;
                    clearEnrollmentData(session);
                    session.state = ChatState.ENROLLMENT_CAPTURE;
                    actions.add(textMsg(enrollmentInitialPromptText()));
                    actions.add(textMsg("Opciones: MENU | CANCELAR"));
                }
                default -> {
                    actions.add(textMsg(allCategoriesMenuText()));
                    actions.add(textMsg("Opciones: MATRICULA | MENU"));
                }
            }
            return;
        }

        // Menú principal de cursos (1..6)
        switch (text) {
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
            case "matricula", "inscripcion" -> {
                clearEnrollmentData(session);
                session.state = ChatState.ENROLLMENT_CAPTURE;
                actions.add(textMsg(enrollmentInitialPromptText()));
                actions.add(textMsg("Opciones: MENU | CANCELAR"));
            }
            default -> {
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

    private void handleEnrollmentConfirm(String text, SessionData session, List<BotAction> actions) {
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
                                    "Si necesitas el enlace otra vez escribe: LINK"
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
                                    "Siguiente paso: completa y firma los contratos del curso.\n\n" +
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
                    actions.add(textMsg("🔗 Enlace de pago:\n" + link));
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
        switch (text) {
            case "1", "calendario" -> {
                session.pendingStudentAction = StudentAction.VIEW_CALENDAR;
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg("📅 Para continuar, escribe tu número de documento.\nEjemplo: 12345678"));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "2", "reservar" -> {
                session.pendingStudentAction = StudentAction.BOOK_CLASS;
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg("🚘 Para reservar práctica, escribe tu número de documento.\nEjemplo: 12345678"));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "3", "horario" -> {
                session.pendingStudentAction = StudentAction.VIEW_SCHEDULE;
                session.state = ChatState.STUDENT_DOC_CAPTURE;
                actions.add(textMsg("🗓️ Para ver tu horario, escribe tu número de documento.\nEjemplo: 12345678"));
                actions.add(textMsg("Opciones: MENU"));
            }
            case "4", "volver" -> {
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
            actions.add(textMsg("⚠️ Documento inválido.\nDebe tener entre 5 y 20 dígitos.\nEjemplo: 12345678"));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            ChatbotProcesoService.StudentAccessData access = procesoService.requireStudentAccessData(doc);
            verificationService.sendEmailVerification(access.email());

            session.studentDocumento = access.documento();
            session.studentEmail = access.email();
            session.state = ChatState.STUDENT_OTP_VERIFY;

            actions.add(textMsg(
                    "🔐 Enviamos un código OTP al correo " + access.emailMasked() + ".\n\n" +
                            "1) Revisa tu correo (y spam)\n" +
                            "2) Copia el código\n" +
                            "3) Envíalo aquí (solo números)"
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
            actions.add(textMsg("⚠️ Código OTP inválido. Envía solo números."));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            boolean ok = verificationService.verifyEmailOtp(session.studentEmail, code);
            if (!ok) {
                actions.add(textMsg(
                        "⚠️ OTP inválido o vencido.\n\n" +
                                "Vuelve a “Soy estudiante” para generar uno nuevo."
                ));
                actions.add(textMsg("Opciones: MENU"));
                return;
            }

            switch (session.pendingStudentAction) {
                case VIEW_CALENDAR, VIEW_SCHEDULE -> {
                    List<Clase> agenda = procesoService.listAgendaByDocumento(session.studentDocumento);
                    actions.add(textMsg(formatAgenda(session.studentDocumento, agenda)));
                    actions.add(textMsg("Opciones: MENU"));
                    session.state = ChatState.STUDENT_MENU;
                    session.pendingStudentAction = StudentAction.NONE;
                }
                case BOOK_CLASS -> {
                    session.state = ChatState.STUDENT_BOOKING_SLOT;
                    actions.add(textMsg(
                            "✅ OTP validado.\n\n" +
                                    "Ahora envía fecha y hora en este formato:\n" +
                                    "YYYY-MM-DD HH:mm\n" +
                                    "Ejemplo: 2026-03-15 08:30"
                    ));
                    actions.add(textMsg("Opciones: MENU"));
                }
                default -> {
                    session.state = ChatState.STUDENT_MENU;
                    actions.add(textMsg(studentMenuText()));
                    actions.add(textMsg("Opciones: MENU"));
                }
            }
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude validar OTP: " + e.getMessage()));
            actions.add(textMsg("Opciones: MENU"));
        }
    }

    private void handleStudentBookingSlot(String rawText, SessionData session, List<BotAction> actions) {
        BookingSlot slot = parseBookingSlot(rawText);
        if (slot == null) {
            actions.add(textMsg(
                    "⚠️ Formato inválido.\n\n" +
                            "Debes escribir:\n" +
                            "YYYY-MM-DD HH:mm\n" +
                            "Ejemplo: 2026-03-15 08:30"
            ));
            actions.add(textMsg("Opciones: MENU"));
            return;
        }

        try {
            LocalDate fecha = LocalDate.parse(slot.fecha());
            LocalTime hora = LocalTime.parse(slot.hora());

            Clase clase = procesoService.bookPracticalClass(session.studentDocumento, fecha, hora);
            session.state = ChatState.STUDENT_MENU;
            session.pendingStudentAction = StudentAction.NONE;

            actions.add(textMsg(
                    "✅ Clase práctica agendada.\n\n" +
                            "ID: " + clase.getId() + "\n" +
                            "Fecha: " + clase.getFecha() + "\n" +
                            "Inicio: " + clase.getHoraInicio() + "\n" +
                            "Fin: " + clase.getHoraFin()
            ));
            actions.add(textMsg("Opciones: MENU"));
        } catch (Exception e) {
            actions.add(textMsg("⚠️ No pude agendar la clase: " + e.getMessage()));
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
        session.studentDocumento = null;
        session.studentEmail = null;
    }

    private boolean isMenuCommand(String text) {
        return "menu".equals(text) || "inicio".equals(text) || "start".equals(text);
    }

    private boolean isHelpCommand(String text) {
        return "ayuda".equals(text) || "help".equals(text);
    }

    private boolean isCancelCommand(String text) {
        return "cancelar".equals(text) || "salir".equals(text);
    }

    private boolean isEndCommand(String text) {
        return "terminar".equals(text)
                || "finalizar".equals(text)
                || "fin".equals(text)
                || "cerrar".equals(text);
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

    private BotAction textMsg(String body) {
        return new BotAction("text", body, null, null, null);
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
                "4️⃣ Soy un estudiante (consultas y reservas)\n\n" +
                "Comandos: MENU | AYUDA | CANCELAR | TERMINAR";
    }

    private String coursesMenuText() {
        return "Nuestros Servicios\n" +
                "Selecciona el servicio que deseas solicitar y agrégalo a tu carrito.\n\n" +
                "1) Licencia A2\n" +
                "2) Licencia B1\n" +
                "3) Licencia C1\n" +
                "4) A2 y B1\n" +
                "5) A2, B1 y C1\n" +
                "6) Recategorización B1 a C1";
    }

    private String allCategoriesMenuText() {
        return "Nuestros Servicios\n" +
                "Selecciona el servicio que deseas solicitar y agrégalo a tu carrito.\n\n" +
                "1) Categoría A2\n" +
                "2) Categoría B1\n" +
                "3) Categoría C1\n" +
                "4) Categoría A2 y B1\n" +
                "5) Categoría A2, B1 y C1\n" +
                "6) Recategorización B1 a C1";
    }

    // ✅ Quitado: "Categoría A2"
    private String courseA2Text() {
        return "Categoría  A2\n\n" +
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
        return "Categoría  B1\n\n" +
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
        return "CategoríaA2, B1 y C1\n\n" +
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
        return "Recategorización B1 a C1\n\n" +
                "Es para servicio particular B1 a servicio público C1\n\n" +
                "📌 Valor curso: $950.000 (incluye examen médico)\n" +
                "📌 Valor licencia: $329.900 en ventanilla única\n\n" +
                "INCLUYE:\n" +
                "⏰ 5 horas de Teoría 👩‍🏫\n" +
                "⏰ 10 horas de práctica. 🚘\n" +
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
        return "Horarios de Atención\n" +
                "Administrativo de Lunes a Sábado: 8:00 AM - 6:00 PM\n\n" +
                "Clases teóricas y prácticas: 6:00 AM - 10:00 PM\n\n" +
                "Según disponibilidad\n\n" +
                "¡Nos adaptamos a tu tiempo!\n\n" +
                "Sedes disponibles:\n" +
                "1) Kennedy – Av. 1 de Mayo #68D-23 Piso 2\n" +
                "2) CC El Edén – Local L2-094A";
    }

    private String studentMenuText() {
        return "Soy estudiante\n\n" +
                "1) Ver calendario de prácticas\n" +
                "2) Reservar clase práctica\n" +
                "3) Ver mi horario\n" +
                "4) Volver";
    }

    private String helpText(ChatState state) {
        return "Ayuda\n\n" +
                "Estado actual: " + state + "\n\n" +
                "Comandos:\n" +
                "- MENU: volver al inicio\n" +
                "- AYUDA: ver esta ayuda\n" +
                "- CANCELAR: cancelar el proceso actual\n" +
                "- TERMINAR: finalizar la conversación\n\n" +
                "Inactividad:\n" +
                "- Si no respondes en " + Math.max(1, inactivityTimeoutMinutes) + " minutos, el flujo se reinicia.";
    }
}