package com.Aplication.HARO.Config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClaseAprendizajeSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public ClaseAprendizajeSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS clase_aprendizaje (
                  id_clase_aprendizaje BIGSERIAL PRIMARY KEY,
                  curso VARCHAR(120) NOT NULL,
                  area VARCHAR(60) NOT NULL,
                  titulo VARCHAR(180) NOT NULL,
                  descripcion VARCHAR(2000),
                  publicada BOOLEAN NOT NULL DEFAULT FALSE,
                  visible BOOLEAN NOT NULL DEFAULT TRUE,
                  fecha_publicacion DATE,
                  creado_en TIMESTAMP NOT NULL DEFAULT NOW(),
                  actualizado_en TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contenido_clase (
                  id_contenido_clase BIGSERIAL PRIMARY KEY,
                  id_clase_aprendizaje BIGINT NOT NULL,
                  titulo VARCHAR(180) NOT NULL,
                  tipo VARCHAR(20) NOT NULL,
                  descripcion VARCHAR(2000),
                  url VARCHAR(1000) NOT NULL,
                  orden_contenido INTEGER NOT NULL DEFAULT 1,
                  visible BOOLEAN NOT NULL DEFAULT TRUE,
                  creado_en TIMESTAMP NOT NULL DEFAULT NOW(),
                  actualizado_en TIMESTAMP NOT NULL DEFAULT NOW(),
                  CONSTRAINT fk_contenido_clase_clase
                    FOREIGN KEY (id_clase_aprendizaje)
                    REFERENCES clase_aprendizaje(id_clase_aprendizaje)
                    ON DELETE CASCADE
                )
                """);
    }
}

