package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Security.AdminSedeGuard;
import com.Aplication.HARO.Service.ServicioLimpiezaSolicitudesEfectivo;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import com.Aplication.HARO.Service.ProspectoService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Endpoints de compatibilidad para HaroGestion (desktop).
 *
 * La vista de "Matrículas" del escritorio intenta diferentes recursos y sufijos.
 * Este controller expone:
 * - Listado de solicitudes de matrícula
 * - Creación (registro manual desde HaroGestion)
 * - Acciones: confirmar pago en efectivo + habilitar contratos, enviar contrato por correo o chatbot
 */
@RestController
@RequestMapping({
        "/api/matriculas",
        "/api/procesos-matricula",
        "/api/solicitudes-matricula",
        "/api/prematriculas",
        "/api/pre-matriculas"
})
public class MatriculasController {

    private static final Logger log = LoggerFactory.getLogger(MatriculasController.class);

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final ChatbotProcesoService procesoService;
    private final PaymentApprovalService paymentApprovalService;
    private final ProspectoService prospectoService;
    private final ServicioLimpiezaSolicitudesEfectivo cashCleanupService;
    private final JdbcTemplate jdbcTemplate;
    private final AdminSedeGuard adminSedeGuard;

    public MatriculasController(ChatbotMatriculaProcesoRepository procesoRepository,
                                ChatbotProcesoService procesoService,
                                PaymentApprovalService paymentApprovalService,
                                ProspectoService prospectoService,
                                ServicioLimpiezaSolicitudesEfectivo cashCleanupService,
                                JdbcTemplate jdbcTemplate,
                                AdminSedeGuard adminSedeGuard) {
        this.procesoRepository = procesoRepository;
        this.procesoService = procesoService;
        this.paymentApprovalService = paymentApprovalService;
        this.prospectoService = prospectoService;
        this.cashCleanupService = cashCleanupService;
        this.jdbcTemplate = jdbcTemplate;
        this.adminSedeGuard = adminSedeGuard;
    }

    public record CrearReq(
            String origenRegistro,
            String metodoPago,
            String estadoPago,
            BigDecimal valorPagado,
            String observacionPago,
            String sede,
            String categoria,
            EstudianteReq estudiante
    ) {
    }

    public record EstudianteReq(
            @NotBlank String numeroDocumento,
            String nombre,
            String apellido,
            String telefono,
            String email
    ) {
    }

    public record ActionReq(
            BigDecimal valorPagado,
            String observacionPago,
            Long validadoPorAdminId,
            Boolean sendEmail,
            Boolean sendChatbot,
            Boolean requireProspect,
            Boolean force
    ) {
    }

    public record VisibilityReq(Boolean visible) {
    }

