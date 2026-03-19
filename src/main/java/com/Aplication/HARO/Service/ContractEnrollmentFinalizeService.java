package com.Aplication.HARO.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractEnrollmentFinalizeService {

    private final ChatbotProcesoService chatbotProcesoService;

    public ContractEnrollmentFinalizeService(ChatbotProcesoService chatbotProcesoService) {
        this.chatbotProcesoService = chatbotProcesoService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long finalizeEnrollment(String documento) {
        return chatbotProcesoService
                .tryFinalizeEnrollmentIfReadyByDocumento(documento)
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long forceFinalizeEnrollment(String documento) {
        return chatbotProcesoService.forceFinalizeEnrollmentByDocumento(documento);
    }
}
