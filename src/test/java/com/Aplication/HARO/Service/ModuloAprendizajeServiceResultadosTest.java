package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.ModuloAprendizaje;
import com.Aplication.HARO.Repository.EstudianteRepository;
import com.Aplication.HARO.Repository.ModuloAprendizajeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuloAprendizajeServiceResultadosTest {

    @Test
    void resultadosExamenIncluyenTiempoDeSimulacro() {
        ModuloAprendizajeRepository repository = mock(ModuloAprendizajeRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        ModuloAprendizajeService service = new ModuloAprendizajeService(repository, estudianteRepository);

        ModuloAprendizaje row = new ModuloAprendizaje();
        row.setId(90L);
        row.setIdEstudiante(12L);
        row.setCurso("B1");
        row.setModulo("Simulacro final");
        row.setPuntajeSimulador(92);
        row.setIntentos(2);
        row.setAprobado(true);
        row.setFechaEvaluacion(LocalDate.of(2026, 3, 27));
        row.setTiempoTotalMinutos(50);
        row.setTiempoCompletadoModuloMinutos(50);
        row.setPorcentajeCompletadoModulo(100);
        row.setActualizadoEn(Instant.parse("2026-03-27T15:00:00Z"));

        Estudiante estudiante = new Estudiante();
        estudiante.setId(12L);
        estudiante.setNombre("Ana");
        estudiante.setApellido("Perez");
        estudiante.setCategoria("B1");
        estudiante.setVisible(true);

        when(repository.findAll()).thenReturn(List.of(row));
        when(estudianteRepository.findByIdAndVisibleTrue(12L)).thenReturn(Optional.of(estudiante));

        List<ModuloAprendizajeService.ResultadoExamenAdmin> out = service.getResultadosExamenPresentados();

        assertEquals(1, out.size());
        ModuloAprendizajeService.ResultadoExamenAdmin result = out.get(0);
        assertEquals(50, result.tiempoTotalMinutos());
        assertEquals(50, result.tiempoCompletadoModuloMinutos());
        assertEquals(100, result.porcentajeCompletadoModulo());
        assertEquals("Aprobado", result.estado());
        assertNotNull(result.fecha());
    }
}
