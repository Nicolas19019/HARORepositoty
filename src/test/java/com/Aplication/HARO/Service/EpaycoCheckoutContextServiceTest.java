package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpaycoCheckoutContextServiceTest {

    @Test
    void resolveFromFlowId_shouldUseStoredProcessData() {
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        EpaycoCheckoutContextService service = new EpaycoCheckoutContextService(chatbotProcesoService);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(7L);
        proceso.setNombreCompleto("Nicolas Machado");
        proceso.setNumeroDocumento("12345678");
        proceso.setEmail("nicolasmachado422@gmail.com");
        proceso.setTelefono("573144899708");
        proceso.setPhone("573000000000");
        proceso.setCategoria("A2");
        proceso.setExpectedAmount(new BigDecimal("5000"));
        proceso.setPaymentLink("https://payco.link/test?x_id_invoice=FLOW-7");

        when(chatbotProcesoService.findProcesoById(7L)).thenReturn(Optional.of(proceso));
        when(chatbotProcesoService.setPaymentLinkIfMissing("12345678")).thenReturn(proceso);

        EpaycoCheckoutContextService.CheckoutContext out = service.resolveFromFlowId(7L);

        assertEquals(7L, out.flowId());
        assertEquals("Nicolas Machado", out.buyerName());
        assertEquals("12345678", out.document());
        assertEquals("nicolasmachado422@gmail.com", out.email());
        assertEquals("573144899708", out.phone());
        assertEquals("A2", out.category());
        assertEquals(new BigDecimal("5000"), out.amount());
        assertEquals("FLOW-7", out.invoice());
        assertEquals("https://payco.link/test?x_id_invoice=FLOW-7", out.paymentLink());
    }

    @Test
    void resolveFromFlowId_shouldFailWhenProcessIsIncomplete() {
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        EpaycoCheckoutContextService service = new EpaycoCheckoutContextService(chatbotProcesoService);

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(9L);
        proceso.setNombreCompleto(" ");
        proceso.setNumeroDocumento(" ");
        proceso.setEmail(" ");
        proceso.setTelefono(" ");
        proceso.setPhone(" ");

        when(chatbotProcesoService.findProcesoById(9L)).thenReturn(Optional.of(proceso));

        EpaycoCheckoutContextService.IncompleteProcessException ex = assertThrows(
                EpaycoCheckoutContextService.IncompleteProcessException.class,
                () -> service.resolveFromFlowId(9L)
        );

        assertEquals(9L, ex.getFlowId());
        assertEquals(4, ex.getMissingFields().size());
    }

    @Test
    void resolveFromFlowId_shouldFailWhenProcessDoesNotExist() {
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        EpaycoCheckoutContextService service = new EpaycoCheckoutContextService(chatbotProcesoService);

        when(chatbotProcesoService.findProcesoById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.resolveFromFlowId(99L));
    }
}
