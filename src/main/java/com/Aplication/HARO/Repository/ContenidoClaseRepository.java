package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ContenidoClase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a contenidos de clases de aprendizaje.
 *
 * Mantiene consultas ordenadas por clase y operaciones de limpieza cuando se
 * elimina o reemplaza una clase.
 */
public interface ContenidoClaseRepository extends JpaRepository<ContenidoClase, Long> {
    List<ContenidoClase> findByClase_IdOrderByOrdenAscIdAsc(Long claseId);
    List<ContenidoClase> findByClase_IdAndVisibleTrueOrderByOrdenAscIdAsc(Long claseId);
    void deleteByClase_Id(Long claseId);
}
