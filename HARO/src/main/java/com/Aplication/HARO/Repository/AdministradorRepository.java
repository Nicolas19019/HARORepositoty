// src/main/java/com/Aplication/HARO/Repository/AdministradorRepository.java
package com.Aplication.HARO.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.Aplication.HARO.Model.Administrador;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
  Optional<Administrador> findByUsuarioIgnoreCase(String usuario);
  Optional<Administrador> findByCorreoIgnoreCase(String correo);
  @Query("""
    SELECT a FROM Administrador a
    WHERE a.correo IS NOT NULL
      AND lower(trim(a.correo)) = lower(trim(:correo))
  """)
  Optional<Administrador> findByCorreoNormalizado(@Param("correo") String correo);
  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByCorreoIgnoreCase(String correo);
}
