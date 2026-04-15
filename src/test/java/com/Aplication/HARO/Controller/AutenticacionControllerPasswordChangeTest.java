package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Security.DetallesUsuarioAplicacion;
import com.Aplication.HARO.Security.ServicioJwt;
import com.Aplication.HARO.Security.ServicioUsuariosCombinado;
import com.Aplication.HARO.Service.AdminPasswordRecoveryService;
import com.Aplication.HARO.Service.AdministradorService;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import com.Aplication.HARO.Service.EstudianteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AutenticacionControllerPasswordChangeTest {

    @Test
    void changePasswordDebeActualizarContrasenaParaAdmin() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new DetallesUsuarioAplicacion(5L, "admin@correo.com", "hash", "ADMIN", true));
        when(administradorService.validarContrasenaActual(5L, "Actual123!")).thenReturn(true);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.changePassword(
                new PeticionCambioPassword("Actual123!", "NuevaClave123!", "NuevaClave123!"),
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("ok", true, "message", "Contrasena actualizada correctamente"), response.getBody());
        verify(administradorService).cambiarContrasenaAutenticado(5L, "NuevaClave123!");
    }

    @Test
    void changePasswordDebeActualizarContrasenaParaEstudiante() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new DetallesUsuarioAplicacion(8L, "student@correo.com", "hash", "ESTUDIANTE", true));
        when(estudianteService.validarContrasenaActual(8L, "Actual123!")).thenReturn(true);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.changePassword(
                new PeticionCambioPassword("Actual123!", "NuevaClave123!", "NuevaClave123!"),
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(estudianteService).cambiarContrasenaAutenticado(8L, "NuevaClave123!");
    }

    @Test
    void changePasswordDebePermitirNuevaContrasenaSinMayuscula() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new DetallesUsuarioAplicacion(8L, "student@correo.com", "hash", "ESTUDIANTE", true));
        when(estudianteService.validarContrasenaActual(8L, "Temporal123!")).thenReturn(true);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.changePassword(
                new PeticionCambioPassword("Temporal123!", "nuevaclave123!", "nuevaclave123!"),
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(estudianteService).cambiarContrasenaAutenticado(8L, "nuevaclave123!");
    }

    @Test
    void changePasswordDebeFallarSiContrasenaActualEsIncorrecta() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new DetallesUsuarioAplicacion(5L, "admin@correo.com", "hash", "ADMIN", true));
        when(administradorService.validarContrasenaActual(5L, "Actual123!")).thenReturn(false);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.changePassword(
                new PeticionCambioPassword("Actual123!", "NuevaClave123!", "NuevaClave123!"),
                authentication
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals(Map.of("ok", false, "message", "La contrasena actual es incorrecta"), response.getBody());
    }
}
