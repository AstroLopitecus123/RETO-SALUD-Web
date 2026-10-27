package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.dto.UsuarioLoginRequest;
import com.clinica.real.madrid.backend_citas.model.Cita;
import com.clinica.real.madrid.backend_citas.model.SolicitudEmpleo;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.model.Rol;
import com.clinica.real.madrid.backend_citas.security.JwtUtil;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.CitaService;
import com.clinica.real.madrid.backend_citas.service.SolicitudEmpleoService;
import com.clinica.real.madrid.backend_citas.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthBoundarySecurityTest {
    @BeforeEach
    void clearLimiters() {
        map(AuthController.class, "loginIntentosPorIp").clear();
        map(AuthController.class, "intentosFallidosMap").clear();
        map(AuthController.class, "cooldownRecuperacionMap").clear();
        map(AuthController.class, "recuperacionIntentosPorIp").clear();
        map(TeleconsultaController.class, "peticionesPorIp").clear();
        map(SolicitudEmpleoController.class, "peticionesPorIp").clear();
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Long>> map(Class<?> type, String field) {
        return (Map<String, List<Long>>) ReflectionTestUtils.getField(type, field);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "untrusted");
        return request;
    }

    @Test
    void loginAllowsThirtyAndBlocksThirtyFirstEvenWithSpoofedForwardedHeader() {
        AuthController controller = new AuthController();
        UsuarioLoginRequest body = new UsuarioLoginRequest();
        for (int i = 0; i < 30; i++) {
            assertEquals(401, controller.login(body, request()).getStatusCode().value());
        }
        assertEquals(429, controller.login(body, request()).getStatusCode().value());
        assertEquals(30, map(AuthController.class, "loginIntentosPorIp").get("192.0.2.1").size());
    }

    @Test
    void teleconsultaAllowsThirtyAndBlocksThirtyFirst() {
        TeleconsultaController controller = new TeleconsultaController();
        for (int i = 0; i < 30; i++) {
            assertEquals(200, controller.getConfig(null, null, request(), null).getStatusCode().value());
        }
        assertEquals(429, controller.getConfig(null, null, request(), null).getStatusCode().value());
    }

    @Test
    void solicitudesAllowFiveAndBlockSixth() {
        SolicitudEmpleoController controller = new SolicitudEmpleoController();
        SolicitudEmpleoService service = mock(SolicitudEmpleoService.class);
        ReflectionTestUtils.setField(controller, "solicitudService", service);
        SolicitudEmpleo body = new SolicitudEmpleo();
        for (int i = 0; i < 5; i++) {
            assertEquals(200, controller.enviarSolicitud(body, request()).getStatusCode().value());
        }
        assertEquals(429, controller.enviarSolicitud(body, request()).getStatusCode().value());
        verify(service, times(5)).enviarSolicitud(body);
    }

    @Test
    void allMapsHaveHardCapacityAndExpireIdleEntries() {
        for (Class<?> type : List.of(AuthController.class, TeleconsultaController.class, SolicitudEmpleoController.class)) {
            String field = type == AuthController.class ? "loginIntentosPorIp" : "peticionesPorIp";
            Map<String, List<Long>> entries = map(type, field);
            long now = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) entries.put("ip-" + i, new ArrayList<>(List.of(now)));
            Boolean admitted = type == AuthController.class
                    ? ReflectionTestUtils.invokeMethod(type, "admitir", entries, "new-ip", 30, 60_000L)
                    : ReflectionTestUtils.invokeMethod(type, "admitir", "new-ip");
            assertFalse(admitted);
            assertEquals(1000, entries.size());
            entries.replaceAll((key, value) -> new ArrayList<>(List.of(now - 900_000)));
            admitted = type == AuthController.class
                    ? ReflectionTestUtils.invokeMethod(type, "admitir", entries, "new-ip", 30, 60_000L)
                    : ReflectionTestUtils.invokeMethod(type, "admitir", "new-ip");
            assertTrue(admitted);
            assertEquals(1, entries.size());
        }
    }

    @Test
    void concurrentRequestsCannotExceedLimit() {
        long admitted = IntStream.range(0, 100).parallel().filter(i -> Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(TeleconsultaController.class, "admitir", "same-ip"))).count();
        assertEquals(30, admitted);
    }

    @Test
    void accountAttemptsAndRecoveryCooldownAreBoundedAndExpire() {
        Map<String, List<Long>> accounts = map(AuthController.class, "intentosFallidosMap");
        for (int i = 0; i < 5; i++) {
            assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AuthController.class, "admitir", accounts, "email", 5, 900_000L));
        }
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(AuthController.class, "admitir", accounts, "email", 5, 900_000L));
        Map<String, List<Long>> cooldown = map(AuthController.class, "cooldownRecuperacionMap");
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AuthController.class, "admitir", cooldown, "email", 1, 60_000L));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(AuthController.class, "admitir", cooldown, "email", 1, 60_000L));
        cooldown.put("email", new ArrayList<>(List.of(System.currentTimeMillis() - 60_000)));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AuthController.class, "admitir", cooldown, "email", 1, 60_000L));
    }

    @Test
    void recoveryLimitsIpBeforeRecordingEmailsAndKeepsTheSameResponse() {
        AuthController controller = new AuthController();
        UsuarioService service = mock(UsuarioService.class);
        ReflectionTestUtils.setField(controller, "usuarioService", service);
        Object response = null;
        for (int i = 0; i < 5; i++) {
            var result = controller.recuperar(Map.of("correo", "unknown-" + i + "@example.com"), request());
            assertEquals(200, result.getStatusCode().value());
            response = result.getBody();
        }
        for (int i = 0; i < 1000; i++) {
            var result = controller.recuperar(Map.of("correo", "blocked-" + i + "@example.com"), request());
            assertEquals(200, result.getStatusCode().value());
            assertEquals(response, result.getBody());
        }
        assertEquals(5, map(AuthController.class, "cooldownRecuperacionMap").size());
        verify(service, times(5)).enviarCorreoRecuperacion(anyString());
        map(AuthController.class, "recuperacionIntentosPorIp").put("192.0.2.1",
                new ArrayList<>(List.of(System.currentTimeMillis() - 60_000)));
        var result = controller.recuperar(Map.of("correo", "after-window@example.com"), request());
        assertEquals(response, result.getBody());
        verify(service).enviarCorreoRecuperacion("after-window@example.com");
    }

    @Test
    void fullRecoveryCooldownEvictsOldestWithoutBlockingNewAccountsOrExistingCooldowns() {
        AuthController controller = new AuthController();
        UsuarioService service = mock(UsuarioService.class);
        ReflectionTestUtils.setField(controller, "usuarioService", service);
        Map<String, List<Long>> cooldown = map(AuthController.class, "cooldownRecuperacionMap");
        long now = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            cooldown.put("unknown-" + i + "@example.com", new ArrayList<>(List.of(now)));
        }
        var result = controller.recuperar(Map.of("correo", "  PATIENT@example.com "), request());
        verify(service).enviarCorreoRecuperacion("patient@example.com");
        assertEquals(1000, cooldown.size());
        assertFalse(cooldown.containsKey("unknown-0@example.com"));
        assertTrue(cooldown.containsKey("unknown-1@example.com"));
        assertTrue(cooldown.containsKey("patient@example.com"));
        assertEquals(result.getBody(), controller.recuperar(Map.of("correo", "patient@example.com"), request()).getBody());
        assertEquals(result.getBody(), controller.recuperar(Map.of("correo", "unknown-1@example.com"), request()).getBody());
        verifyNoMoreInteractions(service);
        assertEquals(1000, cooldown.size());
        assertTrue(cooldown.containsKey("unknown-1@example.com"));
    }

    @Test
    void recoveryFailureHasSameResponseAsCooldownAndUnknownEmails() {
        AuthController controller = new AuthController();
        UsuarioService service = mock(UsuarioService.class);
        ReflectionTestUtils.setField(controller, "usuarioService", service);
        doThrow(new RuntimeException("mail unavailable")).when(service).enviarCorreoRecuperacion("patient@example.com");
        var unknown = controller.recuperar(Map.of("correo", "unknown@example.com"), request());
        var failed = controller.recuperar(Map.of("correo", "patient@example.com"), request());
        var cooldown = controller.recuperar(Map.of("correo", "patient@example.com"), request());
        assertEquals(200, failed.getStatusCode().value());
        assertEquals(unknown.getBody(), failed.getBody());
        assertEquals(unknown.getBody(), cooldown.getBody());
        verify(service, times(1)).enviarCorreoRecuperacion("patient@example.com");
    }

    @Test
    void fullAccountMapAllowsNewLoginWithoutEvictingActiveAccountLimits() {
        AuthController controller = new AuthController();
        UsuarioService service = mock(UsuarioService.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtUtil jwt = mock(JwtUtil.class);
        ReflectionTestUtils.setField(controller, "usuarioService", service);
        ReflectionTestUtils.setField(controller, "authenticationManager", manager);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwt);
        Map<String, List<Long>> accounts = map(AuthController.class, "intentosFallidosMap");
        long now = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            accounts.put("unknown-" + i + "@example.com", new ArrayList<>(List.of(now, now, now, now, now)));
        }
        Usuario usuario = new Usuario();
        usuario.setCorreo("patient@example.com");
        usuario.setRol(Rol.PACIENTE);
        when(service.login("patient@example.com", "password")).thenReturn(usuario);
        when(jwt.generateToken("patient@example.com")).thenReturn("jwt");
        UsuarioLoginRequest body = new UsuarioLoginRequest();
        body.setCorreo("patient@example.com");
        body.setContrasena("password");
        assertEquals(200, controller.login(body, request()).getStatusCode().value());
        assertEquals(1000, accounts.size());
        assertFalse(accounts.containsKey("patient@example.com"));
        body.setCorreo("unknown-0@example.com");
        assertEquals(401, controller.login(body, request()).getStatusCode().value());
        assertEquals(5, accounts.get("unknown-0@example.com").size());
        verify(manager, times(1)).authenticate(any());
        verify(service, times(1)).login(anyString(), anyString());
    }

    @Test
    void deepgramRequiresChannelAndAuthorizedPrincipal() {
        TeleconsultaController controller = new TeleconsultaController();
        ReflectionTestUtils.setField(controller, "deepgramApiKey", "secret-deepgram");
        assertFalse(((Map<?, ?>) controller.getConfig(null, null, request(), null).getBody()).containsKey("deepgramApiKey"));
        assertEquals(401, controller.getConfig("cita-1", null, request(), null).getStatusCode().value());
        var foreignPrincipal = new UsernamePasswordAuthenticationToken("not-a-user-principal", null, List.of());
        assertEquals(401, controller.getConfig("cita-1", null, request(), foreignPrincipal).getStatusCode().value());
        CitaService citas = mock(CitaService.class);
        ReflectionTestUtils.setField(controller, "citaService", citas);
        Usuario patient = new Usuario();
        patient.setId(1L);
        Cita cita = mock(Cita.class);
        when(cita.getPaciente()).thenReturn(patient);
        when(citas.obtenerPorId(1L)).thenReturn(cita);
        var participant = new UsernamePasswordAuthenticationToken(UsuarioPrincipal.fromUsuario(patient), null, List.of());
        assertEquals("secret-deepgram", ((Map<?, ?>) controller.getConfig("cita-1", null, request(), participant).getBody()).get("deepgramApiKey"));
        Usuario outsider = new Usuario();
        outsider.setId(2L);
        var unauthorized = new UsernamePasswordAuthenticationToken(UsuarioPrincipal.fromUsuario(outsider), null, List.of());
        assertEquals(403, controller.getConfig("cita-1", null, request(), unauthorized).getStatusCode().value());
    }
}
