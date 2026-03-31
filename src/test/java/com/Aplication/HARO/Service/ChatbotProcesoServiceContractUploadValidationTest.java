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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatbotProcesoServiceContractUploadValidationTest {

    @Test
    void mergeContractSubmissionByEmail_shouldRejectWrongCategoryCode() {
        Fixture fixture = new Fixture("A2 y B1");

        ChatbotProcesoService.ContractUploadValidationException ex = assertThrows(
                ChatbotProcesoService.ContractUploadValidationException.class,
                () -> fixture.service.mergeContractSubmissionByEmail(
                        "combo@example.com",
                        "B1",
                        "Contrato 1",
                        "Contrato1.pdf",
                        "{}",
                        "Contrato1_Firmado.pdf",
                        "/tmp/Contrato1_Firmado.pdf",
                        "contracts/a2/Contrato1_Firmado.pdf",
                        "firmante"
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("La categoria activa es A2. Debe cargar los contratos en ese orden.", ex.getMessage());
    }

    @Test
    void mergeContractSubmissionByEmail_shouldRejectOutOfOrderContract() {
        Fixture fixture = new Fixture("A2");

        ChatbotProcesoService.ContractUploadValidationException ex = assertThrows(
                ChatbotProcesoService.ContractUploadValidationException.class,
                () -> fixture.service.mergeContractSubmissionByEmail(
                        "combo@example.com",
                        "A2",
                        "Contrato 2",
                        "Contrato2.pdf",
                        "{}",
                        "Contrato2_Firmado.pdf",
                        "/tmp/Contrato2_Firmado.pdf",
                        "contracts/a2/Contrato2_Firmado.pdf",
                        "firmante"
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Debe cargar Contrato1.pdf para la categoria A2.", ex.getMessage());
    }

    @Test
    void mergeContractSubmissionByEmail_shouldRejectExemptCategoryInTripleCombo() {
        Fixture fixture = new Fixture("A2, B1 y C1");

        ChatbotProcesoService.ContractUploadValidationException ex = assertThrows(
                ChatbotProcesoService.ContractUploadValidationException.class,
                () -> fixture.service.mergeContractSubmissionByEmail(
                        "combo@example.com",
                        "B1",
                        "Contrato 1",
                        "Contrato1.pdf",
                        "{}",
                        "Contrato1_Firmado_B1.pdf",
                        "/tmp/Contrato1_Firmado_B1.pdf",
                        "contracts/b1/Contrato1_Firmado_B1.pdf",
                        "firmante"
                )
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("La categoria B1 no requiere contrato en este combo.", ex.getMessage());
    }

    private static final class Fixture {
        private final ChatbotProcesoService service;

        private Fixture(String categoria) {
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

            service = new ChatbotProcesoService(
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
            proceso.setId(90L);
            proceso.setEmail("combo@example.com");
            proceso.setNumeroDocumento("123456789");
            proceso.setCategoria(categoria);

            List<ChatbotContractCategoryProgress> stored = new ArrayList<>();

            when(procesoRepository.findTopByEmailIgnoreCaseOrderByUpdatedAtDesc("combo@example.com"))
                    .thenReturn(Optional.of(proceso));
            when(contractCategoryProgressRepository.findByProcesoIdOrderByOrderIndexAscIdAsc(90L))
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
        }
    }
}
