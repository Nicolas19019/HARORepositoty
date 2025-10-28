// src/main/java/com/Aplication/HARO/Repository/AdministradorRepository.java
package com.Aplication.HARO.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Aplication.HARO.Model.Administrador;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
  Optional<Administrador> findByUsuarioIgnoreCase(String usuario);
  Optional<Administrador> findByCorreoIgnoreCase(String correo);
  boolean existsByUsuarioIgnoreCase(String usuario);
  boolean existsByCorreoIgnoreCase(String correo);
}
