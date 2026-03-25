package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Prospecto;
import com.Aplication.HARO.Repository.ProspectoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class ProspectoService {

    private static final Logger log = LoggerFactory.getLogger(ProspectoService.class);

    private final ProspectoRepository repo;

    public ProspectoService(ProspectoRepository repo) {
        this.repo = repo;
    }

    public void registrarConsulta(String telefono, String servicio) {
        String tel = normalizePhone(telefono);
        String srv = normalizeService(servicio);
        if (tel.isBlank() || srv.isBlank()) {
            return;
        }

        Prospecto p = repo.findByTelefonoAndServicio(tel, srv)
                .orElseGet(() -> {
                    Prospecto n = new Prospecto();
                    n.setTelefono(tel);
                    n.setServicio(srv);
                    n.setConsultas(0);
                    return n;
                });

        Integer count = p.getConsultas();
        if (count == null) count = 0;
        p.setConsultas(count + 1);
        repo.save(p);
    }

    private String normalizePhone(String raw) {
        String out = raw == null ? "" : raw.trim();
        out = out.replaceAll("[^0-9+]", "");
        return out;
    }

    private String normalizeService(String raw) {
        String out = raw == null ? "" : raw.trim();
        out = out.replaceAll("\\s+", " ");
        if (out.isBlank()) return "";
        // guardamos un valor estable para reportes
        return out.toUpperCase(Locale.ROOT);
    }
}

