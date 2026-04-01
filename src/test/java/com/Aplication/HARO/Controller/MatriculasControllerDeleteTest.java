package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import com.Aplication.HARO.Service.ProspectoService;
import com.Aplication.HARO.Service.ServicioLimpiezaSolicitudesEfectivo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MatriculasControllerDeleteTest {

    @Test
    void eliminarSolicitudDebeOcultarlaSinImportarEstadoPago() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(42L);
        proceso.setPaymentStatus("APPROVED");
        proceso.setVisible(true);

        when(procesoRepository.findById(42L)).thenReturn(Optional.of(proceso));
        when(procesoRepository.save(any(ChatbotMatriculaProceso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        ResponseEntity<?> response = controller.eliminarSolicitud(42L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Solicitud ocultada", "visible", false, "id", 42L), response.getBody());
        assertEquals(Boolean.FALSE, proceso.getVisible());
        verify(procesoRepository).save(proceso);
    }

    @Test
    void actualizarVisibilidadDebePermitirMostrarDeNuevo() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(7L);
        proceso.setVisible(false);

        when(procesoRepository.findById(7L)).thenReturn(Optional.of(proceso));
        when(procesoRepository.save(any(ChatbotMatriculaProceso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        ResponseEntity<?> response = controller.actualizarVisibilidad(7L, new MatriculasController.VisibilityReq(true));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Solicitud visible", "visible", true, "id", 7L), response.getBody());
        assertEquals(Boolean.TRUE, proceso.getVisible());
        verify(procesoRepository).save(proceso);
    }

    @Test
    void eliminarSolicitudDebeResponder404SiNoExiste() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(procesoRepository.findById(99L)).thenReturn(Optional.empty());

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        assertThrows(NoSuchElementException.class, () -> controller.eliminarSolicitud(99L));
        verify(procesoRepository, never()).save(any());
    }
}
