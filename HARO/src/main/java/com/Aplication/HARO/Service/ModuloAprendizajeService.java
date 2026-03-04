package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Repository.ModuloAprendizajeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ModuloAprendizajeService {

    private final ModuloAprendizajeRepository repository;

    public ModuloAprendizajeService(ModuloAprendizajeRepository repository) {
        this.repository = repository;
    }

    public List<ModuloAprendizaje> getAll() {
        return repository.findAll();
    }

    public Optional<ModuloAprendizaje> getById(Long id) {
        return repository.findById(id);
    }

    public List<ModuloAprendizaje> getByEstudiante(Long idEstudiante) {
        if (idEstudiante == null || idEstudiante <= 0) {
            throw new IllegalArgumentException("idEstudiante invalido");
        }
        return repository.findByIdEstudianteOrderByFechaEvaluacionDesc(idEstudiante);
    }

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
        if (input.getObservaciones() != null) db.setObservaciones(input.getObservaciones());
        if (input.getFechaEvaluacion() != null) db.setFechaEvaluacion(input.getFechaEvaluacion());

        validarYNormalizar(db);
        return repository.save(db);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Registro no encontrado: " + id);
        }
        repository.deleteById(id);
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
