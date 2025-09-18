package com.carritos.academiacarros.repository;

import com.carritos.academiacarros.model.Profesor;
import com.carritos.academiacarros.model.EspecialidadProfesor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfesorRepository extends JpaRepository<Profesor, Long> {
    List<Profesor> findByEspecialidad(EspecialidadProfesor especialidad);
}
