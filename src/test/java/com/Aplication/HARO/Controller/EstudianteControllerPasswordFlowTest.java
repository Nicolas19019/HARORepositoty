package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Service.EstudianteModuloAccesoService;
import com.Aplication.HARO.Service.EstudianteService;
import com.Aplication.HARO.Service.VerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstudianteControllerPasswordFlowTest {

    @Test
    void createDebeEnviarContrasenaAlServicio() {
        EstudianteService service = mock(EstudianteService.class);
        VerificationService verificationService = mock(VerificationService.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        EstudianteController controller = new EstudianteController(service, verificationService, accesoService);

        Estudiante in = new Estudiante();
        in.setEmail("student@correo.com");
        in.setUsuario("student1");
        in.setContrasena("MiClave123*");

        Estudiante created = new Estudiante();
        created.setId(15L);
        when(service.createEstudiante(any(Estudiante.class))).thenReturn(created);

        controller.create(in);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(service).createEstudiante(captor.capture());
        assertEquals("MiClave123*", captor.getValue().getContrasena());
    }

    @Test
    void updateDebePermitirCambioDeContrasenaCuandoLlegaEnPayload() {
        EstudianteService service = mock(EstudianteService.class);
        VerificationService verificationService = mock(VerificationService.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        EstudianteController controller = new EstudianteController(service, verificationService, accesoService);

        Estudiante patch = new Estudiante();
        patch.setContrasena("NuevaClave123*");

        when(service.updateEstudiante(any(Long.class), any(Estudiante.class))).thenReturn(new Estudiante());

        controller.update(9L, patch);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(service).updateEstudiante(org.mockito.ArgumentMatchers.eq(9L), captor.capture());
        assertEquals("NuevaClave123*", captor.getValue().getContrasena());
    }

    @Test
    void registroModuloDebeValidarOtpYActivarCuenta() {
        EstudianteService service = mock(EstudianteService.class);
        VerificationService verificationService = mock(VerificationService.class);
        EstudianteModuloAccesoService accesoService = mock(EstudianteModuloAccesoService.class);
        EstudianteController controller = new EstudianteController(service, verificationService, accesoService);

        when(verificationService.verifyEmailOtp("student@correo.com", "123456")).thenReturn(true);

        Estudiante updated = new Estudiante();
        updated.setId(20L);
        updated.setEmail("student@correo.com");
        updated.setUsuario("student1");
        updated.setNumeroDocumento("12345678");
        when(service.activarCuentaModulo("student@correo.com", "MiClave123*", "student1")).thenReturn(updated);
        when(accesoService.registrarModulo(any(EstudianteModuloAccesoService.RegistroRequest.class)))
                .thenReturn(new EstudianteModuloAccesoService.RegistroResponse(true, 20L, true, null, null, null, 0, "FRONT_REGISTRO"));

        controller.registroModulo(new EstudianteController.RegistroModuloRequest(
                "student@correo.com",
                "123456",
                "MiClave123*",
                "student1",
                "FRONT_REGISTRO"
        ));

        verify(verificationService).verifyEmailOtp("student@correo.com", "123456");
        verify(service).activarCuentaModulo("student@correo.com", "MiClave123*", "student1");
        verify(accesoService).registrarModulo(any(EstudianteModuloAccesoService.RegistroRequest.class));
    }
}
