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

        // Un solo registro por teléfono: se actualiza el "servicio" con el último consultado.
        // Si por datos anteriores existen duplicados, tomamos el más reciente y limpiamos el resto.
        java.util.List<Prospecto> existing = repo.findByTelefonoInOrderByActualizadoEnDesc(phoneCandidates(tel));
        Prospecto p;
        int mergedConsultas = 0;
        if (existing != null && !existing.isEmpty()) {
            p = pickMaster(existing, tel);

            for (Prospecto it : existing) {
                if (it == null) continue;
                mergedConsultas += safeInt(it.getConsultas());
            }

            java.util.List<Prospecto> toDelete = new java.util.ArrayList<>();
            for (Prospecto it : existing) {
                if (it == null) continue;
                if (p != null && it.getId() != null && p.getId() != null && it.getId().equals(p.getId())) continue;
                if (it == p) continue;
                toDelete.add(it);
            }

            if (!toDelete.isEmpty()) {
                try {
                    repo.deleteAll(toDelete);
                    repo.flush();
                } catch (Exception e) {
                    log.warn("No se pudo limpiar prospectos duplicados telefono={}", maskPhone(tel), e);
                }
            }

            if (p == null) {
                p = new Prospecto();
            }
            // Canoniza el telefono para evitar nuevos registros con variaciones del mismo numero.
            p.setTelefono(tel);
        } else {
            p = new Prospecto();
            p.setTelefono(tel);
            p.setConsultas(0);
        }

        int count = mergedConsultas > 0 ? mergedConsultas : safeInt(p.getConsultas());
        p.setConsultas(count + 1);
        p.setServicio(srv);
        repo.save(p);
    }

    @Transactional(readOnly = true)
    public boolean existeProspectoActivoPorTelefono(String telefono) {
        String tel = normalizePhone(telefono);
        if (tel.isBlank()) {
            return false;
        }
        return repo.findByTelefonoInOrderByActualizadoEnDesc(phoneCandidates(tel))
                .stream()
                .findFirst()
                .isPresent();
    }

    private String normalizePhone(String raw) {
        String out = raw == null ? "" : raw.trim();
        if (out.isBlank()) return "";

        // Soporta formatos tipo "whatsapp:+573001112233"
        int colon = out.lastIndexOf(':');
        if (colon >= 0 && colon < out.length() - 1) {
            String prefix = out.substring(0, colon).toLowerCase(Locale.ROOT);
            if (prefix.endsWith("whatsapp") || prefix.endsWith("tel")) {
                out = out.substring(colon + 1);
            }
        }

        // Canonico: solo digitos (sin '+', espacios, guiones, etc.).
        out = out.replaceAll("\\D+", "");
        if (out.startsWith("00")) {
            out = out.substring(2);
        }
        return out;
    }

    private String normalizeService(String raw) {
        String out = raw == null ? "" : raw.trim();
        out = out.replaceAll("\\s+", " ");
        if (out.isBlank()) return "";
        // guardamos un valor estable para reportes
        return out.toUpperCase(Locale.ROOT);
    }

    private String maskPhone(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() <= 4) return "****";
        return "*".repeat(Math.max(4, v.length() - 4)) + v.substring(v.length() - 4);
    }

    private int safeInt(Integer v) {
        if (v == null) return 0;
        return Math.max(0, v);
    }

    private java.util.List<String> phoneCandidates(String canonicalDigitsOnly) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String tel = canonicalDigitsOnly == null ? "" : canonicalDigitsOnly.trim();
        if (tel.isBlank()) {
            return java.util.List.of();
        }

        out.add(tel);
        out.add("+" + tel);
        out.add("00" + tel);

        // Colombia: soporta historicos guardados con/sin prefijo 57.
        if (tel.startsWith("57") && tel.length() > 10) {
            out.add(tel.substring(tel.length() - 10));
        } else if (tel.length() == 10) {
            out.add("57" + tel);
            out.add("+57" + tel);
        }

        return new java.util.ArrayList<>(out);
    }

    private Prospecto pickMaster(java.util.List<Prospecto> existing, String canonicalDigitsOnly) {
        if (existing == null || existing.isEmpty()) {
            return null;
        }
        for (Prospecto p : existing) {
            if (p != null && canonicalDigitsOnly.equals(p.getTelefono())) {
                return p;
            }
        }
        for (Prospecto p : existing) {
            if (p != null && ("+" + canonicalDigitsOnly).equals(p.getTelefono())) {
                return p;
            }
        }
        return existing.get(0);
    }
}
