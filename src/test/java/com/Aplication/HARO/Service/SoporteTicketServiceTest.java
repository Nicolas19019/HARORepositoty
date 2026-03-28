package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.SoporteTicketSchemaInitializer;
import com.Aplication.HARO.Model.SoporteTicket;
import com.Aplication.HARO.Repository.SoporteTicketRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoporteTicketServiceTest {

    @Test
    void crearDebeGuardarPendienteConDatosNormalizados() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        when(repository.save(any(SoporteTicket.class))).thenAnswer(invocation -> {
            SoporteTicket ticket = invocation.getArgument(0);
            ticket.setId(10L);
            ticket.setCreatedAt(Instant.parse("2026-03-27T14:00:00Z"));
            ticket.setUpdatedAt(Instant.parse("2026-03-27T14:00:00Z"));
            return ticket;
        });

        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        SoporteTicket saved = service.crear(new SoporteTicketService.CreateRequest(
                "TECNICO",
                "  No puedo entrar al simulacro  ",
                "captura.png",
                null,
                "ESTUDIANTE@CORREO.COM ",
                " Ana Perez "
        ));

        assertEquals(10L, saved.getId());
        assertEquals("tecnico", saved.getTipo());
        assertEquals("pendiente", saved.getEstado());
        assertEquals("estudiante@correo.com", saved.getEmailEstudiante());
        assertEquals("Ana Perez", saved.getNombreEstudiante());
        assertNull(saved.getNotaInterna());
        verify(initializer).ensureSchema();
    }

    @Test
    void listarAdminDebeAplicarFiltros() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(
                build(1L, "pendiente", "tecnico", "ana@correo.com", "Ana Perez", "No entra al curso", Instant.parse("2026-03-27T15:00:00Z")),
                build(2L, "resuelto", "acceso", "luis@correo.com", "Luis Ruiz", "No llega el OTP", Instant.parse("2026-03-20T15:00:00Z"))
        ));

        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        List<SoporteTicket> rows = service.listarAdmin(
                "pendiente",
                "tecnico",
                LocalDate.of(2026, 3, 27),
                LocalDate.of(2026, 3, 27),
                "ana@correo.com",
                "curso"
        );

        assertEquals(1, rows.size());
        assertEquals(1L, rows.get(0).getId());
        verify(initializer).ensureSchema();
    }

    @Test
    void listarAdminDebeFallarSiRangoEsInvalido() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        assertThrows(IllegalArgumentException.class,
                () -> service.listarAdmin(null, null,
                        LocalDate.of(2026, 3, 28),
                        LocalDate.of(2026, 3, 27),
                        null, null));
        verify(initializer).ensureSchema();
    }

    @Test
    void actualizarAdminDebeCambiarEstadoNotaYResponsable() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        SoporteTicket existing = build(8L, "pendiente", "acceso", "ana@correo.com", "Ana Perez",
                "No puedo entrar", Instant.parse("2026-03-27T15:00:00Z"));
        when(repository.findById(8L)).thenReturn(Optional.of(existing));
        when(repository.save(any(SoporteTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        SoporteTicket updated = service.actualizarAdmin(8L,
                new SoporteTicketService.AdminUpdateRequest(
                        "en_progreso",
                        "Se solicito evidencia adicional",
                        "Equipo LMS"
                ));

        assertEquals("en_progreso", updated.getEstado());
        assertEquals("Se solicito evidencia adicional", updated.getNotaInterna());
        assertEquals("Equipo LMS", updated.getResponsable());
        verify(repository).save(existing);
        verify(initializer).ensureSchema();
    }

    @Test
    void resumenAdminDebeResponderConCerosSiNoHayTickets() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        when(repository.count()).thenReturn(0L);
        when(repository.countByEstadoIn(any())).thenReturn(0L);

        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        SoporteTicketService.TicketResumen resumen = service.getResumenAdmin();

        assertEquals(0L, resumen.total());
        assertEquals(0L, resumen.abiertos());
        assertEquals(0L, resumen.pendientes());
        assertEquals(0L, resumen.enProgreso());
        assertEquals(0L, resumen.resueltos());
        assertEquals(0L, resumen.archivados());
        verify(initializer).ensureSchema();
    }

    @Test
    void listarAdminDebeResponderVacioSiNoHayTickets() {
        SoporteTicketRepository repository = mock(SoporteTicketRepository.class);
        SoporteTicketSchemaInitializer initializer = mock(SoporteTicketSchemaInitializer.class);
        when(repository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());

        SoporteTicketService service = new SoporteTicketService(repository, initializer);

        List<SoporteTicket> rows = service.listarAdmin(null, null, null, null, null, null);

        assertEquals(0, rows.size());
        verify(initializer).ensureSchema();
        verify(repository, never()).save(any());
    }

    private SoporteTicket build(Long id,
                                String estado,
                                String tipo,
                                String email,
                                String nombre,
                                String mensaje,
                                Instant createdAt) {
        SoporteTicket ticket = new SoporteTicket();
        ticket.setId(id);
        ticket.setEstado(estado);
        ticket.setTipo(tipo);
        ticket.setEmailEstudiante(email);
        ticket.setNombreEstudiante(nombre);
        ticket.setMensaje(mensaje);
        ticket.setCreatedAt(createdAt);
        ticket.setUpdatedAt(createdAt);
        return ticket;
    }
}