    public record MatriculaRow(
            Long id,
            String nombreEstudiante,
            String numeroDocumento,
            String email,
            String telefono,
            String categoria,
            String sede,
            String origenRegistro,
            String metodoPago,
            String estadoPago,
            String estadoContrato,
            Instant createdAt,
            Instant updatedAt,
            boolean prospectoChatbotActivo,
            boolean visible
    ) {
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<MatriculaRow> list(@RequestParam(value = "limit", defaultValue = "500") int limit,
                                   @RequestParam(value = "all", defaultValue = "false") boolean all,
                                   @RequestParam(value = "includeHidden", defaultValue = "false") boolean includeHidden,
                                   Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        int size = Math.max(1, Math.min(limit, 2000));
        try {
            cashCleanupService.cleanupExpiredCashRequests();
        } catch (Exception ex) {
            log.warn("No se pudo ejecutar limpieza de solicitudes EFECTIVO vencidas: {}", ex.getMessage());
        }
        try {
            PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
            List<ChatbotMatriculaProceso> items;
            if (all) {
                items = includeHidden
                        ? procesoRepository.findAll(page).getContent()
                        : procesoRepository.findByVisibleTrue(page).getContent();
            } else {
                items = includeHidden
                        ? procesoRepository.findByMetodoPagoIgnoreCaseOrFlowStatusIgnoreCaseOrFlowStatusIgnoreCase(
                        "EFECTIVO", "PENDING_CASH_VALIDATION", "PENDING_PAYMENT", page)
                        : procesoRepository.findByVisibleTrueAndMetodoPagoIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCase(
                        "EFECTIVO", "PENDING_CASH_VALIDATION", "PENDING_PAYMENT", page);
            }
            if (!adminCtx.superAdmin()) {
                items = items.stream()
                        .filter(p -> adminSedeGuard.canAccess(adminCtx, p.getSede()))
                        .toList();
            }
            return items.stream().map(this::toRow).toList();
        } catch (Exception ex) {
            log.warn("Fallo listando solicitudes por JPA, usando fallback JDBC. cause={}", ex.getMessage());
            List<MatriculaRow> rows = listViaJdbc(size, all, includeHidden);
            if (!adminCtx.superAdmin()) {
                rows = rows.stream()
                        .filter(r -> adminSedeGuard.canAccess(adminCtx, r.sede()))
                        .toList();
            }
            return rows;
        }
    }

    private List<MatriculaRow> listViaJdbc(int limit, boolean all, boolean includeHidden) {
        if (jdbcTemplate == null) {
            return List.of();
        }

        Set<String> cols = new LinkedHashSet<>();
        try {
            List<String> raw = jdbcTemplate.queryForList("""
                    select lower(column_name)
                    from information_schema.columns
                    where table_schema = current_schema()
                      and table_name = 'chatbot_matricula_proceso'
                    """, String.class);
            if (raw != null) cols.addAll(raw);
        } catch (Exception ex) {
            log.warn("No se pudo consultar columnas de chatbot_matricula_proceso: {}", ex.getMessage());
            return List.of();
        }
        if (cols.isEmpty()) {
            return List.of();
        }

        String colId = pickCol(cols, "id");
        String colNombre = pickCol(cols, "nombre_completo", "nombrecompleto");
        String colDoc = pickCol(cols, "numero_documento", "numerodocumento", "numeroDocumento", "numerodocumento");
        String colEmail = pickCol(cols, "email");
        String colTel = pickCol(cols, "telefono", "phone");
        String colPhone = pickCol(cols, "phone", "telefono");
        String colCategoria = pickCol(cols, "categoria");
        String colSede = pickCol(cols, "sede");
        String colOrigen = pickCol(cols, "origen_registro", "origenregistro");
        String colMetodo = pickCol(cols, "metodo_pago", "metodopago");
        String colFlow = pickCol(cols, "flow_status", "flowstatus");
        String colPaymentStatus = pickCol(cols, "payment_status", "paymentstatus");
        String colContractStatus = pickCol(cols, "contract_status", "contractstatus");
        String colCreated = pickCol(cols, "created_at", "createdat");
        String colUpdated = pickCol(cols, "updated_at", "updatedat");
        String colVisible = pickCol(cols, "visible");

        String orderBy = colUpdated != null ? colUpdated : (colCreated != null ? colCreated : (colId != null ? colId : "1"));
        List<String> conditions = new java.util.ArrayList<>();
        if (!includeHidden && colVisible != null && !colVisible.isBlank()) {
            conditions.add("coalesce(" + colVisible + ", true) = true");
        }
        if (!all) {
            List<String> cashOr = new java.util.ArrayList<>();
            if (colMetodo != null && !colMetodo.isBlank()) {
                cashOr.add("coalesce(nullif(btrim(" + colMetodo + "), ''), '') ilike 'EFECTIVO'");
            }
            if (colFlow != null && !colFlow.isBlank()) {
                cashOr.add("coalesce(nullif(btrim(" + colFlow + "), ''), '') ilike 'PENDING_CASH_VALIDATION'");
                cashOr.add("coalesce(nullif(btrim(" + colFlow + "), ''), '') ilike 'PENDING_PAYMENT'");
            }
            if (cashOr.isEmpty()) {
                return List.of();
            }
            conditions.add("(" + String.join(" or ", cashOr) + ")");
        }
        String where = conditions.isEmpty() ? "" : "\nwhere " + String.join("\n  and ", conditions) + "\n";

        String sql = """
                select
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s,
                  %s
                from chatbot_matricula_proceso
                %s
                order by %s desc
                limit ?
                """.formatted(
                expr(cols, colId, "id"),
                expr(cols, colNombre, "nombre_completo"),
                expr(cols, colDoc, "numero_documento"),
                expr(cols, colEmail, "email"),
                expr(cols, colTel, "telefono"),
                expr(cols, colPhone, "phone"),
                expr(cols, colCategoria, "categoria"),
                expr(cols, colSede, "sede"),
                expr(cols, colOrigen, "origen_registro"),
                expr(cols, colMetodo, "metodo_pago"),
                expr(cols, colPaymentStatus, "payment_status"),
                expr(cols, colContractStatus, "contract_status"),
                expr(cols, colCreated, "created_at"),
                expr(cols, colUpdated, "updated_at") + ",\n  " + expr(cols, colVisible, "visible"),
                where,
                orderBy
        );

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, Math.max(1, limit));
        } catch (Exception ex) {
            log.warn("Fallback JDBC fallo listando chatbot_matricula_proceso: {}", ex.getMessage());
            return List.of();
        }

