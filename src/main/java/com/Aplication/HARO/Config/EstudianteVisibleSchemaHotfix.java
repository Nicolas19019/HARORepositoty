package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajuste de arranque para estudiante visible schema.
 */
@Component
public class EstudianteVisibleSchemaHotfix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EstudianteVisibleSchemaHotfix.class);

    private final JdbcTemplate jdbcTemplate;

    public EstudianteVisibleSchemaHotfix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                      IF EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name = 'estudiante'
                      ) THEN

                        IF EXISTS (
                          SELECT 1
                          FROM information_schema.columns
                          WHERE table_schema = current_schema()
                            AND table_name = 'estudiante'
                            AND column_name = 'visible'
                        ) THEN

                          -- Backfill historico: antes la columna existia pero quedo NULL.
                          UPDATE estudiante
                          SET visible = true
                          WHERE visible IS NULL;

                          -- Defaults estables
                          ALTER TABLE estudiante
                            ALTER COLUMN visible SET DEFAULT true;

                          -- Evita que futuros inserts/updates dejen NULL.
                          ALTER TABLE estudiante
                            ALTER COLUMN visible SET NOT NULL;
                        END IF;
                      END IF;
                    END $$;
                    """);
            log.info("Schema hotfix aplicado: estudiante.visible (default + backfill + not null)");
        } catch (Exception ex) {
            log.error("No se pudo aplicar hotfix de esquema para estudiante.visible", ex);
        }
    }
}

