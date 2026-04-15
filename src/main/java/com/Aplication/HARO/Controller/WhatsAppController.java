package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.EstudianteService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para WhatsApp.
 */
@RestController
@RequestMapping("/api/whatsapp")
@CrossOrigin(origins = "*")
public class WhatsAppController {

    private final WhatsAppTemplateService service;
    private final EstudianteService estudianteService;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public WhatsAppController(WhatsAppTemplateService service, EstudianteService estudianteService) {
        this.service = service;
        this.estudianteService = estudianteService;
    }

    /**
     * DTO de entrada para send template.
     */
    public record SendTemplateReq(
            String studentEmail,
            String templateName,
            String to,
            String languageCode,
            List<String> params
    ) {}

    /**
     * DTO de entrada para send text.
     */
    public record SendTextReq(
            String to,
            String text
    ) {}

    /**
     * DTO de entrada para send image.
     */
    public record SendImageReq(
            String to,
            String imageUrl
    ) {}

/**
 * Consulta el estado del controlador o servicio asociado.
 */
    @GetMapping("/template/status")
    public ResponseEntity<WhatsAppTemplateService.ConfigStatus> status() {
        return ResponseEntity.ok(service.getConfigStatus());
    }

/**
 * Envia una plantilla de WhatsApp.
 */
    @PostMapping("/template/send")
    public ResponseEntity<WhatsAppTemplateService.SendResult> sendTemplate(@RequestBody SendTemplateReq req) {
        if (StringUtils.hasText(req.studentEmail()) && !estudianteService.existePorCorreo(req.studentEmail().trim())) {
            throw new IllegalArgumentException("No existe estudiante con el correo: " + req.studentEmail());
        }

        WhatsAppTemplateService.SendResult out = service.sendTemplate(
                req.templateName(),
                req.to(),
                req.languageCode(),
                req.params()
        );
        return ResponseEntity.ok(out);
    }

/**
 * Envia un mensaje de texto por WhatsApp.
 */
    @PostMapping("/text/send")
    public ResponseEntity<WhatsAppTemplateService.SendResult> sendText(@RequestBody SendTextReq req) {
        return ResponseEntity.ok(service.sendTextMessage(req.to(), req.text()));
    }

/**
 * Envia una imagen por WhatsApp.
 */
    @PostMapping("/image/send")
    public ResponseEntity<WhatsAppTemplateService.SendResult> sendImage(@RequestBody SendImageReq req) {
        return ResponseEntity.ok(service.sendImageMessage(req.to(), req.imageUrl()));
    }
}
