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
                    END $$;
                    """);
            log.info("Schema hotfix aplicado: chatbot_matricula_proceso payment_link/contract_link/direccion/sede/student_password_hash/payment_plan/origen_registro/metodo_pago/auditoria_pago/flags_envio_contrato");
        } catch (Exception ex) {
            log.error("No se pudo aplicar hotfix de esquema para chatbot_matricula_proceso", ex);
        }
    }
}
