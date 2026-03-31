package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ChatbotContractCategoryProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatbotContractCategoryProgressRepository extends JpaRepository<ChatbotContractCategoryProgress, Long> {

    List<ChatbotContractCategoryProgress> findByProcesoIdOrderByOrderIndexAscIdAsc(Long procesoId);

    Optional<ChatbotContractCategoryProgress> findByProcesoIdAndCategoryCodeIgnoreCase(Long procesoId, String categoryCode);

    long deleteByProcesoIdIn(List<Long> procesoIds);
}
