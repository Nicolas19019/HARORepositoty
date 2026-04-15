package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso al progreso academico por modulo.
 *
 * Permite listar avances por estudiante, curso, modulo, favoritos y ultimo
 * registro actualizado para construir reportes del modulo de aprendizaje.
 */
@Repository
public interface ModuloAprendizajeRepository extends JpaRepository<ModuloAprendizaje, Long> {
    List<ModuloAprendizaje> findByIdEstudianteOrderByFechaEvaluacionDesc(Long idEstudiante);
    List<ModuloAprendizaje> findByIdEstudianteAndCursoIgnoreCaseOrderByFechaEvaluacionDesc(Long idEstudiante, String curso);
    Optional<ModuloAprendizaje> findTopByIdEstudianteAndCursoIgnoreCaseAndModuloIgnoreCaseOrderByIdDesc(
            Long idEstudiante,
            String curso,
            String modulo
    );
    List<ModuloAprendizaje> findByIdEstudianteAndCursoIgnoreCaseAndFavoritoTrueOrderByFechaEvaluacionDesc(
            Long idEstudiante,
            String curso
    );

    /**
     * Lista estudiantes que tienen registros de progreso en aprendizaje.
     */
    @Query("""
            SELECT DISTINCT m.idEstudiante
            FROM ModuloAprendizaje m
            WHERE m.idEstudiante IS NOT NULL
            ORDER BY m.idEstudiante ASC
            """)
    List<Long> findDistinctIdEstudianteOrderByIdEstudianteAsc();

    Optional<ModuloAprendizaje> findTopByIdEstudianteOrderByActualizadoEnDescIdDesc(Long idEstudiante);
}
