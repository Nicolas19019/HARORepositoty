package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Model.EstudianteModuloAcceso;
import com.Aplication.HARO.Repository.EstudianteModuloAccesoRepository;
import com.Aplication.HARO.Repository.EstudianteRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EstudianteModuloAccesoServiceTest {

    @Test
    void registrarModuloPorEmailCreaRegistroSinIngresos() {
        EstudianteModuloAccesoRepository accesoRepository = mock(EstudianteModuloAccesoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudianteModuloAccesoService service = new EstudianteModuloAccesoService(accesoRepository, estudianteRepository);

        Estudiante estudiante = estudianteVisible(7L, "ana@correo.com", "ana.user", "123", "Ana", "Perez");
        when(estudianteRepository.findByEmailNormalizadoVisible("ana@correo.com")).thenReturn(Optional.of(estudiante));
        when(accesoRepository.findByIdEstudiante(7L)).thenReturn(Optional.empty());
        when(accesoRepository.save(any(EstudianteModuloAcceso.class))).thenAnswer(invocation -> {
            EstudianteModuloAcceso saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        EstudianteModuloAccesoService.RegistroResponse response = service.registrarModulo(
                new EstudianteModuloAccesoService.RegistroRequest(null, "ana@correo.com", null, null, "WEB_REGISTRO_MODULO")
        );

        assertTrue(response.ok());
        assertEquals(7L, response.idEstudiante());
        assertTrue(response.nuevoRegistro());
        assertNotNull(response.registradoEn());
        assertNull(response.primerIngresoEn());
        assertNull(response.ultimoIngresoEn());
        assertEquals(0, response.totalIngresos());
        assertEquals("WEB_REGISTRO_MODULO", response.origenRegistro());
    }

    @Test
    void registrarIngresoMarcaPrimerYUltimoIngresoYAcumulaConteo() {
        EstudianteModuloAccesoRepository accesoRepository = mock(EstudianteModuloAccesoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudianteModuloAccesoService service = new EstudianteModuloAccesoService(accesoRepository, estudianteRepository);

        Estudiante estudiante = estudianteVisible(11L, "luis@correo.com", "luis.user", "555", "Luis", "Lopez");
        EstudianteModuloAcceso existente = new EstudianteModuloAcceso();
        existente.setId(3L);
        existente.setIdEstudiante(11L);
        existente.setRegistradoEn(Instant.parse("2026-03-20T10:00:00Z"));
        existente.setOrigenRegistro("WEB_REGISTRO_MODULO");
        existente.setTotalIngresos(1);

        when(estudianteRepository.findByIdAndVisibleTrue(11L)).thenReturn(Optional.of(estudiante));
        when(accesoRepository.findByIdEstudiante(11L)).thenReturn(Optional.of(existente));
        when(accesoRepository.save(any(EstudianteModuloAcceso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteModuloAccesoService.RegistroResponse response = service.registrarIngresoPorEstudiante(11L);

        assertTrue(response.ok());
        assertEquals(11L, response.idEstudiante());
        assertFalse(response.nuevoRegistro());
        assertEquals(2, response.totalIngresos());
        assertNotNull(response.primerIngresoEn());
        assertNotNull(response.ultimoIngresoEn());
        assertEquals("WEB_REGISTRO_MODULO", response.origenRegistro());
    }

    @Test
    void resumenAdminCuentaRegistradosEIngresados() {
        EstudianteModuloAccesoRepository accesoRepository = mock(EstudianteModuloAccesoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudianteModuloAccesoService service = new EstudianteModuloAccesoService(accesoRepository, estudianteRepository);

        when(accesoRepository.count()).thenReturn(10L);
        when(accesoRepository.countByPrimerIngresoEnIsNotNull()).thenReturn(6L);

        EstudianteModuloAccesoService.ResumenAdmin resumen = service.getResumenAdmin();

        assertEquals(10L, resumen.estudiantesRegistrados());
        assertEquals(6L, resumen.estudiantesQueHanIngresado());
        assertEquals(4L, resumen.estudiantesPendientesPrimerIngreso());
    }

    @Test
    void listaAdminIncluyeDatosDeEstudianteYOrdenaPorUltimoIngreso() {
        EstudianteModuloAccesoRepository accesoRepository = mock(EstudianteModuloAccesoRepository.class);
        EstudianteRepository estudianteRepository = mock(EstudianteRepository.class);
        EstudianteModuloAccesoService service = new EstudianteModuloAccesoService(accesoRepository, estudianteRepository);

        EstudianteModuloAcceso primero = new EstudianteModuloAcceso();
        primero.setId(1L);
        primero.setIdEstudiante(1L);
        primero.setRegistradoEn(Instant.parse("2026-03-20T10:00:00Z"));
        primero.setPrimerIngresoEn(Instant.parse("2026-03-20T10:10:00Z"));
        primero.setUltimoIngresoEn(Instant.parse("2026-03-24T10:00:00Z"));
        primero.setTotalIngresos(3);
        primero.setOrigenRegistro("WEB");

        EstudianteModuloAcceso segundo = new EstudianteModuloAcceso();
        segundo.setId(2L);
        segundo.setIdEstudiante(2L);
        segundo.setRegistradoEn(Instant.parse("2026-03-21T10:00:00Z"));
        segundo.setPrimerIngresoEn(Instant.parse("2026-03-21T10:10:00Z"));
        segundo.setUltimoIngresoEn(Instant.parse("2026-03-23T10:00:00Z"));
        segundo.setTotalIngresos(1);
        segundo.setOrigenRegistro("WEB");

        when(accesoRepository.findAll()).thenReturn(List.of(segundo, primero));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteVisible(1L, "uno@correo.com", "uno", "111", "Ana", "Zuluaga")));
        when(estudianteRepository.findById(2L)).thenReturn(Optional.of(estudianteVisible(2L, "dos@correo.com", "dos", "222", "Beto", "Ariza")));

        List<EstudianteModuloAccesoService.EstudianteModuloAdminRow> out = service.getEstudiantesRegistradosModulo();

        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).idEstudiante());
        assertEquals("Ana Zuluaga", out.get(0).nombre());
        assertEquals(2L, out.get(1).idEstudiante());
    }

    private Estudiante estudianteVisible(Long id, String email, String usuario, String documento, String nombre, String apellido) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        estudiante.setEmail(email);
        estudiante.setUsuario(usuario);
        estudiante.setNumeroDocumento(documento);
        estudiante.setNombre(nombre);
        estudiante.setApellido(apellido);
        estudiante.setVisible(true);
        estudiante.setCategoria("B1");
        estudiante.setEstado("Activo");
        return estudiante;
    }
}
