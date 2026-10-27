package com.clinica.real.madrid.backend_citas.security;

import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthSecurityTest {
    private static final String SECRET = "a".repeat(64);
    private JwtUtil jwt;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setCorreo("auth@example.com");
        UsuarioRepository repository = mock(UsuarioRepository.class);
        when(repository.findByCorreo(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "usuarioRepository", repository);
        ReflectionTestUtils.setField(jwt, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 60_000L);
        jwt.init();
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void legacyNullableVersionSurvivesUntilPasswordChanges() {
        ReflectionTestUtils.setField(usuario, "tokenVersion", null);
        String legacy = Jwts.builder().setSubject(usuario.getCorreo())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
        String current = jwt.generateToken(usuario.getCorreo());
        assertTrue(jwt.validateToken(legacy, UsuarioPrincipal.fromUsuario(usuario)));
        assertTrue(jwt.validateToken(current, UsuarioPrincipal.fromUsuario(usuario)));
        usuario.revocarTokens();
        assertFalse(jwt.validateToken(legacy, UsuarioPrincipal.fromUsuario(usuario)));
        assertFalse(jwt.validateToken(current, UsuarioPrincipal.fromUsuario(usuario)));
        assertTrue(jwt.validateToken(jwt.generateToken(usuario.getCorreo()), UsuarioPrincipal.fromUsuario(usuario)));
        assertFalse(jwt.validateToken(current + "corrupt", UsuarioPrincipal.fromUsuario(usuario)));
    }

    @Test
    void filterReloadsVersionAndRejectsDeletedUsers() throws Exception {
        JwtFilter filter = new JwtFilter();
        UserDetailsService users = mock(UserDetailsService.class);
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "userDetailsService", users);
        String token = jwt.generateToken(usuario.getCorreo());
        when(users.loadUserByUsername(usuario.getCorreo())).thenAnswer(invocation -> UsuarioPrincipal.fromUsuario(usuario));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/usuarios");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        usuario.revocarTokens();
        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        when(users.loadUserByUsername(usuario.getCorreo())).thenThrow(new UsernameNotFoundException("deleted"));
        assertDoesNotThrow(() -> filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {}));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
