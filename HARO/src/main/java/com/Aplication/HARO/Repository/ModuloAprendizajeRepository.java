package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuloAprendizajeRepository extends JpaRepository<ModuloAprendizaje, Long> {
    List<ModuloAprendizaje> findByIdEstudianteOrderByFechaEvaluacionDesc(Long idEstudiante);
    List<ModuloAprendizaje> findByIdEstudianteAndCursoIgnoreCaseOrderByFechaEvaluacionDesc(Long idEstudiante, String curso);
}
