// com/Aplication/HARO/Seguridad/CryptoConfiguracion.java
package com.Aplication.HARO.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CryptoConfiguracion {
  @Bean
  public PasswordEncoder encriptador() {
    return new BCryptPasswordEncoder();
  }
}
