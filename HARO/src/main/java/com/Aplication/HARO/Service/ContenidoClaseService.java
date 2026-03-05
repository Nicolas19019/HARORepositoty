package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Repository.ContenidoClaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ContenidoClaseService {

    public record ContentInput(
            String titulo,
            String tipo,
            String descripcion,
            String url,
            Integer orden,
            Boolean visible
    ) {}

    private static final Set<String> TIPOS_VALIDOS = Set.of("documento", "video", "imagen", "enlace");
    private static final Set<String> EXT_PERMITIDAS = Set.of("pdf", "mp4", "jpg", "jpeg", "png", "docx");
    private static final long MAX_BYTES = 50L * 1024L * 1024L;

    private final ContenidoClaseRepository repository;
    private final ClaseAprendizajeService claseService;
    private final Path uploadDir;

    public ContenidoClaseService(ContenidoClaseRepository repository,
                                 ClaseAprendizajeService claseService,
                                 @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.repository = repository;
        this.claseService = claseService;
        this.uploadDir = Path.of(uploadDir, "clases");
    }

    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClaseForAdmin(Long claseId) {
        return repository.findByClaseIdOrderByOrdenAscIdAsc(claseId);
    }

    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClasePublicada(Long claseId) {
        ClaseAprendizaje clase = claseService.requireById(claseId);
        if (!Boolean.TRUE.equals(clase.getVisible()) || !Boolean.TRUE.equals(clase.getPublicada())) {
            return List.of();
        }
        return repository.findByClaseIdAndVisibleTrueOrderByOrdenAscIdAsc(claseId);
    }

    public ContenidoClase create(Long claseId, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ClaseAprendizaje clase = claseService.requireById(claseId);

        ContenidoClase row = new ContenidoClase();
        row.setClase(clase);
        merge(row, in, archivo, true);
        return repository.save(row);
    }

    public ContenidoClase update(Long id, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        merge(row, in, archivo, false);
        return repository.save(row);
    }

    public void deleteLogico(Long id) {
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        row.setVisible(false);
        repository.save(row);
    }

    private void merge(ContenidoClase row, ContentInput in, MultipartFile archivo, boolean creating) {
        if (in.titulo() != null) row.setTitulo(trim(in.titulo()));
        if (in.tipo() != null) row.setTipo(trim(in.tipo()).toLowerCase());
        if (in.descripcion() != null) row.setDescripcion(trimToNull(in.descripcion()));
        if (in.orden() != null) row.setOrden(in.orden());
        if (in.visible() != null) row.setVisible(in.visible());

        boolean hasFile = archivo != null && !archivo.isEmpty();
        if (hasFile) {
            row.setUrl(storeFile(archivo));
        } else if (in.url() != null) {
            row.setUrl(trim(in.url()));
        }

        if (creating && row.getVisible() == null) row.setVisible(true);
        if (creating && row.getOrden() == null) row.setOrden(1);

        validate(row);
    }

    private void validate(ContenidoClase row) {
        String titulo = trim(row.getTitulo());
        String tipo = trim(row.getTipo()).toLowerCase();
        String url = trim(row.getUrl());

        if (titulo.isBlank()) throw new IllegalArgumentException("titulo es obligatorio");
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new IllegalArgumentException("tipo invalido. Usa: documento, video, imagen, enlace");
        }
        if (url.isBlank()) throw new IllegalArgumentException("url es obligatoria");
        if (row.getOrden() == null || row.getOrden() <= 0) {
            throw new IllegalArgumentException("orden debe ser mayor a 0");
        }

        row.setTitulo(titulo);
        row.setTipo(tipo);
        row.setUrl(url);
        if (row.getVisible() == null) row.setVisible(true);
    }

    private String storeFile(MultipartFile file) {
        try {
            if (file.getSize() <= 0) {
                throw new IllegalArgumentException("archivo vacio");
            }
            if (file.getSize() > MAX_BYTES) {
                throw new IllegalArgumentException("archivo excede 50MB");
            }

            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
            String ext = extension(original);
            if (!EXT_PERMITIDAS.contains(ext)) {
                throw new IllegalArgumentException("extension no permitida. Usa: pdf, mp4, jpg, jpeg, png, docx");
            }

            Files.createDirectories(uploadDir);
            String safeName = UUID.randomUUID() + "." + ext;
            Path target = uploadDir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/clases/" + safeName;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo", e);
        }
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String trimToNull(String s) {
        String out = trim(s);
        return out.isBlank() ? null : out;
    }
}

