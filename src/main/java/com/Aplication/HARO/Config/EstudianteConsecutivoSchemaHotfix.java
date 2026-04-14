package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajuste de arranque para estudiante consecutivo schema.
 */
@Component
public class EstudianteConsecutivoSchemaHotfix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EstudianteConsecutivoSchemaHotfix.class);

    private final JdbcTemplate jdbcTemplate;

    public EstudianteConsecutivoSchemaHotfix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    DECLARE
                      max_consecutivo BIGINT;
                    BEGIN
                      -- Ejecuta solo si la tabla existe.
                      IF EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name = 'estudiante'
                      ) THEN

                        -- Secuencia para asignar consecutivo.
                        IF NOT EXISTS (
                          SELECT 1
                          FROM pg_class c
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                          WHERE c.relkind = 'S'
                            AND c.relname = 'estudiante_consecutivo_seq'
                            AND n.nspname = current_schema()
                        ) THEN
                          CREATE SEQUENCE estudiante_consecutivo_seq;
                        END IF;

                        -- Columna consecutivo.
                        IF NOT EXISTS (
                          SELECT 1
                          FROM information_schema.columns
                          WHERE table_schema = current_schema()
                            AND table_name = 'estudiante'
                            AND column_name = 'consecutivo'
                        ) THEN
                          ALTER TABLE estudiante
                            ADD COLUMN consecutivo BIGINT;
                        END IF;

                        -- Backfill: mantenemos valores estables usando el id.
                        UPDATE estudiante
                        SET consecutivo = id_estudiante
                        WHERE consecutivo IS NULL;

                        -- Alinear la secuencia para que el siguiente consecutivo sea MAX+1.
                        SELECT COALESCE(MAX(consecutivo), 0) INTO max_consecutivo FROM estudiante;
                        PERFORM setval('estudiante_consecutivo_seq', max_consecutivo, true);

                        -- Default para inserts futuros.
                        ALTER TABLE estudiante
                          ALTER COLUMN consecutivo SET DEFAULT nextval('estudiante_consecutivo_seq');

                        -- No nulos (siempre debe existir consecutivo).
                        ALTER TABLE estudiante
                          ALTER COLUMN consecutivo SET NOT NULL;

                        -- Unicidad.
                        IF NOT EXISTS (
                          SELECT 1
                          FROM pg_indexes
                          WHERE schemaname = current_schema()
                            AND tablename = 'estudiante'
                            AND indexname = 'idx_estudiante_consecutivo'
                        ) THEN
                          CREATE UNIQUE INDEX idx_estudiante_consecutivo ON estudiante(consecutivo);
                        END IF;
                      END IF;
                    END $$;
                    """);
            log.info("Schema hotfix aplicado: estudiante.consecutivo + estudiante_consecutivo_seq");
        } catch (Exception ex) {
            log.error("No se pudo aplicar hotfix de esquema para estudiante.consecutivo", ex);
        }
    }
}
