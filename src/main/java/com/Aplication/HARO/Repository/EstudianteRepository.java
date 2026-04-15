// com/Aplication/HARO/Repository/EstudianteRepository.java
package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Estudiante;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de estudiantes.
 *
 * Agrupa busquedas para login, validacion de duplicados, filtrado por
 * visibilidad y generacion de consecutivo de matricula.
 */
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

  /**
   * Busca por correo, usuario o documento usando un unico valor de login.
   */
  @Query("""
    SELECT e FROM Estudiante e
    WHERE (e.email IS NOT NULL AND lower(e.email) = lower(:login))
       OR (e.usuario IS NOT NULL AND lower(e.usuario) = lower(:login))
       OR (e.numeroDocumento IS NOT NULL AND e.numeroDocumento = :login)
  """)
  Optional<Estudiante> findByLogin(@Param("login") String login);

  /**
   * Busca por correo ignorando mayusculas y espacios externos.
   */
  @Query("""
    SELECT e FROM Estudiante e
    WHERE e.email IS NOT NULL
      AND lower(trim(e.email)) = lower(trim(:email))
  """)
  Optional<Estudiante> findByEmailNormalizado(@Param("email") String email);

  /**
   * Busca por correo normalizado solo entre estudiantes visibles.
   */
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
  Optional<Estudiante> findByConsecutivo(Long consecutivo);

  /**
   * Obtiene el siguiente consecutivo desde la secuencia de base de datos.
   */
  @Query(value = "SELECT nextval('estudiante_consecutivo_seq')", nativeQuery = true)
  Long nextConsecutivo();
}
