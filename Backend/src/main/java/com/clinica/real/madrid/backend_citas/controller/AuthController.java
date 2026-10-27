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
import java.util.LinkedHashMap;
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

    private static final Map<String, List<Long>> intentosFallidosMap = new ConcurrentHashMap<>();
    private static final Map<String, List<Long>> cooldownRecuperacionMap = new LinkedHashMap<>();
    private static final Map<String, List<Long>> recuperacionIntentosPorIp = new ConcurrentHashMap<>();
    private static final Map<String, List<Long>> loginIntentosPorIp = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_POR_MINUTO = 30;
    private static final int MAX_RECUPERACION_POR_MINUTO = 5;

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
        // No confiar en X-Forwarded-For proporcionado directamente por el cliente.
        if (!admitir(loginIntentosPorIp, httpRequest.getRemoteAddr(), MAX_LOGIN_POR_MINUTO, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Demasiados intentos de inicio de sesion. Por favor espere 1 minuto."));
        }
        String correo = request.getCorreo() != null ? request.getCorreo().toLowerCase().trim() : "";
        if (correo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciales incorrectas o cuenta no disponible."));
        }

        if (!admitir(intentosFallidosMap, correo, 5, 900_000)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciales incorrectas o cuenta no disponible."));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getContrasena())
            );

            Usuario usuario = usuarioService.login(request.getCorreo(), request.getContrasena());

            synchronized (intentosFallidosMap) {
                intentosFallidosMap.remove(correo);
            }

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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciales incorrectas o cuenta no disponible."));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginGoogle(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        if (!admitir(loginIntentosPorIp, request.getRemoteAddr(), MAX_LOGIN_POR_MINUTO, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Demasiados intentos"));
        }
        try {
            String idToken = body.get("idToken");
            Usuario usuario = usuarioService.loginConGoogle(idToken, body.get("nonce"));
            String token = jwtUtil.generateToken(usuario.getCorreo());
            return ResponseEntity.ok(new UsuarioResponse(token, usuario));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No se pudo validar la autenticacion con Google"));
        }
    }

    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String> respuesta = Map.of("mensaje", "Si el correo está registrado, se han enviado las instrucciones para restablecer tu contraseña.");
        if (!admitir(recuperacionIntentosPorIp, request.getRemoteAddr(), MAX_RECUPERACION_POR_MINUTO, 60_000)) {
            return ResponseEntity.ok(respuesta);
        }
        String correo = body.get("correo");
        if (correo != null) {
            correo = correo.toLowerCase().trim();
            synchronized (cooldownRecuperacionMap) {
                // Solo el cooldown admite eviccion; nunca expulsar bloqueos de login activos.
                if (!cooldownRecuperacionMap.containsKey(correo) && cooldownRecuperacionMap.size() >= 1000) {
                    cooldownRecuperacionMap.remove(cooldownRecuperacionMap.keySet().iterator().next());
                }
                if (!admitir(cooldownRecuperacionMap, correo, 1, 60_000)) {
                    return ResponseEntity.ok(respuesta);
                }
            }
        }
        try {
            usuarioService.enviarCorreoRecuperacion(correo);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecer(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String nuevaContrasena = body.get("nuevaContrasena");
        usuarioService.restablecerContrasena(token, nuevaContrasena);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente."));
    }

    private static boolean admitir(Map<String, List<Long>> mapa, String clave, int limite, long ventana) {
        synchronized (mapa) {
            long ahora = System.currentTimeMillis();
            mapa.entrySet().removeIf(e -> e.getValue().isEmpty()
                    || ahora - e.getValue().get(e.getValue().size() - 1) >= ventana);
            List<Long> intentos = mapa.get(clave);
            if (intentos == null) {
                // Una cuenta nueva puede autenticar bajo el limite por IP sin expulsar bloqueos activos.
                // Los mapas por IP siguen fallando cerrado al alcanzar su capacidad.
                if (mapa.size() >= 1000) { return mapa == intentosFallidosMap; }
                intentos = new ArrayList<>();
                mapa.put(clave, intentos);
            }
            intentos.removeIf(ts -> ahora - ts >= ventana);
            if (intentos.size() >= limite) { return false; }
            intentos.add(ahora);
            return true;
        }
    }
}
