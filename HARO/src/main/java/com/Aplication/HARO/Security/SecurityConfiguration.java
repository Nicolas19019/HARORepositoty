package com.Aplication.HARO.Security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

	 @Bean
	  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	    http
	      .csrf(csrf -> csrf.disable())
	      .authorizeHttpRequests(auth -> auth
	        // swagger / openapi sin auth
	        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
	        // abre tus endpoints (elige uno de los dos bloques)
	        // a) todo tu API sin auth (solo dev):
	        // b) o solo GET sin auth:
	        //.requestMatchers(HttpMethod.GET, "/clases/**").permitAll()

	        .anyRequest().authenticated()
	      )
	      .httpBasic(Customizer.withDefaults()); // si dejas algo protegido
	    return http.build();
	  }
	
	
}