        return rows.stream().map(this::toRowJdbc).toList();
    }

    private String pickCol(Set<String> cols, String... candidates) {
        if (cols == null || candidates == null) return null;
        for (String c : candidates) {
            String v = c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
            if (!v.isBlank() && cols.contains(v)) return v;
        }
        return null;
    }

    private String expr(Set<String> cols, String col, String alias) {
        String a = alias == null ? "" : alias.trim();
        if (a.isBlank()) {
            return "NULL";
        }
        if (col == null || col.isBlank() || cols == null || !cols.contains(col)) {
            return "NULL as " + a;
        }
        return col + " as " + a;
    }

    private MatriculaRow toRowJdbc(Map<String, Object> row) {
        if (row == null) {
            return new MatriculaRow(null, "—", "", "", "", "—", "", "", "", "", "", null, null, false, true);
        }

        Long id = toLong(row.get("id"));
        String nombre = safeUpperOrRaw(toString(row.get("nombre_completo")), false);
        String doc = toString(row.get("numero_documento"));
        String email = toString(row.get("email"));
        String telefono = firstNotBlank(toString(row.get("telefono")), toString(row.get("phone")));
        String categoria = safeUpperOrRaw(toString(row.get("categoria")), true);
        String sede = toString(row.get("sede"));
        String origen = safeUpperOrRaw(toString(row.get("origen_registro")), true);
        String metodo = safeUpperOrRaw(toString(row.get("metodo_pago")), true);
        String estadoPago = mapEstadoPago(toString(row.get("payment_status")));
        String estadoContrato = safeUpperOrRaw(toString(row.get("contract_status")), true);
        Instant createdAt = toInstant(row.get("created_at"));
        Instant updatedAt = toInstant(row.get("updated_at"));
        boolean visible = toBooleanDefaultTrue(row.get("visible"));

        boolean prospectoActivo = false;
        try {
            if (StringUtils.hasText(telefono) && prospectoService != null) {
                prospectoActivo = prospectoService.existeProspectoActivoPorTelefono(telefono);
            }
        } catch (Exception ignored) {
            prospectoActivo = false;
        }

        return new MatriculaRow(
                id,
                firstNotBlank(nombre, "—"),
                doc,
                email,
                telefono,
                firstNotBlank(categoria, "—"),
                sede,
                origen,
                metodo,
                estadoPago,
                estadoContrato,
                createdAt,
                updatedAt,
                prospectoActivo,
                visible
        );
    }

    private Instant toInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Timestamp ts) return ts.toInstant();
        if (v instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isBlank()) return null;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean toBooleanDefaultTrue(Object v) {
        if (v == null) return true;
        if (v instanceof Boolean b) return b;
        String raw = String.valueOf(v).trim();
        if (raw.isBlank()) return true;
        return Boolean.parseBoolean(raw);
    }

    private String toString(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String safeUpperOrRaw(String v, boolean toUpper) {
        String s = v == null ? "" : v.trim();
        if (s.isBlank()) return "";
        return toUpper ? s.toUpperCase(Locale.ROOT) : s;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody CrearReq req, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        if (req == null || req.estudiante() == null) {
            throw new IllegalArgumentException("estudiante es requerido");
        }

        String doc = trim(req.estudiante().numeroDocumento());
        if (!StringUtils.hasText(doc)) {
            throw new IllegalArgumentException("numeroDocumento es requerido");
        }

        String categoria = firstNotBlank(trim(req.categoria()), "");
        if (!StringUtils.hasText(categoria)) {
            throw new IllegalArgumentException("categoria es requerida");
        }

        String email = trim(req.estudiante().email());
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email es requerido");
        }

        String telefono = trim(req.estudiante().telefono());
        if (!StringUtils.hasText(telefono)) {
            throw new IllegalArgumentException("telefono es requerido");
        }

        String nombreCompleto = collapseSpaces(
                (trim(req.estudiante().nombre()) + " " + trim(req.estudiante().apellido())).trim()
        );
        if (!StringUtils.hasText(nombreCompleto)) {
            nombreCompleto = "Estudiante";
        }

        String sede = adminSedeGuard.enforceRequestSede(adminCtx, req.sede());

        // Evita que un admin "tome" solicitudes ya creadas en otra sede.
        procesoRepository.findByNumeroDocumento(doc).ifPresent(existing -> {
            if (StringUtils.hasText(trim(existing.getSede()))) {
                adminSedeGuard.assertCanAccess(adminCtx, existing.getSede());
            }
        });

        ChatbotMatriculaProceso proceso = procesoService.upsertDraft(
                telefono,
                nombreCompleto,
                doc,
                categoria,
                email,
                telefono,
                "",
                sede,
                null
        );

        String origen = StringUtils.hasText(req.origenRegistro()) ? trim(req.origenRegistro()) : "HAROGESTION";
        proceso = procesoService.setOrigenRegistro(doc, origen);

        String metodoPago = trim(req.metodoPago());
        if (StringUtils.hasText(metodoPago)) {
            proceso = procesoService.setMetodoPago(doc, metodoPago);
        }

        if (StringUtils.hasText(req.observacionPago())) {
            proceso.setPaymentObservation(trim(req.observacionPago()));
        }
        if (req.valorPagado() != null && req.valorPagado().signum() >= 0) {
            proceso.setPaymentAmount(req.valorPagado());
        }
        if (proceso.getVisible() == null) {
            proceso.setVisible(true);
        }

        String estadoPago = normalizeEstadoPago(req.estadoPago());

        // HaroGestion legacy: puede enviar estadoPago=PENDIENTE sin metodoPago.
        // En ese caso lo tratamos como EFECTIVO para que aparezca en la cola de validacion manual.
        boolean metodoPagoBlankEnReq = !StringUtils.hasText(metodoPago);
        boolean metodoPagoBlankEnProceso = !StringUtils.hasText(trim(proceso.getMetodoPago()));
        if ("PENDIENTE".equals(estadoPago)
                && ("EFECTIVO".equalsIgnoreCase(trim(proceso.getMetodoPago()))
                || (metodoPagoBlankEnReq && metodoPagoBlankEnProceso))) {
            proceso = procesoService.markCashPaymentPending(doc);
        }

        if ("CONFIRMADO".equals(estadoPago) && "EFECTIVO".equalsIgnoreCase(trim(proceso.getMetodoPago()))) {
            PaymentApprovalService.ContractSendResult out = paymentApprovalService.confirmCashPaymentManual(
                    doc,
                    req.valorPagado(),
                    adminCtx.adminId(),
                    req.observacionPago(),
                    false,
                    false,
                    true
            );
            if (!out.ok()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(out);
            }
            proceso = procesoRepository.findByNumeroDocumento(doc).orElse(proceso);
        }

        procesoRepository.save(proceso);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(proceso));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(
            path = {
                    "/{id}/confirmar-pago-manual-y-contratos",
                    "/{id}/confirmar-pago-manual",
                    "/{id}/confirmar-pago"
            },
            method = {RequestMethod.POST, RequestMethod.PATCH}
    )
    public ResponseEntity<?> confirmarPago(@PathVariable Long id,
                                          @RequestBody(required = false) ActionReq req,
                                          Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        adminSedeGuard.assertCanAccess(adminCtx, p.getSede());

        if (StringUtils.hasText(trim(p.getMetodoPago())) && !"EFECTIVO".equalsIgnoreCase(trim(p.getMetodoPago()))) {
            return ResponseEntity.badRequest().body("Esta acción solo aplica para pagos en EFECTIVO.");
        }

        boolean sendEmail = req == null || req.sendEmail() == null || req.sendEmail();
        // Default TRUE: el flujo de EFECTIVO (chatbot) espera que al confirmar el pago se envie el link por WhatsApp.
        boolean sendChatbot = req == null || req.sendChatbot() == null || req.sendChatbot();
        boolean requireProspect = req == null || req.requireProspect() == null || req.requireProspect();

        PaymentApprovalService.ContractSendResult out = paymentApprovalService.confirmCashPaymentManual(
                p.getNumeroDocumento(),
                req == null ? null : req.valorPagado(),
                adminCtx.adminId(),
                req == null ? null : req.observacionPago(),
                sendEmail,
                sendChatbot,
                requireProspect
        );
        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(
            path = {
                    "/{id}/enviar-enlace-contratos-correo",
                    "/{id}/enviar-enlace-contrato-correo",
                    "/{id}/enviar-contratos-correo",
                    "/{id}/enviar-contrato-correo",
                    "/{id}/enviarEnlaceContratosCorreo"
            },
            method = {RequestMethod.POST, RequestMethod.PATCH}
    )
    public ResponseEntity<?> enviarCorreo(@PathVariable Long id,
                                          @RequestBody(required = false) ActionReq req,
                                          Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        adminSedeGuard.assertCanAccess(adminCtx, p.getSede());

        boolean force = req != null && req.force() != null && req.force();
        PaymentApprovalService.ContractSendResult out = paymentApprovalService.sendContractLinkByEmail(p.getNumeroDocumento(), force);
        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(
            path = {
                    "/{id}/enviar-enlace-contratos-chatbot",
                    "/{id}/enviar-enlace-contrato-chatbot",
                    "/{id}/enviar-contratos-chatbot",
                    "/{id}/enviar-contrato-chatbot",
                    "/{id}/enviarEnlaceContratosChatbot"
            },
            method = {RequestMethod.POST, RequestMethod.PATCH}
    )
    public ResponseEntity<?> enviarChatbot(@PathVariable Long id,
                                           @RequestBody(required = false) ActionReq req,
                                           Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        adminSedeGuard.assertCanAccess(adminCtx, p.getSede());

        String origen = trim(p.getOrigenRegistro()).toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(origen) && !"HAROGESTION".equals(origen)) {
            return ResponseEntity.badRequest().body("El envío por chatbot desde HaroGestion solo aplica a solicitudes creadas desde HAROGESTION.");
        }

        boolean force = req != null && req.force() != null && req.force();
        boolean requireProspect = req == null || req.requireProspect() == null || req.requireProspect();
        PaymentApprovalService.ContractSendResult out = paymentApprovalService.sendContractLinkByChatbot(p.getNumeroDocumento(), requireProspect, force);
        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminarSolicitud(@PathVariable Long id, Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        ChatbotMatriculaProceso proceso = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        adminSedeGuard.assertCanAccess(adminCtx, proceso.getSede());

        proceso.setVisible(false);
        procesoRepository.save(proceso);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Solicitud ocultada",
                "visible", false,
                "id", id
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/visible")
    @Transactional
    public ResponseEntity<?> actualizarVisibilidad(@PathVariable Long id,
                                                   @RequestBody(required = false) VisibilityReq req,
                                                   Authentication authentication) {
        AdminSedeGuard.AdminCtx adminCtx = adminSedeGuard.resolve(authentication);
        ChatbotMatriculaProceso proceso = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        adminSedeGuard.assertCanAccess(adminCtx, proceso.getSede());

        boolean visible = req == null || req.visible() == null || req.visible();
        proceso.setVisible(visible);
        procesoRepository.save(proceso);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", visible ? "Solicitud visible" : "Solicitud oculta",
                "visible", visible,
                "id", id
        ));
    }

    private MatriculaRow toRow(ChatbotMatriculaProceso p) {
        String phone = firstNotBlank(trim(p.getPhone()), trim(p.getTelefono()));
        boolean prospectoActivo = false;
        try {
            if (StringUtils.hasText(phone)) {
                prospectoActivo = prospectoService.existeProspectoActivoPorTelefono(phone);
            }
        } catch (Exception ignored) {
            prospectoActivo = false;
        }

        return new MatriculaRow(
                p.getId(),
                firstNotBlank(trim(p.getNombreCompleto()), "—"),
                trim(p.getNumeroDocumento()),
                trim(p.getEmail()),
                firstNotBlank(trim(p.getTelefono()), trim(p.getPhone())),
                trim(p.getCategoria()).toUpperCase(Locale.ROOT),
                trim(p.getSede()),
                trim(p.getOrigenRegistro()).toUpperCase(Locale.ROOT),
                trim(p.getMetodoPago()).toUpperCase(Locale.ROOT),
                mapEstadoPago(p.getPaymentStatus()),
                trim(p.getContractStatus()).toUpperCase(Locale.ROOT),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                prospectoActivo,
                !Boolean.FALSE.equals(p.getVisible())
        );
    }

    private String mapEstadoPago(String raw) {
        String status = trim(raw).toUpperCase(Locale.ROOT);
        if (status.isBlank()) return "PENDIENTE";
        return switch (status) {
            case "APPROVED", "PAID", "CONFIRMED", "OK" -> "CONFIRMADO";
            case "REJECTED" -> "RECHAZADO";
            case "CANCELLED" -> "CANCELADO";
            case "PENDING" -> "PENDIENTE";
            default -> status;
        };
    }

    private String normalizeEstadoPago(String raw) {
        String v = trim(raw).toUpperCase(Locale.ROOT);
        if (v.isBlank()) return "PENDIENTE";
        if (v.startsWith("CONF")) return "CONFIRMADO";
        if (v.startsWith("PEND")) return "PENDIENTE";
        if ("APROBADO".equals(v) || "APPROVED".equals(v) || "PAID".equals(v)) return "CONFIRMADO";
        return v;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String collapseSpaces(String value) {
        String v = trim(value);
        if (v.isBlank()) return "";
        return v.replaceAll("\\s+", " ");
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            String t = trim(v);
            if (!t.isBlank()) return t;
        }
        return "";
    }
}
