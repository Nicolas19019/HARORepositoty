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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutenticacionControllerModuloAccesoTest {

    @Test
    void loginDeEstudianteMarcaIngresoAlModulo() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("estudiante@correo.com", "secret"));
        when(usuarios.loadUserByUsername("estudiante@correo.com"))
                .thenReturn(new DetallesUsuarioAplicacion(15L, "estudiante@correo.com", "hash", "ESTUDIANTE", true));
        when(jwt.emitirTokenAcceso("estudiante@correo.com", "ESTUDIANTE", 15L, null)).thenReturn("access-token");
        when(jwt.emitirTokenRefresco("estudiante@correo.com")).thenReturn("refresh-token");
        when(jwt.getAccessTtlSeconds()).thenReturn(900L);
        when(jwt.getRefreshTtlSeconds()).thenReturn(604800L);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.login(new PeticionInicioSesion("estudiante@correo.com", "secret"));

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(RespuestaTokens.class, response.getBody());
        verify(accesoService).registrarIngresoPorEstudiante(15L);
    }

    @Test
    void loginDeAdminNoMarcaIngresoAlModulo() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        ServicioUsuariosCombinado usuarios = mock(ServicioUsuariosCombinado.class);
        ServicioJwt jwt = mock(ServicioJwt.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        AdminPasswordRecoveryService recoveryService = mock(AdminPasswordRecoveryService.class);
        AdministradorService administradorService = mock(AdministradorService.class);
        EstudianteService estudianteService = mock(EstudianteService.class);

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("admin@correo.com", "secret"));
        when(usuarios.loadUserByUsername("admin@correo.com"))
                .thenReturn(new DetallesUsuarioAplicacion(3L, "admin@correo.com", "hash", "ADMIN", true));
        when(jwt.emitirTokenAcceso("admin@correo.com", "ADMIN", 3L, null)).thenReturn("access-token");
        when(jwt.emitirTokenRefresco("admin@correo.com")).thenReturn("refresh-token");
        when(jwt.getAccessTtlSeconds()).thenReturn(900L);
        when(jwt.getRefreshTtlSeconds()).thenReturn(604800L);

        AutenticacionController controller = new AutenticacionController(
                authManager, usuarios, jwt, accesoService, recoveryService, administradorService, estudianteService);

        ResponseEntity<?> response = controller.login(new PeticionInicioSesion("admin@correo.com", "secret"));

        assertEquals(200, response.getStatusCode().value());
        verify(accesoService, never()).registrarIngresoPorEstudiante(anyLong());
    }
}
