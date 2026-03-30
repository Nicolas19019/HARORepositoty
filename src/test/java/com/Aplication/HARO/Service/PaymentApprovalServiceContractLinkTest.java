package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentApprovalServiceContractLinkTest {

    @Test
    void handleApprovedPayment_shouldSendWhatsAppOnlyOnce_whenLinkSent() {
        ChatbotMatriculaProcesoRepository repo = mock(ChatbotMatriculaProcesoRepository.class);
        VerificationService verificationService = mock(VerificationService.class);
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        MailService mailService = mock(MailService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        PaymentApprovalService service = new PaymentApprovalService(
                repo,
                verificationService,
                chatbotProcesoService,
                waService,
                prospectoService,
                mailService,
                restTemplate
        );

        ReflectionTestUtils.setField(service, "contractBaseUrl", "https://harorepositoty2-590358146556.europe-west1.run.app");
        ReflectionTestUtils.setField(service, "contractUiUrl", "https://ceaharo.com/Contratos/contrato.html");
        ReflectionTestUtils.setField(service, "autoSendContractOnPayment", true);
        ReflectionTestUtils.setField(service, "internalApiBaseUrl", "");

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(1L);
        proceso.setNumeroDocumento("123");
        proceso.setEmail("test@example.com");
        proceso.setTelefono("573001112233");
        proceso.setPaymentStatus("PENDING");
        proceso.setFlowStatus("DRAFT");
        proceso.setContractStatus("PENDING_SIGNATURE");
        proceso.setContractLink("");

        when(repo.findByNumeroDocumentoForUpdate("123")).thenReturn(Optional.of(proceso));
        when(repo.save(any(ChatbotMatriculaProceso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant expiresAt = Instant.parse("2026-03-30T15:00:00Z");
        when(verificationService.createContractVerificationLink(eq("test@example.com"), any()))
                .thenReturn(new VerificationService.ContractLinkResult(
                        "test@example.com",
                        "CODE1",
                        "https://example.run.app/api/verification/contract/verify?email=test%40example.com&code=CODE1",
                        expiresAt
                ));
        when(verificationService.peekContractAccessCode("test@example.com", "CODE1"))
                .thenReturn(new VerificationService.ContractAccessResult(true, "Codigo valido", expiresAt));

        service.handleApprovedPayment("123", new BigDecimal("100000"));
        service.handleApprovedPayment("123", new BigDecimal("100000")); // duplicate webhook / retry

        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(waService, times(1)).sendTextMessage(toCaptor.capture(), msgCaptor.capture());

        assertEquals("573001112233", toCaptor.getValue());
        String msg = msgCaptor.getValue();
        assertTrue(msg.contains("Tu pago fue aprobado"));
        assertTrue(msg.contains("ceaharo.com/Contratos/contrato.html"));
        assertTrue(msg.contains("Vigente hasta") || msg.contains("vence en 15 minutos"));
        assertTrue(msg.contains("LINK"));
    }

    @Test
    void handleApprovedPayment_duplicate_shouldRefreshExpiredLinkButNotResendWhatsApp() {
        ChatbotMatriculaProcesoRepository repo = mock(ChatbotMatriculaProcesoRepository.class);
        VerificationService verificationService = mock(VerificationService.class);
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        MailService mailService = mock(MailService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        PaymentApprovalService service = new PaymentApprovalService(
                repo,
                verificationService,
                chatbotProcesoService,
                waService,
                prospectoService,
                mailService,
                restTemplate
        );

        ReflectionTestUtils.setField(service, "contractBaseUrl", "https://harorepositoty2-590358146556.europe-west1.run.app");
        ReflectionTestUtils.setField(service, "contractUiUrl", "https://ceaharo.com/Contratos/contrato.html");
        ReflectionTestUtils.setField(service, "autoSendContractOnPayment", true);
        ReflectionTestUtils.setField(service, "internalApiBaseUrl", "");

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(2L);
        proceso.setNumeroDocumento("999");
        proceso.setEmail("test@example.com");
        proceso.setTelefono("573009998877");
        proceso.setPaymentStatus("APPROVED");
        proceso.setFlowStatus("PAID");
        proceso.setContractStatus("LINK_SENT");
        proceso.setContractLink("https://ceaharo.com/Contratos/contrato.html?email=test%40example.com&code=OLD");

        when(repo.findByNumeroDocumentoForUpdate("999")).thenReturn(Optional.of(proceso));
        when(repo.save(any(ChatbotMatriculaProceso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Old code is expired -> should refresh stored link
        when(verificationService.peekContractAccessCode("test@example.com", "OLD"))
                .thenReturn(new VerificationService.ContractAccessResult(false, "Codigo vencido", Instant.parse("2026-03-30T15:00:00Z")));

        when(verificationService.createContractVerificationLink(eq("test@example.com"), any()))
                .thenReturn(new VerificationService.ContractLinkResult(
                        "test@example.com",
                        "NEW",
                        "https://example.run.app/api/verification/contract/verify?email=test%40example.com&code=NEW",
                        Instant.parse("2026-03-30T15:10:00Z")
                ));

        service.handleApprovedPayment("999", new BigDecimal("100000"));

        verifyNoInteractions(waService); // already LINK_SENT -> never resend WhatsApp
        assertTrue(proceso.getContractLink().contains("code=NEW"));
    }
}

