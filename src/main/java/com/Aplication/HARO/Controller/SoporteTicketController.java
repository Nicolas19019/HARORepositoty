package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.SoporteTicket;
import com.Aplication.HARO.Service.SoporteTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class SoporteTicketController {

    public record CreateTicketRequest(
            String tipo,
            String mensaje,
            String adjuntoNombre,
            String adjuntoUrl,
            String emailEstudiante,
            String nombreEstudiante
    ) {}

    public record UpdateTicketRequest(
            String estado,
            String notaInterna,
            String responsable
    ) {}

    private final SoporteTicketService service;

    public SoporteTicketController(SoporteTicketService service) {
        this.service = service;
    }

    @PostMapping("/api/soporte/tickets")
    public ResponseEntity<SoporteTicket> crear(@RequestBody CreateTicketRequest request) {
        SoporteTicket created = service.crear(new SoporteTicketService.CreateRequest(
                request == null ? null : request.tipo(),
                request == null ? null : request.mensaje(),
                request == null ? null : request.adjuntoNombre(),
                request == null ? null : request.adjuntoUrl(),
                request == null ? null : request.emailEstudiante(),
                request == null ? null : request.nombreEstudiante()
        ));
        return ResponseEntity.created(URI.create("/api/admin/soporte/tickets/" + created.getId())).body(created);
    }

    @GetMapping("/api/admin/soporte/tickets")
    public List<SoporteTicket> listarAdmin(@RequestParam(required = false) String estado,
                                           @RequestParam(required = false) String tipo,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(required = false) String email,
                                           @RequestParam(required = false, name = "q") String query) {
        return service.listarAdmin(estado, tipo, from, to, email, query);
    }

    @GetMapping("/api/admin/soporte/tickets/resumen")
    public SoporteTicketService.TicketResumen resumenAdmin() {
        return service.getResumenAdmin();
    }

    @GetMapping("/api/admin/soporte/tickets/count/open")
    public Map<String, Object> countOpen() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", service.countAbiertos());
        return body;
    }

    @PatchMapping("/api/admin/soporte/tickets/{id}")
    public SoporteTicket actualizarAdmin(@PathVariable Long id,
                                         @RequestBody UpdateTicketRequest request) {
        return service.actualizarAdmin(id, new SoporteTicketService.AdminUpdateRequest(
                request == null ? null : request.estado(),
                request == null ? null : request.notaInterna(),
                request == null ? null : request.responsable()
        ));
    }
}
