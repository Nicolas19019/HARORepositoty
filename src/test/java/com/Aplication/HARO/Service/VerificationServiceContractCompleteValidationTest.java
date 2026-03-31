package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationServiceContractCompleteValidationTest {

    @Test
    void completeContractSigning_shouldFailWhenAnyCategoryIsStillPending() {
        OtpTokenRepository repo = mock(OtpTokenRepository.class);
        MailService mail = mock(MailService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        ChatbotProcesoService chatbotProcesoService = mock(ChatbotProcesoService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);
        ContractDocumentStorageService contractDocumentStorageService = mock(ContractDocumentStorageService.class);
        ContractEnrollmentFinalizeService contractEnrollmentFinalizeService = mock(ContractEnrollmentFinalizeService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VerificationService> selfProvider = mock(ObjectProvider.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        VerificationService service = new VerificationService(
                repo,
                mail,
                estudianteService,
                chatbotProcesoService,
                waService,
                contractDocumentStorageService,
                contractEnrollmentFinalizeService,
                selfProvider,
                txManager,
                restTemplate
        );

        when(selfProvider.getObject()).thenReturn(service);

        OtpToken token = new OtpToken();
        token.setEmail("test@example.com");
        token.setPurpose("CONTRACT_SIGN");
        token.setOtpHash(OtpHasher.sha256("CODE123"));
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setAttempts(0);

        when(repo.findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(
                "test@example.com",
                "CONTRACT_SIGN",
                OtpHasher.sha256("CODE123")
        )).thenReturn(Optional.of(token));
        when(repo.save(any(OtpToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setEmail("test@example.com");
        proceso.setNumeroDocumento("123456789");
        proceso.setFlowStatus("CONTRACT_IN_PROGRESS");
        proceso.setPaymentStatus("APPROVED");

        when(chatbotProcesoService.findLatestProcesoByEmail("test@example.com"))
                .thenReturn(Optional.of(proceso));
        when(chatbotProcesoService.getContractCategoryFlowByEmail("test@example.com"))
                .thenReturn(new ChatbotProcesoService.ContractCategoryFlowSnapshot(
                        false,
                        "B1",
                        "B1",
                        1,
                        2,
                        2,
                        1,
                        1,
                        0,
                        1,
                        1,
                        "Contrato1.pdf",
                        "",
                        "",
                        List.of(
                                Map.of("categoryCode", "A2", "completed", true),
                                Map.of("categoryCode", "B1", "completed", false)
                        )
                ));

        VerificationService.ContractCompletionResult out = service.completeContractSigning("test@example.com", "CODE123");

        assertFalse(out.ok());
        assertEquals("Aun faltan contratos por firmar antes de finalizar el proceso.", out.message());
        verify(chatbotProcesoService, never()).markContractSignedByEmail("test@example.com");
        verify(contractEnrollmentFinalizeService, never()).forceFinalizeEnrollment("123456789");
    }
}
