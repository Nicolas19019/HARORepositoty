package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.EpaycoProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Servicio de utilidades ePayco.
 *
 * Calcula y valida firmas de confirmacion, y consulta transacciones por
 * referencia cuando se requiere verificar el pago en la pasarela.
 */
@Service
public class EpaycoService {

    private final EpaycoProperties props;
    private final RestTemplate restTemplate;

    public EpaycoService(EpaycoProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    /**
     * Calcula el hash SHA-256 hexadecimal del valor recibido.
     */
    public String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-256", e);
        }
    }

    /**
     * Indica si se cumple la condicion consultada.
     */
    public boolean isValidSignature(String xRefPayco, String xTransactionId, String xAmount, String xCurrencyCode, String xSignature) {
        String signatureString = String.join("^",
                props.getPCustIdCliente(),
                props.getPKey(),
                nullToEmpty(xRefPayco),
                nullToEmpty(xTransactionId),
                nullToEmpty(xAmount),
                nullToEmpty(xCurrencyCode)
        );

        String generated = sha256Hex(signatureString);
        return constantTimeEquals(generated, nullToEmpty(xSignature));
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Consulta informacion externa asociada a la referencia recibida.
     */
    public String fetchTransactionByRefPayco(String refPayco) {
        return restTemplate.getForObject(props.getValidationUrl() + refPayco, String.class);
    }
}
