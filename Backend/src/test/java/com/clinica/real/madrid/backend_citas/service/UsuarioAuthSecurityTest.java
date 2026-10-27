package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioAuthSecurityTest {
    private UsuarioService service;
    private UsuarioRepository repository;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(mock(JavaMailSender.class));
        repository = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(service, "usuarioRepository", repository);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        usuario = new Usuario();
        usuario.setContrasena("hash");
        usuario.setTokenRecuperacion("reset");
        usuario.setTokenExpira(LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(usuario, "tokenVersion", null);
        when(repository.findById(1L)).thenReturn(Optional.of(usuario));
        when(repository.findByTokenRecuperacion("reset")).thenReturn(Optional.of(usuario));
        when(encoder.matches("actual", "hash")).thenReturn(true);
        when(encoder.encode("NuevaClave123")).thenReturn("new-hash");
    }

    @Test
    void passwordChangeRevokesSessionsAndPendingReset() {
        service.cambiarPassword(1L, "actual", "NuevaClave123");
        assertEquals(1L, usuario.getTokenVersion());
        assertEquals("new-hash", usuario.getContrasena());
        assertNull(usuario.getTokenRecuperacion());
        assertNull(usuario.getTokenExpira());
        verify(repository).save(usuario);
    }

    @Test
    void resetRevokesSessionsAndConsumesReset() {
        service.restablecerContrasena("reset", "NuevaClave123");
        assertEquals(1L, usuario.getTokenVersion());
        assertNull(usuario.getTokenRecuperacion());
        assertNull(usuario.getTokenExpira());
        verify(repository).save(usuario);
    }

    @Test
    void invalidPasswordOrExpiredResetDoesNotRevoke() {
        assertThrows(BadRequestException.class, () -> service.cambiarPassword(1L, "wrong", "NuevaClave123"));
        usuario.setTokenExpira(LocalDateTime.now().minusSeconds(1));
        assertThrows(RuntimeException.class, () -> service.restablecerContrasena("reset", "NuevaClave123"));
        assertThrows(BadRequestException.class, () -> service.restablecerContrasena(null, "NuevaClave123"));
        assertEquals(0L, usuario.getTokenVersion());
        verify(repository, never()).save(any());
    }

    @Test
    void googleRequiresMatchingNonceAndVerifiedEmail() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmailVerified(true);
        payload.set("nonce", "expected");
        assertDoesNotThrow(() -> service.validarNonceGoogle(payload, "expected"));
        assertThrows(BadRequestException.class, () -> service.validarNonceGoogle(payload, null));
        assertThrows(BadRequestException.class, () -> service.validarNonceGoogle(payload, "other"));
        payload.set("nonce", null);
        assertThrows(BadRequestException.class, () -> service.validarNonceGoogle(payload, "expected"));
        payload.set("nonce", "expected");
        payload.setEmailVerified(false);
        assertThrows(BadRequestException.class, () -> service.validarNonceGoogle(payload, "expected"));
        assertThrows(BadRequestException.class, () -> service.loginConGoogle("token", null));
        verifyNoInteractions(repository);
    }
}
