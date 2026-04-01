package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EstudianteServiceFechaMatriculaTest {

    @Test
    void createMatriculadoAsignaFechaMatriculaSiNoViene() {
        EstudianteRepository repo = mock(EstudianteRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("$2a$hash");
        when(repo.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteService service = new EstudianteService(repo, encoder);

        Estudiante in = new Estudiante();
        in.setNombre("Laura");
        in.setApellido("Gomez");
        in.setNumeroDocumento("123456789");
        in.setUsuario("laura.gomez");
        in.setEmail("laura@correo.com");
        in.setTipoEstudiante("matriculado");

        Estudiante out = service.createEstudiante(in);

        assertNotNull(out.getFechaMatricula());
        assertEquals(LocalDate.now(), out.getFechaMatricula());
        assertEquals("PRESENCIAL", out.getOrigenMatricula());
    }

    @Test
    void createProspectoNoAsignaFechaMatricula() {
        EstudianteRepository repo = mock(EstudianteRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("$2a$hash");
        when(repo.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteService service = new EstudianteService(repo, encoder);

        Estudiante in = new Estudiante();
        in.setNombre("Ana");
        in.setApellido("Rios");
        in.setNumeroDocumento("987654321");
        in.setUsuario("ana.rios");
        in.setEmail("ana@correo.com");

        Estudiante out = service.createEstudiante(in);

        assertEquals("prospecto", out.getTipoEstudiante());
        assertNull(out.getFechaMatricula());
        assertEquals("", out.getOrigenMatricula());
    }

    @Test
    void updateHaciaMatriculadoAsignaFechaMatriculaSiFalta() {
        EstudianteRepository repo = mock(EstudianteRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.findById(5L)).thenReturn(Optional.of(estudianteBase(5L)));
        when(repo.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteService service = new EstudianteService(repo, encoder);

        Estudiante patch = new Estudiante();
        patch.setTipoEstudiante("matriculado");

        Estudiante out = service.updateEstudiante(5L, patch);

        assertEquals("matriculado", out.getTipoEstudiante());
        assertNotNull(out.getFechaMatricula());
        assertEquals("PRESENCIAL", out.getOrigenMatricula());
    }

    @Test
    void createMatriculadoRespetaOrigenExplicito() {
        EstudianteRepository repo = mock(EstudianteRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("$2a$hash");
        when(repo.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteService service = new EstudianteService(repo, encoder);

        Estudiante in = new Estudiante();
        in.setNombre("Nora");
        in.setApellido("Gil");
        in.setNumeroDocumento("333444555");
        in.setUsuario("nora.gil");
        in.setEmail("nora@correo.com");
        in.setTipoEstudiante("matriculado");
        in.setOrigenMatricula("CHATBOT");

        Estudiante out = service.createEstudiante(in);

        assertEquals("CHATBOT", out.getOrigenMatricula());
    }

    @Test
    void updateDebePermitirModificarConsecutivo() {
        EstudianteRepository repo = mock(EstudianteRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.findById(5L)).thenReturn(Optional.of(estudianteBase(5L)));
        when(repo.findByConsecutivo(123L)).thenReturn(Optional.empty());
        when(repo.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EstudianteService service = new EstudianteService(repo, encoder);

        Estudiante patch = new Estudiante();
        patch.setConsecutivo(123L);

        Estudiante out = service.updateEstudiante(5L, patch);

        assertEquals(123L, out.getConsecutivo());
    }

    private Estudiante estudianteBase(Long id) {
        Estudiante e = new Estudiante();
        e.setId(id);
        e.setConsecutivo(10L);
        e.setNombre("Base");
        e.setApellido("Student");
        e.setNumeroDocumento("55555");
        e.setUsuario("base.student");
        e.setEmail("base@correo.com");
        e.setTipoEstudiante("prospecto");
        return e;
    }
}
