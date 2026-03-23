package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentSyncContextService;
import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ChatbotInboundControllerStudentOtpTest {

    @Test
    void studentFlow_shouldSendOtpSynchronously_andPromptForOtpCode() {
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentSyncContextService paymentSyncContextService = mock(PaymentSyncContextService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                verificationService,
                waService
        );

        // Step 1: user selects option 4 ("Soy estudiante")
        ChatbotInboundController.InboundMessage start = new ChatbotInboundController.InboundMessage(
                "573001112233",
                "4",
                null,
                null,
                null,
                false
        );
        controller.inbound(start);

        // Step 2: user provides document
        when(procesoService.requireStudentAccessData("12345678"))
                .thenReturn(new ChatbotProcesoService.StudentAccessData(
                        "12345678",
                        "test@example.com",
                        "t***@example.com",
                        "Test User",
                        99L
                ));

        ChatbotInboundController.InboundMessage docMsg = new ChatbotInboundController.InboundMessage(
                "573001112233",
                "12345678",
                null,
                null,
                null,
                false
        );

        ChatbotInboundController.BotResponse resp = controller.inbound(docMsg).getBody();
        verify(verificationService, times(1)).sendEmailVerification("test@example.com");

        List<ChatbotInboundController.BotAction> actions = resp.actions();
        String joined = actions.stream().map(ChatbotInboundController.BotAction::body).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(joined.contains("OTP"), "Response should mention OTP");
        assertTrue(joined.toLowerCase().contains("correo"), "Response should mention email");
        assertTrue(joined.contains("t***@example.com"), "Response should show masked email");
    }

    @Test
    void studentFlow_shouldHandleOtpCooldown_andStillPromptForOtpCode() {
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentSyncContextService paymentSyncContextService = mock(PaymentSyncContextService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                verificationService,
                waService
        );

        controller.inbound(new ChatbotInboundController.InboundMessage(
                "573009998877",
                "soy estudiante",
                null,
                null,
                null,
                false
        ));

        when(procesoService.requireStudentAccessData("12345678"))
                .thenReturn(new ChatbotProcesoService.StudentAccessData(
                        "12345678",
                        "cooldown@example.com",
                        "c***@example.com",
                        "Cooldown User",
                        100L
                ));

        doThrow(new IllegalStateException("Espera 15s para reenviar el código."))
                .when(verificationService).sendEmailVerification("cooldown@example.com");

        ChatbotInboundController.BotResponse resp = controller.inbound(new ChatbotInboundController.InboundMessage(
                "573009998877",
                "12345678",
                null,
                null,
                null,
                false
        )).getBody();

        String joined = resp.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(joined.contains("OTP"), "Response should still mention OTP");
        assertTrue(joined.contains("15s"), "Response should include cooldown seconds hint when present");
    }
}

