package com.Aplication.HARO.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContractLinkUrlHotfix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ContractLinkUrlHotfix.class);

    private final JdbcTemplate jdbcTemplate;

    public ContractLinkUrlHotfix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE chatbot_matricula_proceso
                    SET contract_link = regexp_replace(
                        contract_link,
                        '^(https?://[^/]+)/contrato\\.html',
                        '\\1/Contratos/contrato.html',
                        'i'
                    )
                    WHERE contract_link ~* '^(https?://[^/]+)/contrato\\.html([?#]|$)'
                    """);

            if (updated > 0) {
                log.info("Hotfix contract_link aplicado. Registros normalizados={}", updated);
            } else {
                log.info("Hotfix contract_link aplicado. No habia registros por normalizar.");
            }
        } catch (Exception ex) {
            log.error("No se pudo aplicar hotfix de contract_link", ex);
        }
    }
}
