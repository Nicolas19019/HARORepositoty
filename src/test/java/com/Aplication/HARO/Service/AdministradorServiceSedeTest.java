package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Repository.AdministradorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdministradorServiceSedeTest {

    @Test
    void crearAdministradorGuardaSedeNormalizada() {
        AdministradorRepository repository = mock(AdministradorRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Clave123*")).thenReturn("$2a$adminhash");
        when(repository.save(any(Administrador.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdministradorService service = new AdministradorService(repository, encoder);

        Administrador admin = new Administrador();
        admin.setCorreo("admin@correo.com");
        admin.setUsuario("admin1");
        admin.setNombre("Admin Uno");
        admin.setContrasenaHash("Clave123*");
        admin.setSede("  Kennedy   Central  ");

        Administrador saved = service.crear(admin);

        assertEquals("Kennedy Central", saved.getSede());
    }

    @Test
    void actualizarAdministradorActualizaSede() {
        AdministradorRepository repository = mock(AdministradorRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        Administrador actual = new Administrador();
        actual.setId(8L);
        actual.setCorreo("admin@correo.com");
        actual.setUsuario("admin1");
        actual.setNombre("Admin Uno");
        actual.setContrasenaHash("$2a$existente");
        actual.setSede("Kennedy");

        when(repository.findById(8L)).thenReturn(Optional.of(actual));
        when(repository.save(any(Administrador.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdministradorService service = new AdministradorService(repository, encoder);

        Administrador patch = new Administrador();
        patch.setSede("  CC El Eden  ");

        Administrador saved = service.actualizar(8L, patch);

        assertEquals("CC El Eden", saved.getSede());
    }
}
