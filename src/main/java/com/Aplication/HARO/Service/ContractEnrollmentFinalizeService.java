package com.Aplication.HARO.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio fachada para finalizar matriculas desde contratos.
 *
 * Delega en el flujo de chatbot la creacion o actualizacion del estudiante una
 * vez se completa la firma contractual.
 */
@Service
public class ContractEnrollmentFinalizeService {

    private final ChatbotProcesoService chatbotProcesoService;

    public ContractEnrollmentFinalizeService(ChatbotProcesoService chatbotProcesoService) {
        this.chatbotProcesoService = chatbotProcesoService;
    }

    /**
     * Finaliza el flujo principal y devuelve la referencia generada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long finalizeEnrollment(String documento) {
        return chatbotProcesoService
                .tryFinalizeEnrollmentIfReadyByDocumento(documento)
                .orElse(null);
    }

    /**
     * Fuerza la operacion indicada aun cuando el flujo no cumpla todas las precondiciones.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long forceFinalizeEnrollment(String documento) {
        return chatbotProcesoService.forceFinalizeEnrollmentByDocumento(documento);
    }
}
