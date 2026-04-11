package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Security.AdminSedeGuard;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import com.Aplication.HARO.Service.ProspectoService;
import com.Aplication.HARO.Service.ServicioLimpiezaSolicitudesEfectivo;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatriculasControllerListTest {

    @Test
    void listNormalDebeExcluirProcesosYaFinalizados() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentApprovalService paymentApprovalService = mock(PaymentApprovalService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        ServicioLimpiezaSolicitudesEfectivo cleanupService = mock(ServicioLimpiezaSolicitudesEfectivo.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AdminSedeGuard adminSedeGuard = mock(AdminSedeGuard.class);
        Authentication authentication = mock(Authentication.class);
        AdminSedeGuard.AdminCtx adminCtx = new AdminSedeGuard.AdminCtx(1L, "", "", true);

        ChatbotMatriculaProceso pendiente = new ChatbotMatriculaProceso();
        pendiente.setId(1L);
        pendiente.setNombreCompleto("Pendiente");
        pendiente.setNumeroDocumento("111");
        pendiente.setMetodoPago("EFECTIVO");
        pendiente.setFlowStatus("PENDING_CASH_VALIDATION");
        pendiente.setPaymentStatus("PENDING");
        pendiente.setVisible(true);

        ChatbotMatriculaProceso finalizado = new ChatbotMatriculaProceso();
        finalizado.setId(2L);
        finalizado.setNombreCompleto("Finalizado");
        finalizado.setNumeroDocumento("222");
        finalizado.setMetodoPago("EFECTIVO");
        finalizado.setFlowStatus("STUDENT_CREATED");
        finalizado.setContractStatus("SIGNED");
        finalizado.setPaymentStatus("APPROVED");
        finalizado.setStudentId(88L);
        finalizado.setVisible(true);

        when(adminSedeGuard.resolve(authentication)).thenReturn(adminCtx);
        when(procesoRepository.findByVisibleTrueAndMetodoPagoIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCase(
                any(String.class), any(String.class), any(String.class), any(Pageable.class)
        )).thenReturn(List.of(pendiente, finalizado));

        MatriculasController controller = new MatriculasController(
                procesoRepository,
                procesoService,
                paymentApprovalService,
                prospectoService,
                cleanupService,
                jdbcTemplate,
                adminSedeGuard
        );

        List<MatriculasController.MatriculaRow> out = controller.list(100, false, false, authentication);

        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).id());
        verify(procesoRepository)
                .findByVisibleTrueAndMetodoPagoIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCaseOrVisibleTrueAndFlowStatusIgnoreCase(
                        any(String.class), any(String.class), any(String.class), any(Pageable.class)
                );
    }
}
