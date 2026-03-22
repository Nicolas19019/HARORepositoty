package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatbotProcesoServicePaymentLinkTest {

    @Test
    void getPaymentLink_shouldEmbedUnmaskedIdentityInCallbacks() {
        ChatbotMatriculaProcesoRepository procesoRepository = mock(ChatbotMatriculaProcesoRepository.class);
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

        ReflectionTestUtils.setField(service, "defaultPaymentLink", "https://payco.link/f536c9aa-1456-4ce4-b142-6d7417f71c9f");
        ReflectionTestUtils.setField(service, "paymentLinkA2", "https://payco.link/f536c9aa-1456-4ce4-b142-6d7417f71c9f");
        ReflectionTestUtils.setField(service, "paymentConfirmationUrl", "https://harorepositoty2-590358146556.europe-west1.run.app/confirmation");
        ReflectionTestUtils.setField(service, "paymentReturnUrl", "https://ceaharo.com/respuesta.html");
        ReflectionTestUtils.setField(service, "paymentDocumentParam", "x_extra1");
        ReflectionTestUtils.setField(service, "paymentEmailParam", "customer_email");
        ReflectionTestUtils.setField(service, "paymentFlowIdParam", "x_extra2");
        ReflectionTestUtils.setField(service, "paymentInvoiceParam", "x_id_invoice");
        ReflectionTestUtils.setField(service, "paymentConfirmationParam", "confirmation");
        ReflectionTestUtils.setField(service, "paymentReturnParam", "response");

        ChatbotMatriculaProceso proceso = new ChatbotMatriculaProceso();
        proceso.setId(2L);
        proceso.setNombreCompleto("Nicolas Machado");
        proceso.setNumeroDocumento("12345678");
        proceso.setCategoria("A2");
        proceso.setEmail("nicolasmachado422@gmail.com");
        proceso.setTelefono("573144899708");

        when(procesoRepository.findByNumeroDocumento("12345678")).thenReturn(Optional.of(proceso));

        String paymentLink = service.getPaymentLink("12345678");
        assertNotNull(paymentLink);
        assertFalse(paymentLink.isBlank());

        Map<String, String> rootParams = parseFirstQueryParams(paymentLink);
        String confirmation = rootParams.get("confirmation");
        String response = rootParams.get("response");

        assertEquals("2", rootParams.get("flow_id"));
        assertEquals("2", rootParams.get("x_extra2"));
        assertEquals("2", rootParams.get("extra2"));
        assertEquals("12345678", rootParams.get("x_extra1"));
        assertEquals("12345678", rootParams.get("document"));
        assertEquals("nicolasmachado422@gmail.com", rootParams.get("customer_email"));
        assertEquals("nicolasmachado422@gmail.com", rootParams.get("email"));
        assertEquals("573144899708", rootParams.get("x_customer_phone"));
        assertEquals("573144899708", rootParams.get("x_customer_mobile"));
        assertEquals("573144899708", rootParams.get("phone"));

        assertNotNull(confirmation);
        assertNotNull(response);

        Map<String, String> confirmationParams = parseFirstQueryParams(confirmation);
        Map<String, String> responseParams = parseFirstQueryParams(response);

        assertEquals("2", confirmationParams.get("flow_id"));
        assertEquals("2", confirmationParams.get("x_extra2"));
        assertEquals("2", confirmationParams.get("extra2"));
        assertEquals("12345678", confirmationParams.get("x_extra1"));
        assertEquals("nicolasmachado422@gmail.com", confirmationParams.get("customer_email"));
        assertEquals("573144899708", confirmationParams.get("x_customer_phone"));
        assertEquals("573144899708", confirmationParams.get("x_customer_mobile"));

        assertEquals("2", responseParams.get("flow_id"));
        assertEquals("2", responseParams.get("x_extra2"));
        assertEquals("2", responseParams.get("extra2"));
        assertEquals("12345678", responseParams.get("x_extra1"));
        assertEquals("nicolasmachado422@gmail.com", responseParams.get("customer_email"));
        assertEquals("573144899708", responseParams.get("x_customer_phone"));
        assertEquals("573144899708", responseParams.get("x_customer_mobile"));
    }

    private Map<String, String> parseFirstQueryParams(String rawUrl) {
        Map<String, String> out = new LinkedHashMap<>();
        URI uri = URI.create(rawUrl);
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return out;
        }
        for (String pair : query.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            if (out.containsKey(key)) {
                continue;
            }
            String value = parts.length > 1 ? decode(parts[1]) : "";
            out.put(key, value);
        }
        return out;
    }

    private String decode(String raw) {
        return URLDecoder.decode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }
}
