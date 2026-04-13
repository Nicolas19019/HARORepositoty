package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Security.ServicioJwt;
import com.Aplication.HARO.Security.ServicioUsuariosCombinado;
import com.Aplication.HARO.Service.AdminPasswordRecoveryService;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutenticacionControllerPasswordRecoveryTest {

    @Test
    void forgotPasswordDebeResponderOkGenerico() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.forgotPassword(new PeticionForgotPassword("admin@correo.com"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Se envio un codigo de recuperacion al correo"), response.getBody());
        verify(recoveryService).requestRecovery("admin@correo.com");
    }

    @Test
    void forgotPasswordHaroGestionDebeResponderOkGenerico() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.forgotPasswordHaroGestion(new PeticionForgotPassword("admin@correo.com"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Se envio un codigo de recuperacion al correo"), response.getBody());
        verify(recoveryService).requestRecovery("admin@correo.com");
    }

    @Test
    void verifyForgotPasswordDebeResponderBadRequestSiCodigoNoEsValido() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        when(recoveryService.verifyCode("admin@correo.com", "123456"))
                .thenReturn(new AdminPasswordRecoveryService.VerificationResult(false, "Codigo invalido o expirado"));

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.verifyForgotPassword(new PeticionForgotPasswordVerify("admin@correo.com", "123456"));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void verifyForgotPasswordHaroGestionDebeResponderOkSiCodigoEsValido() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        when(recoveryService.verifyCode("admin@correo.com", "123456"))
                .thenReturn(new AdminPasswordRecoveryService.VerificationResult(true, "Codigo valido"));

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.verifyForgotPasswordHaroGestion(new PeticionForgotPasswordVerify("admin@correo.com", "123456"));

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void resetPasswordDebeResponderOkSiActualizaContrasena() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        when(recoveryService.resetPassword("admin@correo.com", "123456", "NuevaClave123"))
                .thenReturn(new AdminPasswordRecoveryService.VerificationResult(true, "Contrasena actualizada correctamente"));

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.resetPassword(
                new PeticionResetPassword("admin@correo.com", "123456", "NuevaClave123")
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void resetPasswordHaroGestionDebeResponderOkSiActualizaContrasena() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);

        when(recoveryService.resetPassword("admin@correo.com", "123456", "NuevaClave123"))
                .thenReturn(new AdminPasswordRecoveryService.VerificationResult(true, "Contrasena actualizada correctamente"));

        AutenticacionController controller = new AutenticacionController(authManager, usuarios, jwt, accesoService, recoveryService);

        ResponseEntity<?> response = controller.resetPasswordHaroGestion(
                new PeticionResetPassword("admin@correo.com", "123456", "NuevaClave123")
        );

        assertEquals(200, response.getStatusCode().value());
    }
}
