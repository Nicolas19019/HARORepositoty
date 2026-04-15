package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Repository.ClaseAprendizajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Servicio de clases del modulo de aprendizaje.
 *
 * Administra publicacion, visibilidad, filtros por curso y area, y eliminacion
 * logica o fisica de clases academicas.
 */
@Service
@Transactional
public class ClaseAprendizajeService {

    private static final Set<String> AREAS_VALIDAS = Set.of(
            "etica", "tecnica", "marco_legal", "adaptacion", "mecanica"
    );

    private final ClaseAprendizajeRepository repository;

    public ClaseAprendizajeService(ClaseAprendizajeRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<ClaseAprendizaje> getAllAdmin() {
        return repository.findAllByOrderByIdDesc();
    }

    /**
     * Ejecuta la operacion publica del servicio.
     */
    @Transactional(readOnly = true)
    public List<ClaseAprendizaje> getPublicadas(String curso, String area) {
        String cursoNorm = trim(curso);
        String areaNorm = normalizeAreaOptional(area);

        if (!cursoNorm.isBlank() && !areaNorm.isBlank()) {
            return repository.findByCursoIgnoreCaseAndAreaIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(
                    cursoNorm, areaNorm
            );
        }
        if (!cursoNorm.isBlank()) {
            return repository.findByCursoIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(cursoNorm);
        }
        if (!areaNorm.isBlank()) {
            return repository.findByAreaIgnoreCaseAndPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc(areaNorm);
        }
        return repository.findByPublicadaTrueAndVisibleTrueOrderByFechaPublicacionDescIdDesc();
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public ClaseAprendizaje create(ClaseAprendizaje in) {
        if (in == null) throw new IllegalArgumentException("La clase es requerida");
        in.setId(null);
        validarYNormalizar(in, true);
        return repository.save(in);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public ClaseAprendizaje update(Long id, ClaseAprendizaje in) {
        if (in == null) throw new IllegalArgumentException("La clase es requerida");
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));

        if (in.getCurso() != null) db.setCurso(in.getCurso());
        if (in.getArea() != null) db.setArea(in.getArea());
        if (in.getTitulo() != null) db.setTitulo(in.getTitulo());
        if (in.getDescripcion() != null) db.setDescripcion(in.getDescripcion());
        if (in.getPublicada() != null) db.setPublicada(in.getPublicada());
        if (in.getVisible() != null) db.setVisible(in.getVisible());
        if (in.getFechaPublicacion() != null) db.setFechaPublicacion(in.getFechaPublicacion());

        validarYNormalizar(db, false);
        return repository.save(db);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public ClaseAprendizaje patchEstado(Long id, Boolean publicada, Boolean visible) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));

        if (publicada != null) db.setPublicada(publicada);
        if (visible != null) db.setVisible(visible);
        validarYNormalizar(db, false);
        return repository.save(db);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteLogico(Long id) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
        db.setVisible(false);
        db.setPublicada(false);
        validarYNormalizar(db, false);
        repository.save(db);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteFisico(Long id) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
        repository.deleteById(db.getId());
        repository.flush();
    }

    /**
     * Obtiene la informacion requerida o lanza excepcion si no existe.
     */
    @Transactional(readOnly = true)
    public ClaseAprendizaje requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
    }

    /**
     * Valida la informacion recibida antes de continuar el proceso.
     */
    private void validarYNormalizar(ClaseAprendizaje c, boolean creando) {
        String curso = trim(c.getCurso());
        String area = normalizeAreaRequired(c.getArea());
        String titulo = trim(c.getTitulo());

        if (curso.isBlank()) throw new IllegalArgumentException("curso es obligatorio");
        if (titulo.isBlank()) throw new IllegalArgumentException("titulo es obligatorio");

        c.setCurso(curso);
        c.setArea(area);
        c.setTitulo(titulo);
        c.setDescripcion(trimToNull(c.getDescripcion()));
        if (c.getVisible() == null) c.setVisible(true);
        if (c.getPublicada() == null) c.setPublicada(false);

        if (Boolean.TRUE.equals(c.getPublicada()) && c.getFechaPublicacion() == null) {
            c.setFechaPublicacion(LocalDate.now());
        }
        if (Boolean.FALSE.equals(c.getPublicada()) && creando && c.getFechaPublicacion() == null) {
            c.setFechaPublicacion(LocalDate.now());
        }
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeAreaRequired(String area) {
        String norm = trim(area).toLowerCase();
        if (norm.isBlank()) {
            throw new IllegalArgumentException("area es obligatoria");
        }
        if (!AREAS_VALIDAS.contains(norm)) {
            throw new IllegalArgumentException("area invalida. Usa: etica, tecnica, marco_legal, adaptacion, mecanica");
        }
        return norm;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeAreaOptional(String area) {
        String norm = trim(area).toLowerCase();
        if (norm.isBlank()) return "";
        if (!AREAS_VALIDAS.contains(norm)) {
            throw new IllegalArgumentException("area invalida. Usa: etica, tecnica, marco_legal, adaptacion, mecanica");
        }
        return norm;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trimToNull(String s) {
        String out = trim(s);
        return out.isBlank() ? null : out;
    }
}
