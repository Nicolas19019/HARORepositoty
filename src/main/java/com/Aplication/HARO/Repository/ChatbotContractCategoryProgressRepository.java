package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ChatbotContractCategoryProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso al progreso de contratos por categoria del chatbot.
 *
 * Permite que un mismo proceso firme contratos por paquetes o categorias
 * diferentes, guardando el contrato actual, el estado y los archivos firmados.
 */
public interface ChatbotContractCategoryProgressRepository extends JpaRepository<ChatbotContractCategoryProgress, Long> {

    /**
     * Lista las categorias de un proceso en el orden definido para la firma.
     */
    List<ChatbotContractCategoryProgress> findByProcesoIdOrderByOrderIndexAscIdAsc(Long procesoId);

    Optional<ChatbotContractCategoryProgress> findByProcesoIdAndCategoryCodeIgnoreCase(Long procesoId, String categoryCode);

    long deleteByProcesoId(Long procesoId);

    long deleteByProcesoIdIn(List<Long> procesoIds);
}
