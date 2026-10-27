package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.Notificacion;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.service.NotificacionService;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<Notificacion>> getMisNotificaciones(Authentication auth) {
        String correo = auth.getName();
        Usuario r = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Notificacion> notificaciones = notificacionService.obtenerMisNotificaciones(
            r.getId(), 
            r.getRol().name(), 
            r.getFechaRegistro()
        );
        return ResponseEntity.ok(notificaciones);
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<Map<String, Long>> contarNoLeidas(Authentication auth) {
        String correo = auth.getName();
        Usuario r = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long conteo = notificacionService.contarNoLeidas(
            r.getId(), 
            r.getRol().name(), 
            r.getFechaRegistro()
        );
        return ResponseEntity.ok(Map.of("cantidad", conteo));
    }

    @PutMapping("/{id}/leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> marcarComoLeida(@PathVariable Long id, Authentication auth) {
        String correo = auth.getName();
        Usuario r = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Notificacion notificacion;
        try {
            notificacion = notificacionService.marcarComoLeida(id, r.getId(), r.getRol().name(), r.getFechaRegistro());
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        if (notificacion == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(notificacion);
    }
}
