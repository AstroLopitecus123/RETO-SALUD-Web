package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.dto.UsuarioRegistroRequest;
import com.clinica.real.madrid.backend_citas.dto.UsuarioLoginRequest;
import com.clinica.real.madrid.backend_citas.dto.UsuarioResponse;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.security.JwtUtil;
import com.clinica.real.madrid.backend_citas.service.UsuarioService;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Map<String, Integer> intentosFallidosMap = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> bloqueoExpiraMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> cooldownRecuperacionMap = new ConcurrentHashMap<>();
    private static final Map<String, List<Long>> loginIntentosPorIp = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_POR_MINUTO = 30;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private com.clinica.real.madrid.backend_citas.service.MedicoService medicoService;

    @Autowired
    private com.clinica.real.madrid.backend_citas.service.RecaptchaService recaptchaService;

    @PostMapping("/registro")
    @SuppressWarnings("java:S4507")
    public ResponseEntity<?> registro(@RequestBody UsuarioRegistroRequest request) {
        try {
            if (!recaptchaService.validarToken(request.getRecaptchaToken())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Verificación de reCAPTCHA fallida. Por favor intenta nuevamente."));
            }

            Usuario usuario = usuarioService.registrarUsuario(request);

            String token = jwtUtil.generateToken(usuario.getCorreo());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UsuarioResponse(token, usuario));
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al registrar: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    @SuppressWarnings("java:S6098")
    public ResponseEntity<?> login(@RequestBody UsuarioLoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        long ahora = System.currentTimeMillis();
        boolean rateLimited = loginIntentosPorIp.compute(ip, (k, lista) -> {
            if (lista == null) { lista = new ArrayList<>(); }
            lista.removeIf(ts -> (ahora - ts) > 60_000);
            if (lista.size() >= MAX_LOGIN_POR_MINUTO) { return lista; }
            lista.add(ahora);
            return lista;
        }).size() > MAX_LOGIN_POR_MINUTO;
        if (rateLimited) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Demasiados intentos de inicio de sesion. Por favor espere 1 minuto."));
        }
        String correo = request.getCorreo() != null ? request.getCorreo().toLowerCase().trim() : "";
        if (correo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Correo o contraseña incorrectos."));
        }

        LocalDateTime expira = bloqueoExpiraMap.get(correo);
        if (expira != null) {
            if (LocalDateTime.now(java.time.ZoneId.systemDefault()).isBefore(expira)) {
                long segundosRestantes = ChronoUnit.SECONDS.between(LocalDateTime.now(java.time.ZoneId.systemDefault()), expira);
                long minutos = segundosRestantes / 60;
                long segundos = segundosRestantes % 60;
                String tiempoRestante = minutos > 0
                        ? minutos + " min " + segundos + " seg"
                        : segundos + " seg";
                System.err.println("INCIDENTE: Intento de inicio de sesión en cuenta bloqueada: " + correo);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Cuenta bloqueada. Reintente en " + tiempoRestante + "."));
            } else {
                bloqueoExpiraMap.remove(correo);
                intentosFallidosMap.remove(correo);
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getContrasena())
            );

            Usuario usuario = usuarioService.login(request.getCorreo(), request.getContrasena());

            intentosFallidosMap.remove(correo);

            System.out.println("ROL BACKEND: " + usuario.getRol());

            String token = jwtUtil.generateToken(usuario.getCorreo());

            UsuarioResponse response = new UsuarioResponse(token, usuario);
            if ("MEDICO".equals(usuario.getRol().name())) {
                com.clinica.real.madrid.backend_citas.model.Medico medico = medicoService.obtenerPorUsuarioId(usuario.getId());
                if (medico != null) {
                    response.setMedicoId(medico.getId());
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            boolean existe = usuarioService.existePorCorreo(correo);
            if (existe) {
                int intentos = intentosFallidosMap.getOrDefault(correo, 0) + 1;
                intentosFallidosMap.put(correo, intentos);
                System.out.println("Intentos fallidos para " + correo + ": " + intentos);

                if (intentos >= 5) {
                    bloqueoExpiraMap.put(correo, LocalDateTime.now(java.time.ZoneId.systemDefault()).plusMinutes(3));
                    System.err.println("INCIDENTE: Cuenta bloqueada temporalmente por exceso de intentos fallidos (5): " + correo);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Cuenta bloqueada por 5 intentos fallidos. Reintente en 3 min 0 seg."));
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Correo o contraseña incorrectos."));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginGoogle(@RequestBody Map<String, String> body) {
        try {
            String idToken = body.get("idToken");
            Usuario usuario = usuarioService.loginConGoogle(idToken);
            String token = jwtUtil.generateToken(usuario.getCorreo());
            return ResponseEntity.ok(new UsuarioResponse(token, usuario));
        } catch (Exception e) {
            System.err.println(e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error en Google Auth: " + e.getMessage()));
        }
    }

    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody Map<String, String> body) {
        String correo = body.get("correo");
        if (correo != null) {
            correo = correo.toLowerCase().trim();
            long ahora = System.currentTimeMillis();
            Long ultimoEnvio = cooldownRecuperacionMap.get(correo);
            if (ultimoEnvio != null && (ahora - ultimoEnvio < 60_000)) {
                return ResponseEntity.ok(Map.of("mensaje", "Ya enviamos un correo recientemente. Por favor espera 1 minuto antes de solicitar otro."));
            }
            cooldownRecuperacionMap.put(correo, ahora);
        }
        usuarioService.enviarCorreoRecuperacion(correo);
        return ResponseEntity.ok(Map.of("mensaje", "Correo de recuperación enviado correctamente."));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String nuevaContrasena = body.get("nuevaContrasena");
        usuarioService.restablecerContrasena(token, nuevaContrasena);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente."));
    }
}
