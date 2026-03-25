package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.PaymentSyncContextService;
import com.Aplication.HARO.Service.ProspectoService;
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
        ProspectoService prospectoService = mock(ProspectoService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                prospectoService,
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
                        99L,
                        "",
                        "",
                        ""
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
        ProspectoService prospectoService = mock(ProspectoService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                prospectoService,
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
                        100L,
                        "",
                        "",
                        ""
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

    @Test
    void studentFlow_shouldAllowReturningToStudentMenuWithoutRevalidatingOtp_untilLogout() {
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentSyncContextService paymentSyncContextService = mock(PaymentSyncContextService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                prospectoService,
                verificationService,
                waService
        );

        when(procesoService.requireStudentAccessData("12345678"))
                .thenReturn(new ChatbotProcesoService.StudentAccessData(
                        "12345678",
                        "student@example.com",
                        "s***@example.com",
                        "Alumno Prueba",
                        200L,
                        "",
                        "",
                        ""
                ));
        when(verificationService.verifyEmailOtp("student@example.com", "123456"))
                .thenReturn(true);

        // Start student flow
        controller.inbound(new ChatbotInboundController.InboundMessage("573000000001", "4", null, null, null, false));
        controller.inbound(new ChatbotInboundController.InboundMessage("573000000001", "12345678", null, null, null, false));
        ChatbotInboundController.BotResponse afterOtp = controller.inbound(
                new ChatbotInboundController.InboundMessage("573000000001", "123456", null, null, null, false)
        ).getBody();

        String joinedOtp = afterOtp.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joinedOtp.contains("Soy estudiante CEA HARO"), "Should show student menu after OTP verification");

        // Go back to main menu (should NOT clear verified student session)
        ChatbotInboundController.BotResponse mainMenu = controller.inbound(
                new ChatbotInboundController.InboundMessage("573000000001", "menu", null, null, null, false)
        ).getBody();
        String joinedMain = mainMenu.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joinedMain.contains("Hola, soy Ha-Rot"), "Should return to main menu");

        // Re-enter student menu without OTP
        ChatbotInboundController.BotResponse backToStudent = controller.inbound(
                new ChatbotInboundController.InboundMessage("573000000001", "soy estudiante", null, null, null, false)
        ).getBody();
        String joinedBack = backToStudent.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joinedBack.contains("sesion de estudiante activa") || joinedBack.contains("sesion de estudiante"),
                "Should detect active student session");
        assertTrue(joinedBack.contains("Soy estudiante CEA HARO"), "Should show student menu again without OTP");

        // Logout via option 6 -> confirm -> yes
        ChatbotInboundController.BotResponse askLogout = controller.inbound(
                new ChatbotInboundController.InboundMessage("573000000001", "6", null, null, null, false)
        ).getBody();
        String joinedLogoutAsk = askLogout.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joinedLogoutAsk.toLowerCase().contains("cerrar sesion"), "Should ask logout confirmation");

        controller.inbound(new ChatbotInboundController.InboundMessage("573000000001", "si", null, null, null, false));

        // Now student should be forced to provide document again
        ChatbotInboundController.BotResponse afterLogout = controller.inbound(
                new ChatbotInboundController.InboundMessage("573000000001", "soy estudiante", null, null, null, false)
        ).getBody();
        String joinedAfterLogout = afterLogout.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joinedAfterLogout.toLowerCase().contains("documento"), "Should ask for document again after logout");
        assertTrue(joinedAfterLogout.toLowerCase().contains("otp"), "Should mention OTP again after logout");
    }

    @Test
    void coursesMenu_shouldRedirectRefuerzoAndRecategorizacionToAdvisor() {
        ChatbotProcesoService procesoService = mock(ChatbotProcesoService.class);
        PaymentSyncContextService paymentSyncContextService = mock(PaymentSyncContextService.class);
        ProspectoService prospectoService = mock(ProspectoService.class);
        VerificationService verificationService = mock(VerificationService.class);
        WhatsAppTemplateService waService = mock(WhatsAppTemplateService.class);

        ChatbotInboundController controller = new ChatbotInboundController(
                procesoService,
                paymentSyncContextService,
                prospectoService,
                verificationService,
                waService
        );

        controller.inbound(new ChatbotInboundController.InboundMessage(
                "573001110000",
                "1",
                null,
                null,
                null,
                false
        ));

        ChatbotInboundController.BotResponse refuerzoResp = controller.inbound(new ChatbotInboundController.InboundMessage(
                "573001110000",
                "6",
                null,
                null,
                null,
                false
        )).getBody();

        String refuerzo = refuerzoResp.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(refuerzo.contains("Clase de refuerzo - Carro"));
        assertTrue(refuerzo.contains("https://wa.me/573202114876?text="));
        assertTrue(refuerzo.contains("quiero mas informacion de las clases de refuerzo de carro"));
        assertTrue(!refuerzo.contains("MATRICULA"), "Refuerzo should not send the user to enrollment");

        ChatbotInboundController.BotResponse recatResp = controller.inbound(new ChatbotInboundController.InboundMessage(
                "573001110000",
                "8",
                null,
                null,
                null,
                false
        )).getBody();

        String recat = recatResp.actions().stream()
                .map(ChatbotInboundController.BotAction::body)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(recat.contains("Recategorización B1 a C1"));
        assertTrue(recat.contains("quiero mas informacion de la recategorizacion B1 a C1"));
        assertTrue(recat.contains("📌 Valor curso: $950.000"));
        assertTrue(!recat.contains("MATRICULA"), "Recategorization should not send the user to enrollment");
    }
}
