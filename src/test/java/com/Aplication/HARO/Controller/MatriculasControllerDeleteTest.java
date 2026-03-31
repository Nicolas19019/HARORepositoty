package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotContractCategoryProgressRepository;
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
    void eliminarSolicitudDebeResponderOkYEliminarDependencias() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotContractCategoryProgressRepository progressRepository = mock(ChatbotContractCategoryProgressRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(42L);
        proceso.setPaymentStatus("PENDING");
        proceso.setContractStatus("PENDING_SIGNATURE");

        when(procesoRepository.findById(42L)).thenReturn(Optional.of(proceso));

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                progressRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        ResponseEntity<?> response = controller.eliminarSolicitud(42L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Solicitud eliminada", "id", 42L), response.getBody());
        verify(progressRepository).deleteByProcesoId(42L);
        verify(procesoRepository).delete(proceso);
    }

    @Test
    void eliminarSolicitudDebeFallarSiPagoYaFueConfirmado() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotContractCategoryProgressRepository progressRepository = mock(ChatbotContractCategoryProgressRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(7L);
        proceso.setPaymentStatus("APPROVED");

        when(procesoRepository.findById(7L)).thenReturn(Optional.of(proceso));

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                progressRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        ResponseEntity<?> response = controller.eliminarSolicitud(7L);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(Map.of("ok", false, "message", "No se puede eliminar una solicitud con pago confirmado.", "id", 7L), response.getBody());
        verify(progressRepository, never()).deleteByProcesoId(anyLong());
        verify(procesoRepository, never()).delete(any());
    }

    @Test
    void eliminarSolicitudDebeResponder404SiNoExiste() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotContractCategoryProgressRepository progressRepository = mock(ChatbotContractCategoryProgressRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(procesoRepository.findById(99L)).thenReturn(Optional.empty());

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                progressRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate
        );

        assertThrows(NoSuchElementException.class, () -> controller.eliminarSolicitud(99L));
        verify(progressRepository, never()).deleteByProcesoId(anyLong());
        verify(procesoRepository, never()).delete(any());
    }
}
