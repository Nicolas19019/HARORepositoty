package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Repository.ClaseAprendizajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

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

    @Transactional(readOnly = true)
    public List<ClaseAprendizaje> getAllAdmin() {
        return repository.findAllByOrderByIdDesc();
    }

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

    public ClaseAprendizaje create(ClaseAprendizaje in) {
        if (in == null) throw new IllegalArgumentException("La clase es requerida");
        in.setId(null);
        validarYNormalizar(in, true);
        return repository.save(in);
    }

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

    public ClaseAprendizaje patchEstado(Long id, Boolean publicada, Boolean visible) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));

        if (publicada != null) db.setPublicada(publicada);
        if (visible != null) db.setVisible(visible);
        validarYNormalizar(db, false);
        return repository.save(db);
    }

    public void deleteLogico(Long id) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
        db.setVisible(false);
        db.setPublicada(false);
        validarYNormalizar(db, false);
        repository.save(db);
    }

    public void deleteFisico(Long id) {
        ClaseAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
        repository.deleteById(db.getId());
        repository.flush();
    }

    @Transactional(readOnly = true)
    public ClaseAprendizaje requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clase no encontrada: " + id));
    }

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

    private String normalizeAreaOptional(String area) {
        String norm = trim(area).toLowerCase();
        if (norm.isBlank()) return "";
        if (!AREAS_VALIDAS.contains(norm)) {
            throw new IllegalArgumentException("area invalida. Usa: etica, tecnica, marco_legal, adaptacion, mecanica");
        }
        return norm;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String trimToNull(String s) {
        String out = trim(s);
        return out.isBlank() ? null : out;
    }
}