package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatbotMatriculaSchemaHotfix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatbotMatriculaSchemaHotfix.class);

    private final JdbcTemplate jdbcTemplate;

    public ChatbotMatriculaSchemaHotfix(JdbcTemplate jdbcTemplate) {
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
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'payment_link'
                          AND data_type = 'character varying'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ALTER COLUMN payment_link TYPE TEXT;
                      END IF;

                      IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'contract_link'
                          AND data_type = 'character varying'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ALTER COLUMN contract_link TYPE TEXT;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'direccion'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN direccion VARCHAR(220);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'edad'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN edad INTEGER;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'sede'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN sede VARCHAR(120);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'student_password_hash'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN student_password_hash VARCHAR(100);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'payment_plan'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN payment_plan VARCHAR(20);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'origen_registro'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN origen_registro VARCHAR(20);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'metodo_pago'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN metodo_pago VARCHAR(20);
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'payment_confirmed_at'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN payment_confirmed_at TIMESTAMPTZ;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'payment_validated_by_admin_id'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN payment_validated_by_admin_id BIGINT;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'payment_observation'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN payment_observation TEXT;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'contract_email_sent'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN contract_email_sent BOOLEAN DEFAULT FALSE;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'contract_email_sent_at'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN contract_email_sent_at TIMESTAMPTZ;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'contract_chatbot_sent'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN contract_chatbot_sent BOOLEAN DEFAULT FALSE;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'contract_chatbot_sent_at'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN contract_chatbot_sent_at TIMESTAMPTZ;
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_matricula_proceso'
                          AND column_name = 'visible'
                      ) THEN
                        ALTER TABLE chatbot_matricula_proceso
                          ADD COLUMN visible BOOLEAN DEFAULT TRUE;
                      END IF;

                      UPDATE chatbot_matricula_proceso
                      SET visible = TRUE
                      WHERE visible IS NULL;

                      ALTER TABLE chatbot_matricula_proceso
                        ALTER COLUMN visible SET DEFAULT TRUE;

                      ALTER TABLE chatbot_matricula_proceso
                        ALTER COLUMN visible SET NOT NULL;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name = 'chatbot_contract_category_progress'
                      ) THEN
                        CREATE TABLE chatbot_contract_category_progress (
                          id BIGSERIAL PRIMARY KEY,
                          proceso_id BIGINT NOT NULL,
                          category_code VARCHAR(20) NOT NULL,
                          category_label VARCHAR(40) NOT NULL,
                          order_index INTEGER NOT NULL DEFAULT 0,
                          status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                          current_contract_index INTEGER NOT NULL DEFAULT 0,
                          contract_form_data TEXT,
                          signed_contract_files TEXT,
                          completed_at TIMESTAMPTZ,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          CONSTRAINT fk_chatbot_contract_category_progress_proceso
                            FOREIGN KEY (proceso_id) REFERENCES chatbot_matricula_proceso(id) ON DELETE CASCADE,
                          CONSTRAINT uk_chatbot_contract_category_progress_process_category
                            UNIQUE (proceso_id, category_code)
                        );
                      END IF;

                      IF NOT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE schemaname = current_schema()
                          AND indexname = 'idx_chatbot_contract_category_progress_proceso'
                      ) THEN
                        CREATE INDEX idx_chatbot_contract_category_progress_proceso
                          ON chatbot_contract_category_progress (proceso_id, order_index);
                      END IF;
                    END $$;
                    """);
            log.info("Schema hotfix aplicado: chatbot_matricula_proceso + chatbot_contract_category_progress");
        } catch (Exception ex) {
            log.error("No se pudo aplicar hotfix de esquema para chatbot_matricula_proceso", ex);
        }

        // Vistas de conveniencia: separan ePayco vs efectivo sin cambiar la entidad principal.
        // (PostgreSQL no tiene CREATE VIEW IF NOT EXISTS, por eso usamos OR REPLACE)
        try {
            jdbcTemplate.execute("""
                    CREATE OR REPLACE VIEW chatbot_matricula_proceso_epayco AS
                    SELECT p.*
                    FROM chatbot_matricula_proceso p
                    WHERE COALESCE(NULLIF(btrim(p.metodo_pago), ''), 'EPAYCO') ILIKE 'EPAYCO';
                    """);
            jdbcTemplate.execute("""
                    CREATE OR REPLACE VIEW chatbot_matricula_proceso_efectivo AS
                    SELECT p.*
                    FROM chatbot_matricula_proceso p
                    WHERE COALESCE(NULLIF(btrim(p.metodo_pago), ''), '') ILIKE 'EFECTIVO';
                    """);
            log.info("Vistas creadas/actualizadas: chatbot_matricula_proceso_epayco, chatbot_matricula_proceso_efectivo");
        } catch (Exception ex) {
            log.warn("No se pudieron crear vistas de matricula por metodo de pago: {}", ex.getMessage());
        }
    }
}
