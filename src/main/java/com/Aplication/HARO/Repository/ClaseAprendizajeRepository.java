package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a clases publicables del modulo de aprendizaje.
 *
 * Ofrece listados administrativos y filtros visibles para estudiantes por
 * curso, area y fecha de publicacion.
 */
public interface ClaseAprendizajeRepository extends JpaRepository<ClaseAprendizaje, Long> {
    List<ClaseAprendizaje> findAllByOrderByIdDesc();
    List<ClaseAprendizaje> findByPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc();
    List<ClaseAprendizaje> findByCursoIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(String curso);
    List<ClaseAprendizaje> findByAreaIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(String area);
    List<ClaseAprendizaje> findByCursoIgnoreCaseAndAreaIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(
            String curso, String area
    );
}

