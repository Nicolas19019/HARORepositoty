package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para google calendar.
 */
@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = "*")
public class GoogleCalendarController {

    private final GoogleCalendarService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public GoogleCalendarController(GoogleCalendarService service) {
        this.service = service;
    }

    /**
     * DTO de entrada para crear reunion.
     */
    public record CrearReunionReq(
            String titulo,
            String descripcion,
            String inicio,
            String fin,
            String zonaHoraria,
            String calendarId,
            List<String> asistentes,
            Boolean crearMeet
    ) {}

/**
 * Crea una reunion en Google Calendar.
 */
    @PostMapping("/reuniones")
    public ResponseEntity<GoogleCalendarService.ReunionCreada> crearReunion(@RequestBody CrearReunionReq req) {
        boolean crearMeet = req.crearMeet() != null && req.crearMeet();
        GoogleCalendarService.ReunionCreada created = service.crearReunion(
                req.titulo(),
                req.descripcion(),
                req.inicio(),
                req.fin(),
                req.zonaHoraria(),
                req.asistentes(),
                req.calendarId(),
                crearMeet
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
