package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.AdministradorRepository;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPasswordRecoveryServiceTest {

    @Test
    void requestRecoveryDebeEnviarCodigoSiAdminActivoExiste() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );
        ReflectionTestUtils.setField(service, "otpLength", 6);
        ReflectionTestUtils.setField(service, "ttlSeconds", 900L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 30L);
        ReflectionTestUtils.setField(service, "subject", "Recuperacion de contrasena - HaroGestion");

        Administrador admin = new Administrador();
        admin.setId(10L);
        admin.setCorreo("admin@correo.com");
        admin.setNombre("Admin Uno");
        admin.setActivo(true);

        when(adminRepository.findByCorreoNormalizado("admin@correo.com")).thenReturn(Optional.of(admin));
        when(otpTokenRepository.save(any(OtpToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestRecovery("admin@correo.com");

        verify(otpTokenRepository).save(any(OtpToken.class));
        verify(mailService).sendHtml(any(String.class), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void requestRecoveryDebeUsarAsuntoYContenidoDeHaroGestion() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );
        ReflectionTestUtils.setField(service, "subject", "Recuperacion de contrasena - HaroGestion");

        Administrador admin = new Administrador();
        admin.setCorreo("admin@correo.com");
        admin.setNombre("Admin Uno");
        admin.setActivo(true);

        when(adminRepository.findByCorreoNormalizado("admin@correo.com")).thenReturn(Optional.of(admin));
        when(otpTokenRepository.save(any(OtpToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestRecovery("admin@correo.com");

        org.mockito.ArgumentCaptor<String> subjectCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> htmlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> plainCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(org.mockito.ArgumentMatchers.eq("admin@correo.com"), subjectCaptor.capture(), htmlCaptor.capture(), plainCaptor.capture());
        assertEquals("Recuperacion de contrasena - HaroGestion", subjectCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(htmlCaptor.getValue().contains("HaroGestion"));
        org.junit.jupiter.api.Assertions.assertTrue(plainCaptor.getValue().contains("HaroGestion"));
        org.junit.jupiter.api.Assertions.assertFalse(plainCaptor.getValue().contains("modulo de gestion de aprendizaje"));
    }

    @Test
    void requestRecoveryNoDebeEnviarNadaSiAdminNoExisteOEstaInactivo() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );

        Administrador admin = new Administrador();
        admin.setCorreo("admin@correo.com");
        admin.setActivo(false);

        when(adminRepository.findByCorreoNormalizado("admin@correo.com")).thenReturn(Optional.of(admin));

        service.requestRecovery("admin@correo.com");

        verify(mailService, never()).sendHtml(any(String.class), any(String.class), any(String.class), any(String.class));
        verify(otpTokenRepository, never()).save(any(OtpToken.class));
    }

    @Test
    void verifyCodeDebeAceptarCodigoVigente() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );

        OtpToken token = new OtpToken();
        token.setEmail("admin@correo.com");
        token.setPurpose("ADMIN_PASSWORD_RESET");
        token.setOtpHash(com.Aplication.HARO.Security.OtpHasher.sha256("123456"));
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setAttempts(0);

        when(otpTokenRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc("admin@correo.com", "ADMIN_PASSWORD_RESET"))
                .thenReturn(Optional.of(token));

        AdminPasswordRecoveryService.VerificationResult out = service.verifyCode("admin@correo.com", "123456");

        assertTrue(out.ok());
        assertEquals("Codigo valido", out.message());
    }

    @Test
    void resetPasswordDebeActualizarHashYConsumirToken() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );

        Administrador admin = new Administrador();
        admin.setId(10L);
        admin.setCorreo("admin@correo.com");
        admin.setActivo(true);

        OtpToken token = new OtpToken();
        token.setEmail("admin@correo.com");
        token.setPurpose("ADMIN_PASSWORD_RESET");
        token.setOtpHash(com.Aplication.HARO.Security.OtpHasher.sha256("123456"));
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setAttempts(0);

        when(adminRepository.findByCorreoNormalizado("admin@correo.com")).thenReturn(Optional.of(admin));
        when(otpTokenRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc("admin@correo.com", "ADMIN_PASSWORD_RESET"))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NuevaClave123")).thenReturn("HASH");

        AdminPasswordRecoveryService.VerificationResult out = service.resetPassword("admin@correo.com", "123456", "NuevaClave123");

        assertTrue(out.ok());
        assertEquals("Contrasena actualizada correctamente", out.message());
        assertEquals("HASH", admin.getContrasenaHash());
        assertTrue(token.getConsumedAt() != null);
        verify(adminRepository).save(admin);
    }

    @Test
    void resetPasswordDebeFallarSiCodigoEsInvalido() {
        AdministradorRepository adminRepository = mock(AdministradorRepository.class);
        OtpTokenRepository otpTokenRepository = mock(OtpTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        MailService mailService = mock(MailService.class);

        AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
                adminRepository,
                otpTokenRepository,
                passwordEncoder,
                mailService
        );

        Administrador admin = new Administrador();
        admin.setCorreo("admin@correo.com");
        admin.setActivo(true);

        OtpToken token = new OtpToken();
        token.setEmail("admin@correo.com");
        token.setPurpose("ADMIN_PASSWORD_RESET");
        token.setOtpHash(com.Aplication.HARO.Security.OtpHasher.sha256("123456"));
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setAttempts(0);

        when(adminRepository.findByCorreoNormalizado("admin@correo.com")).thenReturn(Optional.of(admin));
        when(otpTokenRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc("admin@correo.com", "ADMIN_PASSWORD_RESET"))
                .thenReturn(Optional.of(token));
        when(otpTokenRepository.save(any(OtpToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPasswordRecoveryService.VerificationResult out = service.resetPassword("admin@correo.com", "999999", "NuevaClave123");

        assertFalse(out.ok());
        assertEquals("Codigo invalido o vencido", out.message());
        verify(adminRepository, never()).save(any(Administrador.class));
    }
}
