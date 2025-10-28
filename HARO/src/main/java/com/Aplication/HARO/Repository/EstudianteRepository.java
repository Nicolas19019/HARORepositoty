// com/Aplication/HARO/Repository/EstudianteRepository.java
package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Estudiante;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

  @Query("""
    SELECT e FROM Estudiante e
    WHERE (e.email IS NOT NULL AND lower(e.email) = lower(:login))
       OR (e.usuario IS NOT NULL AND lower(e.usuario) = lower(:login))
       OR (e.numeroDocumento IS NOT NULL AND e.numeroDocumento = :login)
  """)
  Optional<Estudiante> findByLogin(@Param("login") String login);

  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByEmailIgnoreCase(String email);
  Optional<Estudiante> findByUsuarioIgnoreCase(String u);
  Optional<Estudiante> findByEmailIgnoreCase(String e);
  Optional<Estudiante> findByNumeroDocumento(String d);
}
