package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Profesor;
import com.Aplication.HARO.Repository.ProfesorRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfesorServiceSedeTest {

    @Test
    void createProfesorGuardaSedeNormalizada() {
        ProfesorRepository repository = mock(ProfesorRepository.class);
        when(repository.save(any(Profesor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfesorService service = new ProfesorService(repository);

        Profesor profesor = new Profesor();
        profesor.setCedula("123");
        profesor.setNombre("Luis");
        profesor.setApellido("Rojas");
        profesor.setCategoria("carro");
        profesor.setSede("  Kennedy   Central  ");

        Profesor saved = service.createProfesor(profesor);

        assertEquals("Kennedy Central", saved.getSede());
    }

    @Test
    void updateProfesorActualizaSede() {
        ProfesorRepository repository = mock(ProfesorRepository.class);
        Profesor actual = new Profesor();
        actual.setId(5L);
        actual.setCedula("123");
        actual.setNombre("Luis");
        actual.setApellido("Rojas");
        actual.setCategoria("carro");
        actual.setSede("Kennedy");

        when(repository.findById(5L)).thenReturn(Optional.of(actual));
        when(repository.save(any(Profesor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfesorService service = new ProfesorService(repository);

        Profesor patch = new Profesor();
        patch.setSede("  Eden  ");

        Profesor saved = service.updateProfesor(5L, patch);

        assertEquals("Eden", saved.getSede());
    }
}
