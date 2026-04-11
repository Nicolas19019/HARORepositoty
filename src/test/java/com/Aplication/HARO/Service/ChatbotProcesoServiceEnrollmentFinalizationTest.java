package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.EstadoCuenta;
import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.Pagos;
import com.Aplication.HARO.Repository.ChatbotContractCategoryProgressRepository;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import com.Aplication.HARO.Repository.ClaseRepository;
import com.Aplication.HARO.Repository.EstadoCuentaRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.PagoRepository;
import com.Aplication.HARO.Repository.ProfesorRepository;
import com.Aplication.HARO.Repository.VehiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotProcesoServiceEnrollmentFinalizationTest {

    @Test
    void forceFinalizeEnrollmentByDocumento_debeReutilizarEstudianteExistentePorCorreo() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
        ChatbotContractCategoryProgressRepository contractCategoryProgressRepository = mock(ChatbotContractCategoryProgressRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudianteService estudianteService = mock(EstudianteService.class);
        EstadoCuentaRepository estadoCuentaRepository = mock(EstadoCuentaRepository.class);
        PagoRepository pagoRepository = mock(PagoRepository.class);
        ClaseRepository claseRepository = mock(ClaseRepository.class);
        ProfesorRepository profesorRepository = mock(ProfesorRepository.class);
        VehiculoRepository vehiculoRepository = mock(VehiculoRepository.class);
        GoogleCalendarService googleCalendarService = mock(GoogleCalendarService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        ChatbotProcesoService service = new ChatbotProcesoService(
                procesoRepository,
                contractCategoryProgressRepository,
                estudianteRepository,
                estudianteService,
                estadoCuentaRepository,
                pagoRepository,
                claseRepository,
                profesorRepository,
                vehiculoRepository,
                googleCalendarService,
                passwordEncoder
        );

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setNumeroDocumento("12345");
        proceso.setEmail("existente@correo.com");
        proceso.setNombreCompleto("Ana Perez");
        proceso.setTelefono("3001112233");
        proceso.setCategoria("A2");
        proceso.setDireccion("Calle 1");
        proceso.setSede("Norte");
        proceso.setPaymentStatus("APPROVED");
        proceso.setPaymentAmount(new BigDecimal("850000"));
        proceso.setContractStatus("SIGNED");

        Estudiante existente = new Estudiante();
        existente.setId(77L);
        existente.setNumeroDocumento("99999");
        existente.setEmail("existente@correo.com");
        existente.setTipoEstudiante("prospecto");
        existente.setEstado("Pendiente");
        existente.setVisible(false);

        EstadoCuenta estadoCuenta = new EstadoCuenta();
        estadoCuenta.setId(501L);
        estadoCuenta.setIdEstudiante(77L);
        estadoCuenta.setMontoTotal(BigDecimal.ZERO);
        estadoCuenta.setMontoPagado(BigDecimal.ZERO);
        estadoCuenta.setMultas(BigDecimal.ZERO);

        when(procesoRepository.findByNumeroDocumentoForUpdate("12345")).thenReturn(Optional.of(proceso));
        when(procesoRepository.save(any(ChatbotMatriculaProceso.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(estudianteRepository.findByNumeroDocumento("12345")).thenReturn(Optional.empty());
        when(estudianteRepository.findByEmailNormalizado("existente@correo.com")).thenReturn(Optional.of(existente));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(estadoCuentaRepository.findByIdEstudiante(77L)).thenReturn(Optional.of(estadoCuenta));
        when(estadoCuentaRepository.save(any(EstadoCuenta.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.findTopByEstadoCuentaOrderByIdDesc(501L)).thenReturn(Optional.empty());
        when(pagoRepository.save(any(Pagos.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Long studentId = service.forceFinalizeEnrollmentByDocumento("12345");

        assertEquals(77L, studentId);
        assertEquals(77L, proceso.getStudentId());
        assertEquals("STUDENT_CREATED", proceso.getFlowStatus());
        assertEquals("12345", existente.getNumeroDocumento());
        assertEquals("matriculado", existente.getTipoEstudiante());
        assertEquals("Activo", existente.getEstado());
        assertEquals("CHATBOT", existente.getOrigenMatricula());
        assertEquals("A2", existente.getCategoria());
        assertEquals("moto", existente.getTipoPase());
        assertEquals(LocalDate.now(), existente.getFechaMatricula());
        verify(estudianteService, never()).createEstudiante(any(Estudiante.class));
    }
}
