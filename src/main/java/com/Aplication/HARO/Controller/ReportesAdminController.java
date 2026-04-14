package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Model.Clase;
import com.Aplication.HARO.Service.ClaseService;
import com.Aplication.HARO.Service.ModuloAprendizajeService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({
        "/api/reportes",
        "/api/informes",
        "/api/admin/reportes",
        "/api/admin/informes",
        "/api/modulos-aprendizaje/admin/reportes",
        "/api/modulos-aprendizaje/admin/informes",
        "/api/modulo-aprendizaje/admin/reportes",
        "/api/modulo-aprendizaje/admin/informes",
        "/api/modulos-aprendisaje/admin/reportes",
        "/api/modulos-aprendisaje/admin/informes"
})
/**
 * Controlador REST para reportes admin.
 */
@CrossOrigin(origins = "*")
public class ReportesAdminController {

    /**
     * Registro de salida para clase reporte.
     */
    public record ClaseReporteItem(
            Long id,
            Long idEstudiante,
            Long idProfesor,
            String fecha,
            String horaInicio,
            String horaFin,
            String estado,
            String estadoClase
    ) {}

    /**
     * Resumen de apoyo para indicadores principales del reporte.
     */
    public record KpisReporte(
            int totalEstudiantesModulo,
            int totalResultadosExamen,
            int totalClases,
            int examenesAprobados,
            int examenesNoAprobados,
            double promedioPuntajeExamen
    ) {}

    /**
     * DTO de salida para reportes admin.
     */
    public record ReportesAdminResponse(
            String generadoEn,
            List<ModuloAprendizajeService.EstudianteUsoModulo> students,
            List<ModuloAprendizajeService.EstudianteUsoModulo> estudiantesModulo,
            List<ModuloAprendizajeService.ResultadoExamenAdmin> results,
            List<ModuloAprendizajeService.ResultadoExamenAdmin> resultadosExamen,
            List<ClaseReporteItem> classes,
            List<ClaseReporteItem> clases,
            KpisReporte kpis,
            Map<String, Long> examenesPorCategoria,
            Map<String, Long> examenesPorEstado,
            Map<String, Long> clasesPorEstado,
            Map<String, Long> clasesPorEstadoClase
    ) {}

    private final ModuloAprendizajeService moduloAprendizajeService;
    private final ClaseService claseService;

    /**
     * Inyecta las dependencias necesarias del controlador.
     */
    public ReportesAdminController(ModuloAprendizajeService moduloAprendizajeService,
                                   ClaseService claseService) {
        this.moduloAprendizajeService = moduloAprendizajeService;
        this.claseService = claseService;
    }

    @GetMapping({
            "",
            "/",
            "/resumen",
            "/dashboard",
            "/datos"
    })
/**
 * Consolida el reporte administrativo principal.
 */
    public ReportesAdminResponse getReportesAdmin() {
        List<ModuloAprendizajeService.EstudianteUsoModulo> students = moduloAprendizajeService.getEstudiantesConUsoModulo();
        List<ModuloAprendizajeService.ResultadoExamenAdmin> results = moduloAprendizajeService.getResultadosExamenPresentados();
        List<ClaseReporteItem> classes = claseService.getAllClases()
                .stream()
                .map(this::toClaseItem)
                .toList();

        int aprobados = (int) results.stream()
                .filter(r -> "aprobado".equalsIgnoreCase(safe(r.estado())))
                .count();
        int noAprobados = Math.max(0, results.size() - aprobados);
        double promedio = round2(results.stream().mapToInt(ModuloAprendizajeService.ResultadoExamenAdmin::puntaje).average().orElse(0.0));

        return new ReportesAdminResponse(
                OffsetDateTime.now(ZoneId.of("America/Bogota")).toString(),
                students,
                students,
                results,
                results,
                classes,
                classes,
                new KpisReporte(
                        students.size(),
                        results.size(),
                        classes.size(),
                        aprobados,
                        noAprobados,
                        promedio
                ),
                countBy(results, r -> emptyTo("Sin categoria", r.categoria())),
                countBy(results, r -> emptyTo("Sin estado", r.estado())),
                countBy(classes, c -> emptyTo("Sin estado", c.estado())),
                countBy(classes, c -> emptyTo("Sin estado clase", c.estadoClase()))
        );
    }

/**
 * Lista estudiantes con informacion consolidada para el modulo.
 */
    @GetMapping({"/estudiantes", "/students"})
    public List<ModuloAprendizajeService.EstudianteUsoModulo> getEstudiantesModulo() {
        return moduloAprendizajeService.getEstudiantesConUsoModulo();
    }

/**
 * Lista resultados consolidados para la vista administrativa.
 */
    @GetMapping({"/resultados", "/results", "/resultados-examen"})
    public List<ModuloAprendizajeService.ResultadoExamenAdmin> getResultadosExamen() {
        return moduloAprendizajeService.getResultadosExamenPresentados();
    }

/**
 * Lista las clases usadas en el reporte.
 */
    @GetMapping({"/clases", "/classes"})
    public List<ClaseReporteItem> getClases() {
        return claseService.getAllClases()
                .stream()
                .map(this::toClaseItem)
                .toList();
    }

/**
 * Convierte el valor recibido al formato requerido.
 */
    private ClaseReporteItem toClaseItem(Clase c) {
        return new ClaseReporteItem(
                c.getId(),
                c.getId_estudiante(),
                c.getId_profesor(),
                c.getFecha() == null ? null : c.getFecha().toString(),
                c.getHoraInicio() == null ? null : c.getHoraInicio().toString(),
                c.getHoraFin() == null ? null : c.getHoraFin().toString(),
                safe(c.getEstado()),
                safe(c.getEstadoClase())
        );
    }

/**
 * Agrupa una lista y cuenta sus elementos por la llave indicada.
 */
    private static <T> Map<String, Long> countBy(List<T> rows, java.util.function.Function<T, String> keyMapper) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> {
                            String key = keyMapper.apply(row);
                            return key.isBlank() ? "Sin dato" : key;
                        },
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

/**
 * Devuelve una cadena segura para operaciones internas.
 */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

/**
 * Devuelve un valor de respaldo cuando la cadena llega vacia.
 */
    private static String emptyTo(String fallback, String value) {
        String v = safe(value);
        return v.isBlank() ? fallback : v;
    }

/**
 * Redondea un numero decimal a dos cifras.
 */
    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
