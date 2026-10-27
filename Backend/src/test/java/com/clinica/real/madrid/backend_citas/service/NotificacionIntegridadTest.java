package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.model.Notificacion;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionIntegridadTest {
    @Mock NotificacionRepository repository;
    @InjectMocks NotificacionService service;
    private final LocalDateTime registro = LocalDateTime.now().minusDays(1);

    @Test
    void pacienteNoMarcaNotificacionDeStaff() {
        Notificacion n = new Notificacion("titulo", "mensaje", "ADMIN", null, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(n));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoLeida(1L, 2L, "PACIENTE", registro));
        verify(repository, never()).save(any());
    }

    @Test
    void rolCorrectoPuedeMarcarSuAviso() {
        Notificacion n = new Notificacion("titulo", "mensaje", "RECEPCION", null, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(n));
        when(repository.save(n)).thenReturn(n);
        assertSame(n, service.marcarComoLeida(1L, 2L, "RECEPCION", registro));
        verify(repository).save(n);
    }

    @Test
    void rolNoPermiteModificarDestinatarioAjeno() {
        Usuario otro = new Usuario();
        otro.setId(99L);
        Notificacion n = new Notificacion("titulo", "mensaje", "ADMIN", otro, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(n));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoLeida(1L, 2L, "ADMIN", registro));
        verify(repository, never()).save(any());
    }

    @Test
    void avisosAnterioresAlRegistroNoSePuedenMarcar() {
        Notificacion n = new Notificacion("titulo", "mensaje", "ADMIN", null, 1L);
        n.setFechaCreacion(registro.minusDays(1));
        when(repository.findById(1L)).thenReturn(Optional.of(n));
        assertThrows(AccessDeniedException.class, () -> service.marcarComoLeida(1L, 2L, "ADMIN", registro));
    }
}
