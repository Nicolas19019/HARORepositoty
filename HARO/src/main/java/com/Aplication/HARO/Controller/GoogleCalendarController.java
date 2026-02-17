package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = "*")
public class GoogleCalendarController {

    private final GoogleCalendarService service;

    public GoogleCalendarController(GoogleCalendarService service) {
        this.service = service;
    }

    public record CrearReunionReq(
            String titulo,
            String descripcion,
            String inicio,
            String fin,
            String zonaHoraria,
            String calendarId,
            List<String> asistentes
    ) {}

    @PostMapping("/reuniones")
    public ResponseEntity<GoogleCalendarService.ReunionCreada> crearReunion(@RequestBody CrearReunionReq req) {
        GoogleCalendarService.ReunionCreada created = service.crearReunion(
                req.titulo(),
                req.descripcion(),
                req.inicio(),
                req.fin(),
                req.zonaHoraria(),
                req.asistentes(),
                req.calendarId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
