package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotContractCategoryProgress;
import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatbotProcesoServiceContractFlowTest {

    @Test
    void buildContractAccessPayloadByEmail_shouldExposeDynamicCategoryFlowForCombo() {
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
        proceso.setId(50L);
        proceso.setEmail("combo@example.com");
        proceso.setNombreCompleto("Estudiante Combo");
        proceso.setNumeroDocumento("123456789");
        proceso.setCategoria("A2, B1 y C1");
        proceso.setTelefono("573001112233");

        List<ChatbotContractCategoryProgress> stored = new ArrayList<>();

        when(procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc("combo@example.com"))
                .thenReturn(Optional.of(proceso));
        when(contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(50L))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(contractCategoryProgressRepository.save(any(ChatbotContractCategoryProgress.class)))
                .thenAnswer(invocation -> {
                    ChatbotContractCategoryProgress item = invocation.getArgument(0);
                    if (item.getId() == null) {
                        item.setId((long) (stored.size() + 1));
                    }
                    stored.removeIf(existing -> existing.getCategoryCode().equalsIgnoreCase(item.getCategoryCode()));
                    stored.add(item);
                    stored.sort((left, right) -> Integer.compare(left.getOrderIndex(), right.getOrderIndex()));
                    return item;
                });
        when(procesoRepository.save(any(ChatbotMatriculaProceso.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = service.buildContractAccessPayloadByEmail("combo@example.com");

        assertEquals(List.of("A2", "B1", "C1"), payload.get("selectedCategories"));
        assertEquals("A2", payload.get("categoria"));

        Object contractFlowRaw = payload.get("contractFlow");
        assertInstanceOf(Map.class, contractFlowRaw);
        @SuppressWarnings("unchecked")
        Map<String, Object> contractFlow = (Map<String, Object>) contractFlowRaw;

        assertEquals(3, contractFlow.get("totalCategories"));
        assertEquals(0, contractFlow.get("currentCategoryIndex"));
        assertEquals("A2", contractFlow.get("currentCategoryCode"));
        assertEquals("A2", contractFlow.get("currentCategoryLabel"));
        assertEquals(0, contractFlow.get("currentContractIndex"));

        Object categoriesRaw = contractFlow.get("categories");
        assertInstanceOf(List.class, categoriesRaw);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) categoriesRaw;
        assertEquals(3, categories.size());
        assertEquals("A2", categories.get(0).get("categoryCode"));
        assertEquals("PENDING", categories.get(0).get("status"));
        assertEquals("B1", categories.get(1).get("categoryCode"));
        assertEquals("C1", categories.get(2).get("categoryCode"));
        assertTrue(stored.size() == 3);
    }
}
