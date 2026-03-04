package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ModuloAprendizajeRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class ModuloAprendizajeService {

    public record ResumenAprendizaje(
            int porcentajeGeneral,
            int modulosCompletados,
            int modulosTotales,
            int horasCompletadas,
            int horasTotales,
            int favoritos
    ) {}

    public record EstudianteUsoModulo(
            Long id,
            String nombre,
            String email,
            String documento,
            String categoria,
            String estado,
            int progreso,
            int modulosCompletados,
            int modulosTotales,
            int horasCompletadas,
            int horasTotales,
            int favoritos,
            LocalDate ultimaActividad
    ) {}

    public record ResultadoExamenAdmin(
            Long idRegistro,
            Long idEstudiante,
            String nombreEstudiante,
            String categoria,
            int puntaje,
            int aciertos,
            int totalPreguntas,
            int intento,
            String estado,
            LocalDate fecha
    ) {}

    private final ModuloAprendizajeRepository repository;
    private final EstudianteRepository estudianteRepository;

    public ModuloAprendizajeService(ModuloAprendizajeRepository repository,
                                    EstudianteRepository estudianteRepository) {
        this.repository = repository;
        this.estudianteRepository = estudianteRepository;
    }

    @Transactional(readOnly = true)
    public List<ModuloAprendizaje> getAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ModuloAprendizaje> getById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ModuloAprendizaje> getByEstudiante(Long idEstudiante) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        return repository.findByIdEstudianteOrderByFechaEvaluacionDesc(idEstudiante);
    }

    @Transactional(readOnly = true)
    public List<ModuloAprendizaje> getByEstudianteAndCurso(Long idEstudiante, String curso) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        String cursoNorm = trim(curso);
        if (cursoNorm.isBlank()) {
            throw new IllegalArgumentException("curso es obligatorio");
        }
        return repository.findByIdEstudianteAndCursoIgnoreCaseOrderByFechaEvaluacionDesc(idEstudiante, cursoNorm);
    }

    public ModuloAprendizaje create(ModuloAprendizaje input) {
        if (input == null) {
            throw new IllegalArgumentException("El registro de aprendizaje es requerido");
        }
        input.setId(null);
        validarYNormalizar(input);
        return repository.save(input);
    }

    public ModuloAprendizaje upsert(ModuloAprendizaje input) {
        if (input == null) {
            throw new IllegalArgumentException("El registro de aprendizaje es requerido");
        }

        String curso = trim(input.getCurso());
        String modulo = trim(input.getModulo());
        if (input.getIdEstudiante() == null || input.getIdEstudiante() <= 0) {
            throw new IllegalArgumentException("idEstudiante es obligatorio y debe ser mayor a 0");
        }
        if (curso.isBlank()) {
            throw new IllegalArgumentException("curso es obligatorio");
        }
        if (modulo.isBlank()) {
            throw new IllegalArgumentException("modulo es obligatorio");
        }

        ModuloAprendizaje row = repository
                .findTopByIdEstudianteAndCursoIgnoreCaseAndModuloIgnoreCaseOrderByIdDesc(
                        input.getIdEstudiante(), curso, modulo)
                .orElseGet(ModuloAprendizaje::new);

        if (row.getId() == null) {
            row.setIdEstudiante(input.getIdEstudiante());
            row.setCurso(curso);
            row.setModulo(modulo);
        }

        if (input.getPuntajeTeorico() != null) row.setPuntajeTeorico(input.getPuntajeTeorico());
        if (input.getPuntajePractico() != null) row.setPuntajePractico(input.getPuntajePractico());
        if (input.getPuntajeSimulador() != null) row.setPuntajeSimulador(input.getPuntajeSimulador());
        if (input.getPuntajeFinal() != null) row.setPuntajeFinal(input.getPuntajeFinal());
        if (input.getPorcentajeAvance() != null) row.setPorcentajeAvance(input.getPorcentajeAvance());
        if (input.getTiempoTotalMinutos() != null) row.setTiempoTotalMinutos(input.getTiempoTotalMinutos());
        if (input.getTiempoCompletadoModuloMinutos() != null) {
            row.setTiempoCompletadoModuloMinutos(input.getTiempoCompletadoModuloMinutos());
        }
        if (input.getPorcentajeCompletadoModulo() != null) {
            row.setPorcentajeCompletadoModulo(input.getPorcentajeCompletadoModulo());
        }
        if (input.getTiempoTotalCursoMinutos() != null) row.setTiempoTotalCursoMinutos(input.getTiempoTotalCursoMinutos());
        if (input.getTiempoCompletadoCursoMinutos() != null) {
            row.setTiempoCompletadoCursoMinutos(input.getTiempoCompletadoCursoMinutos());
        }
        if (input.getPorcentajeCompletadoCurso() != null) {
            row.setPorcentajeCompletadoCurso(input.getPorcentajeCompletadoCurso());
        }
        if (input.getIntentos() != null) row.setIntentos(input.getIntentos());
        if (input.getAprobado() != null) row.setAprobado(input.getAprobado());
        if (input.getFavorito() != null) row.setFavorito(input.getFavorito());
        if (input.getObservaciones() != null) row.setObservaciones(input.getObservaciones());
        if (input.getFechaEvaluacion() != null) row.setFechaEvaluacion(input.getFechaEvaluacion());

        validarYNormalizar(row);
        return repository.save(row);
    }

    public ModuloAprendizaje update(Long id, ModuloAprendizaje input) {
        if (input == null) {
            throw new IllegalArgumentException("El registro de aprendizaje es requerido");
        }

        ModuloAprendizaje db = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));

        if (input.getIdEstudiante() != null) db.setIdEstudiante(input.getIdEstudiante());
        if (input.getCurso() != null) db.setCurso(input.getCurso());
        if (input.getModulo() != null) db.setModulo(input.getModulo());
        if (input.getPuntajeTeorico() != null) db.setPuntajeTeorico(input.getPuntajeTeorico());
        if (input.getPuntajePractico() != null) db.setPuntajePractico(input.getPuntajePractico());
        if (input.getPuntajeSimulador() != null) db.setPuntajeSimulador(input.getPuntajeSimulador());
        if (input.getPuntajeFinal() != null) db.setPuntajeFinal(input.getPuntajeFinal());
        if (input.getPorcentajeAvance() != null) db.setPorcentajeAvance(input.getPorcentajeAvance());
        if (input.getTiempoTotalMinutos() != null) db.setTiempoTotalMinutos(input.getTiempoTotalMinutos());
        if (input.getTiempoCompletadoModuloMinutos() != null) db.setTiempoCompletadoModuloMinutos(input.getTiempoCompletadoModuloMinutos());
        if (input.getPorcentajeCompletadoModulo() != null) db.setPorcentajeCompletadoModulo(input.getPorcentajeCompletadoModulo());
        if (input.getTiempoTotalCursoMinutos() != null) db.setTiempoTotalCursoMinutos(input.getTiempoTotalCursoMinutos());
        if (input.getTiempoCompletadoCursoMinutos() != null) db.setTiempoCompletadoCursoMinutos(input.getTiempoCompletadoCursoMinutos());
        if (input.getPorcentajeCompletadoCurso() != null) db.setPorcentajeCompletadoCurso(input.getPorcentajeCompletadoCurso());
        if (input.getIntentos() != null) db.setIntentos(input.getIntentos());
        if (input.getAprobado() != null) db.setAprobado(input.getAprobado());
        if (input.getFavorito() != null) db.setFavorito(input.getFavorito());
        if (input.getObservaciones() != null) db.setObservaciones(input.getObservaciones());
        if (input.getFechaEvaluacion() != null) db.setFechaEvaluacion(input.getFechaEvaluacion());

        validarYNormalizar(db);
        return repository.save(db);
    }

    @Transactional(readOnly = true)
    public List<String> getFavoritos(Long idEstudiante, String curso) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        String cursoNorm = trim(curso);
        if (cursoNorm.isBlank()) {
            throw new IllegalArgumentException("curso es obligatorio");
        }

        Set<String> out = new LinkedHashSet<>();
        for (ModuloAprendizaje row : repository.findByIdEstudianteAndCursoIgnoreCaseAndFavoritoTrueOrderByFechaEvaluacionDesc(idEstudiante, cursoNorm)) {
            String modulo = trim(row.getModulo());
            if (!modulo.isBlank()) {
                out.add(modulo);
            }
        }
        return new ArrayList<>(out);
    }

    public void saveFavoritos(Long idEstudiante, String curso, List<String> modulosFavoritos) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        String cursoNorm = trim(curso);
        if (cursoNorm.isBlank()) {
            throw new IllegalArgumentException("curso es obligatorio");
        }

        Set<String> favoritosNorm = new LinkedHashSet<>();
        if (modulosFavoritos != null) {
            for (String m : modulosFavoritos) {
                String modulo = trim(m);
                if (!modulo.isBlank()) favoritosNorm.add(modulo);
            }
        }
        Set<String> favoritosLower = new LinkedHashSet<>();
        for (String modulo : favoritosNorm) {
            favoritosLower.add(modulo.toLowerCase());
        }

        List<ModuloAprendizaje> existentes = repository.findByIdEstudianteAndCursoIgnoreCaseOrderByFechaEvaluacionDesc(idEstudiante, cursoNorm);
        Map<String, ModuloAprendizaje> porModulo = new LinkedHashMap<>();
        for (ModuloAprendizaje row : existentes) {
            String key = trim(row.getModulo()).toLowerCase();
            if (key.isBlank()) continue;
            ModuloAprendizaje actual = porModulo.get(key);
            if (actual == null || safeId(row) > safeId(actual)) {
                porModulo.put(key, row);
            }
        }

        List<ModuloAprendizaje> cambios = new ArrayList<>();
        for (Map.Entry<String, ModuloAprendizaje> entry : porModulo.entrySet()) {
            boolean fav = favoritosLower.contains(entry.getKey());
            ModuloAprendizaje row = entry.getValue();
            if (!Boolean.valueOf(fav).equals(row.getFavorito())) {
                row.setFavorito(fav);
                validarYNormalizar(row);
                cambios.add(row);
            }
        }

        for (String moduloFav : favoritosNorm) {
            if (porModulo.containsKey(moduloFav.toLowerCase())) continue;

            ModuloAprendizaje nuevo = new ModuloAprendizaje();
            nuevo.setIdEstudiante(idEstudiante);
            nuevo.setCurso(cursoNorm);
            nuevo.setModulo(moduloFav);
            nuevo.setFavorito(true);
            nuevo.setPorcentajeAvance(0);
            nuevo.setIntentos(0);
            nuevo.setAprobado(false);
            nuevo.setFechaEvaluacion(LocalDate.now());
            validarYNormalizar(nuevo);
            cambios.add(nuevo);
        }

        if (!cambios.isEmpty()) {
            repository.saveAll(cambios);
        }
    }

    @Transactional(readOnly = true)
    public ResumenAprendizaje getResumen(Long idEstudiante) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }

        List<ModuloAprendizaje> rows = repository.findByIdEstudianteOrderByFechaEvaluacionDesc(idEstudiante);
        if (rows.isEmpty()) {
            return new ResumenAprendizaje(0, 0, 0, 0, 0, 0);
        }

        Map<String, ModuloAprendizaje> latestByModulo = new LinkedHashMap<>();
        for (ModuloAprendizaje row : rows) {
            String key = (trim(row.getCurso()) + "|" + trim(row.getModulo())).toLowerCase();
            ModuloAprendizaje actual = latestByModulo.get(key);
            if (actual == null || safeId(row) > safeId(actual)) {
                latestByModulo.put(key, row);
            }
        }

        int modulosTotales = latestByModulo.size();
        int modulosCompletados = 0;
        int favoritos = 0;
        int totalMin = 0;
        int doneMin = 0;
        int sumaPct = 0;
        int rowsWithPct = 0;

        for (ModuloAprendizaje row : latestByModulo.values()) {
            Integer pctModulo = row.getPorcentajeCompletadoModulo();
            if (pctModulo == null) pctModulo = row.getPorcentajeAvance();

            if (pctModulo != null) {
                sumaPct += pctModulo;
                rowsWithPct += 1;
            }

            if (Boolean.TRUE.equals(row.getAprobado()) || (pctModulo != null && pctModulo >= 100)) {
                modulosCompletados += 1;
            }

            if (Boolean.TRUE.equals(row.getFavorito())) {
                favoritos += 1;
            }

            Integer tTotal = firstNonNull(row.getTiempoTotalMinutos(), row.getTiempoTotalCursoMinutos());
            Integer tDone = firstNonNull(row.getTiempoCompletadoModuloMinutos(), row.getTiempoCompletadoCursoMinutos());
            if (tTotal != null && tTotal > 0) totalMin += tTotal;
            if (tDone != null && tDone > 0) doneMin += tDone;
        }

        int porcentajeGeneral;
        if (totalMin > 0) {
            porcentajeGeneral = Math.round((doneMin * 100.0f) / totalMin);
        } else if (rowsWithPct > 0) {
            porcentajeGeneral = Math.round((float) sumaPct / rowsWithPct);
        } else {
            porcentajeGeneral = 0;
        }

        return new ResumenAprendizaje(
                clampPct(porcentajeGeneral),
                modulosCompletados,
                modulosTotales,
                Math.round(doneMin / 60.0f),
                Math.round(totalMin / 60.0f),
                favoritos
        );
    }

    @Transactional(readOnly = true)
    public List<EstudianteUsoModulo> getEstudiantesConUsoModulo() {
        List<Long> ids = repository.findDistinctIdEstudianteOrderByIdEstudianteAsc();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<EstudianteUsoModulo> out = new ArrayList<>();
        for (Long idEstudiante : ids) {
            if (idEstudiante == null || idEstudiante <= 0) {
                continue;
            }

            Estudiante estudiante = estudianteRepository.findByIdAndVisibleTrue(idEstudiante).orElse(null);
            if (estudiante == null) {
                continue;
            }

            ResumenAprendizaje resumen = getResumen(idEstudiante);
            if (resumen.modulosTotales() <= 0) {
                continue;
            }

            LocalDate ultimaActividad = repository
                    .findTopByIdEstudianteOrderByActualizadoEnDescIdDesc(idEstudiante)
                    .map(this::toLocalDateActividad)
                    .orElse(null);

            out.add(new EstudianteUsoModulo(
                    idEstudiante,
                    nombreCompleto(estudiante),
                    trim(estudiante.getEmail()),
                    trim(estudiante.getNumeroDocumento()),
                    trim(estudiante.getCategoria()),
                    estadoEstudiante(estudiante),
                    resumen.porcentajeGeneral(),
                    resumen.modulosCompletados(),
                    resumen.modulosTotales(),
                    resumen.horasCompletadas(),
                    resumen.horasTotales(),
                    resumen.favoritos(),
                    ultimaActividad
            ));
        }

        out.sort(
                Comparator
                        .comparing(EstudianteUsoModulo::ultimaActividad,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EstudianteUsoModulo::progreso, Comparator.reverseOrder())
                        .thenComparing(EstudianteUsoModulo::id)
        );
        return out;
    }

    @Transactional(readOnly = true)
    public List<ResultadoExamenAdmin> getResultadosExamenPresentados() {
        List<ModuloAprendizaje> all = repository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        Map<Long, ModuloAprendizaje> latestExamByStudent = new LinkedHashMap<>();
        for (ModuloAprendizaje row : all) {
            if (!esRegistroExamenPresentado(row)) {
                continue;
            }
            Long idEstudiante = row.getIdEstudiante();
            if (idEstudiante == null || idEstudiante <= 0) {
                continue;
            }

            ModuloAprendizaje actual = latestExamByStudent.get(idEstudiante);
            if (actual == null || esMasReciente(row, actual)) {
                latestExamByStudent.put(idEstudiante, row);
            }
        }

        if (latestExamByStudent.isEmpty()) {
            return List.of();
        }

        List<ResultadoExamenAdmin> out = new ArrayList<>();
        final int totalPreguntas = 10;
        for (Map.Entry<Long, ModuloAprendizaje> entry : latestExamByStudent.entrySet()) {
            Long idEstudiante = entry.getKey();
            ModuloAprendizaje row = entry.getValue();

            Estudiante estudiante = estudianteRepository.findByIdAndVisibleTrue(idEstudiante).orElse(null);
            if (estudiante == null) {
                continue;
            }

            int puntaje = clampPct(resolveExamScore(row));
            int aciertos = Math.round((puntaje * totalPreguntas) / 100.0f);
            int intento = row.getIntentos() != null && row.getIntentos() > 0 ? row.getIntentos() : 1;
            boolean aprobado = Boolean.TRUE.equals(row.getAprobado()) || puntaje >= 80;
            LocalDate fecha = toLocalDateActividad(row);

            out.add(new ResultadoExamenAdmin(
                    row.getId(),
                    idEstudiante,
                    nombreCompleto(estudiante),
                    trim(estudiante.getCategoria()),
                    puntaje,
                    aciertos,
                    totalPreguntas,
                    intento,
                    aprobado ? "Aprobado" : "No aprobado",
                    fecha
            ));
        }

        out.sort(
                Comparator
                        .comparing(ResultadoExamenAdmin::fecha, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ResultadoExamenAdmin::puntaje, Comparator.reverseOrder())
                        .thenComparing(ResultadoExamenAdmin::idRegistro, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return out;
    }

    public void delete(Long id) {
        try {
            repository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("Registro no encontrado: " + id);
        }
    }

    private LocalDate toLocalDateActividad(ModuloAprendizaje row) {
        if (row == null) return null;
        if (row.getFechaEvaluacion() != null) {
            return row.getFechaEvaluacion();
        }
        if (row.getActualizadoEn() != null) {
            return row.getActualizadoEn().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (row.getCreadoEn() != null) {
            return row.getCreadoEn().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private String nombreCompleto(Estudiante e) {
        String nombre = trim(e == null ? null : e.getNombre());
        String apellido = trim(e == null ? null : e.getApellido());
        String full = (nombre + " " + apellido).trim();
        if (!full.isBlank()) return full;
        if (!nombre.isBlank()) return nombre;
        if (!apellido.isBlank()) return apellido;
        return "Estudiante " + (e == null || e.getId() == null ? "" : e.getId());
    }

    private String estadoEstudiante(Estudiante e) {
        String estado = trim(e == null ? null : e.getEstado());
        return estado.isBlank() ? "Activo" : estado;
    }

    private boolean esRegistroExamenPresentado(ModuloAprendizaje row) {
        if (row == null) return false;
        String modulo = trim(row.getModulo()).toLowerCase();
        boolean esExamen = modulo.contains("examen") || modulo.contains("simulacro") || modulo.contains("simulador");
        if (!esExamen) return false;

        if (row.getIntentos() != null && row.getIntentos() > 0) return true;
        if (row.getPuntajeTeorico() != null) return true;
        if (row.getPuntajeSimulador() != null) return true;
        if (row.getPuntajeFinal() != null) return true;
        if (row.getPorcentajeCompletadoModulo() != null) return true;
        if (row.getPorcentajeAvance() != null) return true;
        return Boolean.TRUE.equals(row.getAprobado());
    }

    private boolean esMasReciente(ModuloAprendizaje candidate, ModuloAprendizaje current) {
        if (candidate == null) return false;
        if (current == null) return true;
        if (candidate.getActualizadoEn() != null && current.getActualizadoEn() != null) {
            int cmp = candidate.getActualizadoEn().compareTo(current.getActualizadoEn());
            if (cmp != 0) return cmp > 0;
        } else if (candidate.getActualizadoEn() != null) {
            return true;
        } else if (current.getActualizadoEn() != null) {
            return false;
        }
        return safeId(candidate) > safeId(current);
    }

    private int resolveExamScore(ModuloAprendizaje row) {
        if (row == null) return 0;
        Integer score = firstNonNull(
                row.getPuntajeSimulador(),
                firstNonNull(
                        row.getPuntajeTeorico(),
                        firstNonNull(
                                row.getPuntajeFinal(),
                                firstNonNull(row.getPorcentajeCompletadoModulo(), row.getPorcentajeAvance())
                        )
                )
        );
        return score == null ? 0 : score;
    }

    private void validarYNormalizar(ModuloAprendizaje row) {
        if (row.getIdEstudiante() == null || row.getIdEstudiante() <= 0) {
            throw new IllegalArgumentException("idEstudiante es obligatorio y debe ser mayor a 0");
        }

        String curso = trim(row.getCurso());
        if (curso.isBlank()) {
            throw new IllegalArgumentException("curso es obligatorio");
        }
        row.setCurso(curso);

        String modulo = trim(row.getModulo());
        if (modulo.isBlank()) {
            throw new IllegalArgumentException("modulo es obligatorio");
        }
        row.setModulo(modulo);

        row.setPuntajeTeorico(normalizarPuntaje("puntajeTeorico", row.getPuntajeTeorico()));
        row.setPuntajePractico(normalizarPuntaje("puntajePractico", row.getPuntajePractico()));
        row.setPuntajeSimulador(normalizarPuntaje("puntajeSimulador", row.getPuntajeSimulador()));
        row.setPuntajeFinal(normalizarPuntaje("puntajeFinal", row.getPuntajeFinal()));
        row.setPorcentajeAvance(normalizarPuntaje("porcentajeAvance", row.getPorcentajeAvance()));
        row.setPorcentajeCompletadoModulo(
                normalizarPuntaje("porcentajeCompletadoModulo", row.getPorcentajeCompletadoModulo())
        );
        row.setPorcentajeCompletadoCurso(
                normalizarPuntaje("porcentajeCompletadoCurso", row.getPorcentajeCompletadoCurso())
        );

        row.setTiempoTotalMinutos(normalizarNoNegativo("tiempoTotalMinutos", row.getTiempoTotalMinutos()));
        row.setTiempoCompletadoModuloMinutos(
                normalizarNoNegativo("tiempoCompletadoModuloMinutos", row.getTiempoCompletadoModuloMinutos())
        );
        row.setTiempoTotalCursoMinutos(normalizarNoNegativo("tiempoTotalCursoMinutos", row.getTiempoTotalCursoMinutos()));
        row.setTiempoCompletadoCursoMinutos(
                normalizarNoNegativo("tiempoCompletadoCursoMinutos", row.getTiempoCompletadoCursoMinutos())
        );

        if (row.getIntentos() == null) row.setIntentos(0);
        if (row.getIntentos() < 0) {
            throw new IllegalArgumentException("intentos no puede ser negativo");
        }

        if (row.getAprobado() == null) row.setAprobado(false);
        if (row.getFavorito() == null) row.setFavorito(false);

        if (row.getPuntajeFinal() == null) {
            Integer promedio = promedioPuntajes(
                    row.getPuntajeTeorico(),
                    row.getPuntajePractico(),
                    row.getPuntajeSimulador()
            );
            row.setPuntajeFinal(promedio);
        }

        validarRelacionTiempo(
                "modulo",
                row.getTiempoTotalMinutos(),
                row.getTiempoCompletadoModuloMinutos()
        );
        validarRelacionTiempo(
                "curso",
                row.getTiempoTotalCursoMinutos(),
                row.getTiempoCompletadoCursoMinutos()
        );

        if (row.getPorcentajeCompletadoModulo() == null) {
            Integer pctModulo = calcularPorcentajeDesdeTiempos(
                    row.getTiempoCompletadoModuloMinutos(),
                    row.getTiempoTotalMinutos()
            );
            if (pctModulo != null) {
                row.setPorcentajeCompletadoModulo(pctModulo);
            }
        }

        if (row.getPorcentajeCompletadoModulo() == null && row.getPorcentajeAvance() != null) {
            row.setPorcentajeCompletadoModulo(row.getPorcentajeAvance());
        }

        if (row.getPorcentajeCompletadoCurso() == null) {
            Integer pctCurso = calcularPorcentajeDesdeTiempos(
                    row.getTiempoCompletadoCursoMinutos(),
                    row.getTiempoTotalCursoMinutos()
            );
            if (pctCurso != null) {
                row.setPorcentajeCompletadoCurso(pctCurso);
            }
        }

        if (row.getPorcentajeAvance() == null) {
            if (row.getPorcentajeCompletadoCurso() != null) {
                row.setPorcentajeAvance(row.getPorcentajeCompletadoCurso());
            } else if (row.getPorcentajeCompletadoModulo() != null) {
                row.setPorcentajeAvance(row.getPorcentajeCompletadoModulo());
            }
        }

        if (row.getFechaEvaluacion() == null) {
            row.setFechaEvaluacion(LocalDate.now());
        }

        if (row.getObservaciones() != null && row.getObservaciones().length() > 1000) {
            throw new IllegalArgumentException("observaciones excede 1000 caracteres");
        }
    }

    private Integer normalizarPuntaje(String campo, Integer valor) {
        if (valor == null) return null;
        if (valor < 0 || valor > 100) {
            throw new IllegalArgumentException(campo + " debe estar entre 0 y 100");
        }
        return valor;
    }

    private Integer normalizarNoNegativo(String campo, Integer valor) {
        if (valor == null) return null;
        if (valor < 0) {
            throw new IllegalArgumentException(campo + " no puede ser negativo");
        }
        return valor;
    }

    private void validarRelacionTiempo(String ambito, Integer total, Integer completado) {
        if (total == null || completado == null) return;
        if (total == 0 && completado > 0) {
            throw new IllegalArgumentException("tiempo completado de " + ambito + " no puede ser mayor a 0 si el total es 0");
        }
        if (total > 0 && completado > total) {
            throw new IllegalArgumentException("tiempo completado de " + ambito + " no puede superar el tiempo total");
        }
    }

    private Integer calcularPorcentajeDesdeTiempos(Integer completado, Integer total) {
        if (completado == null || total == null) return null;
        if (total == 0) return 0;
        return Math.round((completado * 100.0f) / total);
    }

    private Integer promedioPuntajes(Integer... valores) {
        List<Integer> lista = new ArrayList<>();
        for (Integer v : valores) {
            if (v != null) lista.add(v);
        }
        if (lista.isEmpty()) return null;

        int suma = 0;
        for (Integer v : lista) suma += v;
        return Math.round((float) suma / lista.size());
    }

    private Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private int clampPct(int pct) {
        if (pct < 0) return 0;
        if (pct > 100) return 100;
        return pct;
    }

    private long safeId(ModuloAprendizaje row) {
        return row == null || row.getId() == null ? 0L : row.getId();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
