package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollment")
public class EnrollmentController {

    private final ChatbotProcesoService procesoService;
    private final PaymentApprovalService paymentApprovalService;

    public EnrollmentController(ChatbotProcesoService procesoService,
                                PaymentApprovalService paymentApprovalService) {
        this.procesoService = procesoService;
        this.paymentApprovalService = paymentApprovalService;
    }

    public record UpsertProcesoReq(
            String phone,
            String nombreCompleto,
            @NotBlank String documento,
            @NotBlank String categoria,
            @NotBlank String email,
            @NotBlank String telefono,
            String direccion,
            String sede,
            String origenRegistro,
            String metodoPago
    ) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/process/upsert")
    public ResponseEntity<?> upsertProceso(@RequestBody UpsertProcesoReq req) {
        ChatbotMatriculaProceso proceso = procesoService.upsertDraft(
                req.phone(),
                req.nombreCompleto(),
                req.documento(),
                req.categoria(),
                req.email(),
                req.telefono(),
                req.direccion() == null ? "" : req.direccion(),
                req.sede() == null ? "" : req.sede()
        );

        // Por defecto, si viene de este endpoint asumimos HAROGESTION.
        String origen = StringUtils.hasText(req.origenRegistro()) ? req.origenRegistro() : "HAROGESTION";
        proceso = procesoService.setOrigenRegistro(req.documento(), origen);

        if (StringUtils.hasText(req.metodoPago())) {
            proceso = procesoService.setMetodoPago(req.documento(), req.metodoPago());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("id", proceso.getId());
        body.put("documento", proceso.getNumeroDocumento());
        body.put("email", proceso.getEmail());
        body.put("telefono", proceso.getTelefono());
        body.put("sede", proceso.getSede());
        body.put("origenRegistro", origen);
        body.put("metodoPago", proceso.getMetodoPago());
        body.put("paymentStatus", proceso.getPaymentStatus());
        body.put("flowStatus", proceso.getFlowStatus());
        return ResponseEntity.ok(body);
    }

    public record ManualCashConfirmReq(
            @NotBlank String documento,
            BigDecimal valorPagado,
            Long adminId,
            String observacion,
            Boolean sendEmail,
            Boolean sendChatbot,
            Boolean requireProspect
    ) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/payment/cash/confirm")
    public ResponseEntity<?> confirmCash(@RequestBody ManualCashConfirmReq req) {
        boolean sendEmail = req.sendEmail() == null || req.sendEmail();
        boolean sendChatbot = req.sendChatbot() != null && req.sendChatbot();
        boolean requireProspect = req.requireProspect() == null || req.requireProspect();

        PaymentApprovalService.ContractSendResult out = paymentApprovalService.confirmCashPaymentManual(
                req.documento(),
                req.valorPagado(),
                req.adminId(),
                req.observacion(),
                sendEmail,
                sendChatbot,
                requireProspect
        );

        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }

    public record SendContractReq(
            @NotBlank String documento,
            Boolean force,
            Boolean requireProspect
    ) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/contracts/email/send")
    public ResponseEntity<?> sendContractEmail(@RequestBody SendContractReq req) {
        boolean force = req.force() != null && req.force();
        PaymentApprovalService.ContractSendResult out = paymentApprovalService.sendContractLinkByEmail(req.documento(), force);
        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/contracts/chatbot/send")
    public ResponseEntity<?> sendContractChatbot(@RequestBody SendContractReq req) {
        boolean force = req.force() != null && req.force();
        boolean requireProspect = req.requireProspect() == null || req.requireProspect();
        PaymentApprovalService.ContractSendResult out = paymentApprovalService.sendContractLinkByChatbot(req.documento(), requireProspect, force);
        HttpStatus status = out.ok() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(out);
    }
}
