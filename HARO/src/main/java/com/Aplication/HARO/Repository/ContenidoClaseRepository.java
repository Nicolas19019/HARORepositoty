package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ContenidoClase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContenidoClaseRepository extends JpaRepository<ContenidoClase, Long> {
    List<ContenidoClase> findByClase_IdOrderByOrdenAscIdAsc(Long claseId);
    List<ContenidoClase> findByClase_IdAndVisibleTrueOrderByOrdenAscIdAsc(Long claseId);
}
