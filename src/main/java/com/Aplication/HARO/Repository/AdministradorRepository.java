// src/main/java/com/Aplication/HARO/Repository/AdministradorRepository.java
package com.Aplication.HARO.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.Aplication.HARO.Model.Administrador;

/**
 * Acceso a datos de administradores internos.
 *
 * Centraliza busquedas por usuario, correo y validaciones de unicidad usadas en
 * autenticacion y administracion de cuentas.
 */
public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
  Optional<Administrador> findByUsuarioIgnoreCase(String usuario);
  Optional<Administrador> findByCorreoIgnoreCase(String correo);

  /**
   * Busca un administrador ignorando mayusculas y espacios externos del correo.
   */
  @Query("""
    SELECT a FROM Administrador a
    WHERE a.correo IS NOT NULL
      AND lower(trim(a.correo)) = lower(trim(:correo))
  """)
  Optional<Administrador> findByCorreoNormalizado(@Param("correo") String correo);

  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByCorreoIgnoreCase(String correo);
}
