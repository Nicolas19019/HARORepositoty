// com/Aplication/HARO/Seguridad/CryptoConfiguracion.java
package com.Aplication.HARO.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuracion base de utilidades criptograficas.
 *
 * Expone un encoder BCrypt simple para componentes que requieren el bean
 * historico {@code encriptador}.
 */
@Configuration
public class CryptoConfiguracion {
  /**
   * Crea un codificador BCrypt para hashes de contrasenas.
   */
  @Bean
  public PasswordEncoder encriptador() {
    return new BCryptPasswordEncoder();
  }
}
