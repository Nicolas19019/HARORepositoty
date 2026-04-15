package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializador de arranque para QR scan schema.
 */
@Component
public class QrScanSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QrScanSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public QrScanSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS qr_scan (
                      id_qr_scan BIGSERIAL PRIMARY KEY,
                      source VARCHAR(60),
                      channel VARCHAR(60),
                      target VARCHAR(120),
                      page VARCHAR(120),
                      qr_name VARCHAR(120),
                      campaign VARCHAR(120),
                      sede VARCHAR(120),
                      location VARCHAR(120),
                      ip_address VARCHAR(120),
                      user_agent TEXT,
                      referer TEXT,
                      query_params TEXT,
                      created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_qr_scan_created_at ON qr_scan(created_at)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_qr_scan_source ON qr_scan(source)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_qr_scan_channel ON qr_scan(channel)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_qr_scan_target ON qr_scan(target)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_qr_scan_page ON qr_scan(page)");

            log.info("Schema initializer aplicado: qr_scan");
        } catch (Exception ex) {
            log.error("No se pudo aplicar schema initializer de qr_scan", ex);
        }
    }
}
