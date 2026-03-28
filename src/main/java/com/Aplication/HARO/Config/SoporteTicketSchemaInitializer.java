package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SoporteTicketSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SoporteTicketSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SoporteTicketSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureSchema();
    }

    public void ensureSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS soporte_ticket (
                      id_soporte_ticket BIGSERIAL PRIMARY KEY,
                      tipo VARCHAR(60) NOT NULL,
                      mensaje TEXT NOT NULL,
                      estado VARCHAR(30) NOT NULL,
                      nota_interna TEXT,
                      email_estudiante VARCHAR(180) NOT NULL,
                      nombre_estudiante VARCHAR(180) NOT NULL,
                      adjunto_nombre VARCHAR(255),
                      adjunto_url TEXT,
                      responsable VARCHAR(180),
                      created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                      updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_soporte_ticket_created_at ON soporte_ticket(created_at)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_soporte_ticket_estado ON soporte_ticket(estado)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_soporte_ticket_tipo ON soporte_ticket(tipo)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_soporte_ticket_email ON soporte_ticket(email_estudiante)");

            log.info("Schema initializer aplicado: soporte_ticket");
        } catch (Exception ex) {
            log.error("No se pudo aplicar schema initializer de soporte_ticket", ex);
            throw ex;
        }
    }
}
