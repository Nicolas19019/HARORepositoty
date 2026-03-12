package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatbotMatriculaProcesoRepository extends JpaRepository<ChatbotMatriculaProceso, Long> {
    Optional<ChatbotMatriculaProceso> findByNumeroDocumento(String numeroDocumento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChatbotMatriculaProceso p where p.numeroDocumento = :numeroDocumento")
    Optional<ChatbotMatriculaProceso> findByNumeroDocumentoForUpdate(@Param("numeroDocumento") String numeroDocumento);

    Optional<ChatbotMatriculaProceso> findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(String email);
    Optional<ChatbotMatriculaProceso> findTopByPhoneOrderByUpdatedAtDesc(String phone);
}
