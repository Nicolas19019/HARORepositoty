// com/Aplication/HARO/Repository/EstudianteRepository.java
package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Estudiante;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

  @Query("""
    SELECT e FROM Estudiante e
    WHERE (e.email IS NOT NULL AND lower(e.email) = lower(:login))
       OR (e.usuario IS NOT NULL AND lower(e.usuario) = lower(:login))
       OR (e.numeroDocumento IS NOT NULL AND e.numeroDocumento = :login)
  """)
  Optional<Estudiante> findByLogin(@Param("login") String login);

  @Query("""
    SELECT e FROM Estudiante e
    WHERE e.email IS NOT NULL
      AND lower(trim(e.email)) = lower(trim(:email))
  """)
  Optional<Estudiante> findByEmailNormalizado(@Param("email") String email);

  @Query("""
    SELECT e FROM Estudiante e
    WHERE e.visible = true
      AND e.email IS NOT NULL
      AND lower(trim(e.email)) = lower(trim(:email))
  """)
  Optional<Estudiante> findByEmailNormalizadoVisible(@Param("email") String email);

  boolean existsByNumeroDocumento(String numeroDocumento);

  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByEmailIgnoreCase(String email);
  List<Estudiante> findByVisibleTrueOrderByIdAsc();
  Optional<Estudiante> findByIdAndVisibleTrue(Long id);
  Optional<Estudiante> findByNumeroDocumentoAndVisibleTrue(String d);
  Optional<Estudiante> findByUsuarioIgnoreCase(String u);
  Optional<Estudiante> findByEmailIgnoreCase(String e);
  Optional<Estudiante> findByNumeroDocumento(String d);
}
