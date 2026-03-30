package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Profesor;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatbotProcesoServiceSedeTest {

    @Test
    void pickAvailableProfesor_shouldPreferInstructorFromSameSede() {
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

        Profesor otro = new Profesor();
        otro.setId(10L);
        otro.setVisible(true);
        otro.setCategoria("carro");
        otro.setSede("CC El Eden");

        Profesor mismo = new Profesor();
        mismo.setId(20L);
        mismo.setVisible(true);
        mismo.setCategoria("carro");
        mismo.setSede("Kennedy");

        when(profesorRepository.findByVisibleTrueOrderByIdAsc()).thenReturn(List.of(otro, mismo));
        when(claseRepository.findBusyProfesorIds(LocalDate.of(2026, 3, 25), LocalTime.of(8, 0), LocalTime.of(10, 0)))
                .thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Optional<Profesor> picked = (Optional<Profesor>) ReflectionTestUtils.invokeMethod(
                service,
                "pickAvailableProfesor",
                LocalDate.of(2026, 3, 25),
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                "carro",
                "Kennedy"
        );

        assertTrue(picked.isPresent());
        assertEquals(20L, picked.get().getId());
    }
}
