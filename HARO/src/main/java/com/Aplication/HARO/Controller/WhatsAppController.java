package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.EstudianteService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whatsapp")
@CrossOrigin(origins = "*")
public class WhatsAppController {

    private final WhatsAppTemplateService service;
    private final EstudianteService estudianteService;

    public WhatsAppController(WhatsAppTemplateService service, EstudianteService estudianteService) {
        this.service = service;
        this.estudianteService = estudianteService;
    }

    public record SendTemplateReq(
            String studentEmail,
            String templateName,
            String to,
            String languageCode,
            List<String> params
    ) {}

    public record SendTextReq(
            String to,
            String text
    ) {}

    public record SendImageReq(
            String to,
            String imageUrl
    ) {}

    @GetMapping("/template/status")
    public ResponseEntity<WhatsAppTemplateService.ConfigStatus> status() {
        return ResponseEntity.ok(service.getConfigStatus());
    }

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

    @PostMapping("/text/send")
    public ResponseEntity<WhatsAppTemplateService.SendResult> sendText(@RequestBody SendTextReq req) {
        return ResponseEntity.ok(service.sendTextMessage(req.to(), req.text()));
    }

    @PostMapping("/image/send")
    public ResponseEntity<WhatsAppTemplateService.SendResult> sendImage(@RequestBody SendImageReq req) {
        return ResponseEntity.ok(service.sendImageMessage(req.to(), req.imageUrl()));
    }
}
