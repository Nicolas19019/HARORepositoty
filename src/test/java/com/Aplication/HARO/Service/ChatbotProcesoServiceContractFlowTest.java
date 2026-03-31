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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatbotProcesoServiceContractFlowTest {

    @Test
    void buildContractAccessPayloadByEmail_shouldKeepSingleCategoryFlowStable() {
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
        proceso.setId(10L);
        proceso.setEmail("single@example.com");
        proceso.setNombreCompleto("Estudiante Uno");
        proceso.setNumeroDocumento("123456789");
        proceso.setCategoria("A2");

        List<ChatbotContractCategoryProgress> stored = new ArrayList<>();

        when(procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc("single@example.com"))
                .thenReturn(Optional.of(proceso));
        when(contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(10L))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(contractCategoryProgressRepository.save(any(ChatbotContractCategoryProgress.class)))
                .thenAnswer(invocation -> {
                    ChatbotContractCategoryProgress item = invocation.getArgument(0);
                    if (item.getId() == null) {
                        item.setId((long) (stored.size() + 1));
                    }
                    stored.removeIf(existing -> existing.getCategoryCode().equalsIgnoreCase(item.getCategoryCode()));
                    stored.add(item);
                    return item;
                });
        when(procesoRepository.save(any(ChatbotMatriculaProceso.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = service.buildContractAccessPayloadByEmail("single@example.com");

        @SuppressWarnings("unchecked")
        Map<String, Object> contractFlow = (Map<String, Object>) payload.get("contractFlow");
        assertEquals(1, contractFlow.get("totalCategories"));
        assertEquals(1, contractFlow.get("currentCategoryPosition"));
        assertEquals("A2", contractFlow.get("currentCategoryCode"));
        assertEquals(0, contractFlow.get("currentContractIndex"));
        assertEquals(1, contractFlow.get("currentContractPosition"));
        assertEquals("Contrato1.pdf", contractFlow.get("expectedContractFile"));
        assertEquals(List.of("A2"), contractFlow.get("contractCategoriesRequired"));
        assertEquals(null, contractFlow.get("nextCategoryCode"));
        assertEquals(null, contractFlow.get("nextCategoryLabel"));
        assertEquals(List.of("A2"), payload.get("selectedCategories"));
        assertEquals(List.of("A2"), payload.get("contractCategoriesRequired"));
    }

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
        assertEquals(List.of("A2", "C1"), payload.get("contractCategoriesRequired"));
        assertEquals("A2", payload.get("categoria"));

        Object contractFlowRaw = payload.get("contractFlow");
        assertInstanceOf(Map.class, contractFlowRaw);
        @SuppressWarnings("unchecked")
        Map<String, Object> contractFlow = (Map<String, Object>) contractFlowRaw;

        assertEquals(2, contractFlow.get("totalCategories"));
        assertEquals(0, contractFlow.get("currentCategoryIndex"));
        assertEquals(1, contractFlow.get("currentCategoryPosition"));
        assertEquals("A2", contractFlow.get("currentCategoryCode"));
        assertEquals("A2", contractFlow.get("currentCategoryLabel"));
        assertEquals(0, contractFlow.get("currentContractIndex"));
        assertEquals(1, contractFlow.get("currentContractPosition"));
        assertEquals(1, contractFlow.get("expectedContractNumber"));
        assertEquals("Contrato1.pdf", contractFlow.get("expectedContractFile"));
        assertEquals(List.of("A2", "C1"), contractFlow.get("contractCategoriesRequired"));
        assertEquals("C1", contractFlow.get("nextCategoryCode"));
        assertEquals("C1", contractFlow.get("nextCategoryLabel"));

        Object categoriesRaw = contractFlow.get("categories");
        assertInstanceOf(List.class, categoriesRaw);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) categoriesRaw;
        assertEquals(3, categories.size());
        assertEquals("A2", categories.get(0).get("categoryCode"));
        assertEquals(true, categories.get(0).get("requiresContract"));
        assertEquals("PENDING", categories.get(0).get("status"));
        assertEquals("B1", categories.get(1).get("categoryCode"));
        assertEquals(false, categories.get(1).get("requiresContract"));
        assertEquals("EXEMPT", categories.get(1).get("status"));
        assertEquals(0, categories.get(1).get("totalContracts"));
        assertEquals("C1", categories.get(2).get("categoryCode"));
        assertEquals(true, categories.get(2).get("requiresContract"));
        assertTrue(stored.size() == 2);
    }

    @Test
    void getContractCategoryFlowByEmail_shouldKeepNextCategoryPendingAfterCompletingFirstOne() {
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
        proceso.setId(77L);
        proceso.setEmail("combo@example.com");
        proceso.setNumeroDocumento("123456789");
        proceso.setCategoria("A2 y B1");

        ChatbotContractCategoryProgress a2 = new ChatbotContractCategoryProgress();
        a2.setId(1L);
        a2.setProceso(proceso);
        a2.setCategoryCode("A2");
        a2.setCategoryLabel("A2");
        a2.setOrderIndex(0);
        a2.setSignedContractFiles("""
                [{"pdfFile":"Contrato1.pdf"},{"pdfFile":"Contrato2.pdf"},{"pdfFile":"Contrato3.pdf"},{"pdfFile":"Contrato4.pdf"}]
                """);

        ChatbotContractCategoryProgress b1 = new ChatbotContractCategoryProgress();
        b1.setId(2L);
        b1.setProceso(proceso);
        b1.setCategoryCode("B1");
        b1.setCategoryLabel("B1");
        b1.setOrderIndex(1);
        b1.setSignedContractFiles("[]");

        List<ChatbotContractCategoryProgress> stored = new ArrayList<>(List.of(a2, b1));

        when(procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc("combo@example.com"))
                .thenReturn(Optional.of(proceso));
        when(contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(77L))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(contractCategoryProgressRepository.save(any(ChatbotContractCategoryProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatbotProcesoService.ContractCategoryFlowSnapshot flow = service.getContractCategoryFlowByEmail("combo@example.com");

        assertFalse(flow.allCompleted());
        assertEquals(2, flow.totalCategories());
        assertEquals(1, flow.completedCategories());
        assertEquals("B1", flow.currentCategoryCode());
        assertEquals(1, flow.currentCategoryIndex());
        assertEquals(2, flow.currentCategoryPosition());
        assertEquals("Contrato1.pdf", flow.expectedContractFile());
        assertTrue(flow.nextCategoryCode().isBlank());
        assertTrue(flow.nextCategoryLabel().isBlank());
    }

    @Test
    void getContractCategoryFlowByEmail_shouldSkipExemptB1InTripleCombo() {
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
        proceso.setId(88L);
        proceso.setEmail("triple@example.com");
        proceso.setNumeroDocumento("123456789");
        proceso.setCategoria("A2, B1 y C1");

        ChatbotContractCategoryProgress a2 = new ChatbotContractCategoryProgress();
        a2.setId(1L);
        a2.setProceso(proceso);
        a2.setCategoryCode("A2");
        a2.setCategoryLabel("A2");
        a2.setOrderIndex(0);
        a2.setSignedContractFiles("""
                [{"pdfFile":"Contrato1.pdf"},{"pdfFile":"Contrato2.pdf"},{"pdfFile":"Contrato3.pdf"},{"pdfFile":"Contrato4.pdf"}]
                """);

        ChatbotContractCategoryProgress c1 = new ChatbotContractCategoryProgress();
        c1.setId(2L);
        c1.setProceso(proceso);
        c1.setCategoryCode("C1");
        c1.setCategoryLabel("C1");
        c1.setOrderIndex(1);
        c1.setSignedContractFiles("[]");

        List<ChatbotContractCategoryProgress> stored = new ArrayList<>(List.of(a2, c1));

        when(procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc("triple@example.com"))
                .thenReturn(Optional.of(proceso));
        when(contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(88L))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(contractCategoryProgressRepository.save(any(ChatbotContractCategoryProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatbotProcesoService.ContractCategoryFlowSnapshot flow = service.getContractCategoryFlowByEmail("triple@example.com");

        assertFalse(flow.allCompleted());
        assertEquals(2, flow.totalCategories());
        assertEquals(1, flow.completedCategories());
        assertEquals(List.of("A2", "C1"), flow.contractCategoriesRequired());
        assertEquals("C1", flow.currentCategoryCode());
        assertEquals(1, flow.currentCategoryIndex());
        assertEquals(2, flow.currentCategoryPosition());
        assertTrue(flow.nextCategoryCode().isBlank());
        assertEquals(3, flow.categories().size());
        assertEquals("B1", flow.categories().get(1).get("categoryCode"));
        assertEquals(false, flow.categories().get(1).get("requiresContract"));
        assertEquals("EXEMPT", flow.categories().get(1).get("status"));
        assertEquals(0, flow.categories().get(1).get("totalContracts"));
    }
}
