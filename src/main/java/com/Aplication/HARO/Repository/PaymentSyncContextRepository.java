package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.PaymentSyncContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Acceso a contextos temporales de sincronizacion de pagos.
 *
 * Permite reconciliar callbacks de pasarela con procesos de matricula usando
 * cualquiera de las referencias disponibles y limpiar registros vencidos.
 */
public interface PaymentSyncContextRepository extends JpaRepository<PaymentSyncContext, Long> {

    /**
     * Busca contextos activos que coincidan con al menos una referencia no vacia.
     */
    @Query("""
            select c
            from PaymentSyncContext c
            where c.expiresAt > :now
              and (
                    (:lookupReference <> '' and c.lookupReference = :lookupReference)
                 or (:gatewayReference <> '' and c.gatewayReference = :gatewayReference)
                 or (:invoice <> '' and c.invoice = :invoice)
                 or (:transactionId <> '' and c.transactionId = :transactionId)
              )
            order by c.updatedAt desc
            """)
    List<PaymentSyncContext> findActiveCandidates(@Param("lookupReference") String lookupReference,
                                                  @Param("gatewayReference") String gatewayReference,
                                                  @Param("invoice") String invoice,
                                                  @Param("transactionId") String transactionId,
                                                  @Param("now") Instant now,
                                                  Pageable pageable);

    /**
     * Elimina contextos relacionados con cualquiera de las referencias recibidas.
     */
    @Modifying
    @Query("""
            delete from PaymentSyncContext c
            where
                  (:lookupReference <> '' and c.lookupReference = :lookupReference)
               or (:gatewayReference <> '' and c.gatewayReference = :gatewayReference)
               or (:invoice <> '' and c.invoice = :invoice)
               or (:transactionId <> '' and c.transactionId = :transactionId)
            """)
    int deleteByAnyReference(@Param("lookupReference") String lookupReference,
                             @Param("gatewayReference") String gatewayReference,
                             @Param("invoice") String invoice,
                             @Param("transactionId") String transactionId);

    /**
     * Elimina contextos cuyo vencimiento ya paso.
     */
    @Modifying
    @Query("delete from PaymentSyncContext c where c.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
