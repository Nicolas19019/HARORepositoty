package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatbotMatriculaProcesoRepository extends JpaRepository<ChatbotMatriculaProceso, Long> {
    Optional<ChatbotMatriculaProceso> findByNumeroDocumento(String numeroDocumento);
    Optional<ChatbotMatriculaProceso> findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(String email);
    Optional<ChatbotMatriculaProceso> findTopByPhoneOrderByUpdatedAtDesc(String phone);
}
