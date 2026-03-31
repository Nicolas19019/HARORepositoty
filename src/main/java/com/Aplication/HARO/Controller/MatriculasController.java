package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import com.Aplication.HARO.Service.ProspectoService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

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

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final ChatbotProcesoService procesoService;
    private final PaymentApprovalService paymentApprovalService;
    private final ProspectoService prospectoService;

    public MatriculasController(ChatbotMatriculaProcesoRepository procesoRepository,
                                ChatbotProcesoService procesoService,
                                PaymentApprovalService paymentApprovalService,
                                ProspectoService prospectoService) {
        this.procesoRepository = procesoRepository;
        this.procesoService = procesoService;
        this.paymentApprovalService = paymentApprovalService;
        this.prospectoService = prospectoService;
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
            boolean prospectoChatbotActivo
    ) {
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<MatriculaRow> list(@RequestParam(value = "limit", defaultValue = "500") int limit) {
        int size = Math.max(1, Math.min(limit, 2000));
        List<ChatbotMatriculaProceso> items = procesoRepository.findAll(
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).getContent();

        return items.stream().map(this::toRow).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody CrearReq req) {
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

        String sede = trim(req.sede());

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

        // Campos informativos (no confirman pago).
        if (StringUtils.hasText(req.observacionPago())) {
            proceso.setPaymentObservation(trim(req.observacionPago()));
        }
        if (req.valorPagado() != null && req.valorPagado().signum() >= 0) {
            proceso.setPaymentAmount(req.valorPagado());
        }

        String estadoPago = normalizeEstadoPago(req.estadoPago());

        // Si el admin lo registra como EFECTIVO + PENDIENTE, queda en validación manual.
        if ("PENDIENTE".equals(estadoPago) && "EFECTIVO".equalsIgnoreCase(trim(proceso.getMetodoPago()))) {
            proceso = procesoService.markCashPaymentPending(doc);
        }

        // Si lo registra como EFECTIVO + CONFIRMADO, deja el pago aprobado y habilita contratos,
        // pero NO envía por correo/chatbot automáticamente (eso se hace con las acciones).
        if ("CONFIRMADO".equals(estadoPago) && "EFECTIVO".equalsIgnoreCase(trim(proceso.getMetodoPago()))) {
            PaymentApprovalService.ContractSendResult out = paymentApprovalService.confirmCashPaymentManual(
                    doc,
                    req.valorPagado(),
                    null,
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
    public ResponseEntity<?> confirmarPago(@PathVariable Long id, @RequestBody(required = false) ActionReq req) {
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

        if (StringUtils.hasText(trim(p.getMetodoPago())) && !"EFECTIVO".equalsIgnoreCase(trim(p.getMetodoPago()))) {
            return ResponseEntity.badRequest().body("Esta acción solo aplica para pagos en EFECTIVO.");
        }

        boolean sendEmail = req == null || req.sendEmail() == null || req.sendEmail();
        boolean sendChatbot = req != null && req.sendChatbot() != null && req.sendChatbot();
        boolean requireProspect = req == null || req.requireProspect() == null || req.requireProspect();

        PaymentApprovalService.ContractSendResult out = paymentApprovalService.confirmCashPaymentManual(
                p.getNumeroDocumento(),
                req == null ? null : req.valorPagado(),
                req == null ? null : req.validadoPorAdminId(),
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
    public ResponseEntity<?> enviarCorreo(@PathVariable Long id, @RequestBody(required = false) ActionReq req) {
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

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
    public ResponseEntity<?> enviarChatbot(@PathVariable Long id, @RequestBody(required = false) ActionReq req) {
        ChatbotMatriculaProceso p = procesoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));

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
                prospectoActivo
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

