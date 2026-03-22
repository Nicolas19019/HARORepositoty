package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatbotMatriculaProcesoRepository extends JpaRepository<ChatbotMatriculaProceso, Long> {
    Optional<ChatbotMatriculaProceso> findByNumeroDocumento(String numeroDocumento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChatbotMatriculaProceso p where p.id = :id")
    Optional<ChatbotMatriculaProceso> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChatbotMatriculaProceso p where p.numeroDocumento = :numeroDocumento")
    Optional<ChatbotMatriculaProceso> findByNumeroDocumentoForUpdate(@Param("numeroDocumento") String numeroDocumento);

    Optional<ChatbotMatriculaProceso> findTopByEmailIgnoreCaseOrderByUpdatedAtDesc(String email);
    Optional<ChatbotMatriculaProceso> findTopByPhoneOrderByUpdatedAtDesc(String phone);
    Optional<ChatbotMatriculaProceso> findTopByPaymentLinkContainingOrderByUpdatedAtDesc(String invoiceToken);

    @Query("""
            select p
            from ChatbotMatriculaProceso p
            where (:docPrefix = '' or p.numeroDocumento like concat(:docPrefix, '%'))
              and (:docSuffix = '' or p.numeroDocumento like concat('%', :docSuffix))
              and (:emailPrefix = '' or lower(p.email) like concat(lower(:emailPrefix), '%'))
              and (:emailSuffix = '' or lower(p.email) like concat('%', lower(:emailSuffix)))
            order by p.updatedAt desc
            """)
    List<ChatbotMatriculaProceso> findCandidatesForMaskedLookup(@Param("docPrefix") String docPrefix,
                                                                @Param("docSuffix") String docSuffix,
                                                                @Param("emailPrefix") String emailPrefix,
                                                                @Param("emailSuffix") String emailSuffix,
                                                                Pageable pageable);
}
