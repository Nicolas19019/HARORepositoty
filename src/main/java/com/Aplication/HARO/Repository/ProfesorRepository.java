// com/Aplication/HARO/Repository/ProfesorRepository.java
package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.Profesor;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de profesores.
 *
 * Agrupa busquedas para login, validacion de duplicados y seleccion de
 * profesores visibles para agenda y administracion.
 */
public interface ProfesorRepository extends JpaRepository<Profesor, Long> {

  /**
   * Busca por correo, email, usuario o cedula usando un unico valor de login.
   */
  @Query("""
    SELECT p FROM Profesor p
    WHERE (p.correo  IS NOT NULL AND lower(p.correo)  = lower(:login))
       OR (p.email   IS NOT NULL AND lower(p.email)   = lower(:login))
       OR (p.usuario IS NOT NULL AND lower(p.usuario) = lower(:login))
       OR (p.cedula  IS NOT NULL AND p.cedula = :login)
  """)
  Optional<Profesor> findByLogin(@Param("login") String login);
  boolean existsByCedula(String cedula);
  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByCorreoIgnoreCase(String correo);
  boolean existsByEmailIgnoreCase(String email);
  Optional<Profesor> findByUsuarioIgnoreCase(String u);
  Optional<Profesor> findByEmailIgnoreCase(String e);
  Optional<Profesor> findByCorreoIgnoreCase(String c);
  Optional<Profesor> findByCedula(String d);
  Optional<Profesor> findByIdAndVisibleTrue(Long id);
  Optional<Profesor> findFirstByVisibleTrueOrderByIdAsc();
  List<Profesor> findByVisibleTrueOrderByIdAsc();
}
