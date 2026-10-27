package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.model.Notificacion;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.repository.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    public Notificacion crearNotificacionParaRol(String titulo, String mensaje, String rolDestino, Long referenciaId) {
        Notificacion notificacion = new Notificacion(titulo, mensaje, rolDestino, null, referenciaId);
        return notificacionRepository.save(notificacion);
    }

    public Notificacion crearNotificacionParaUsuario(String titulo, String mensaje, Usuario usuarioDestino, Long referenciaId) {
        Notificacion notificacion = new Notificacion(titulo, mensaje, null, usuarioDestino, referenciaId);
        return notificacionRepository.save(notificacion);
    }

    public List<Notificacion> obtenerMisNotificaciones(Long usuarioId, String rol, LocalDateTime fechaRegistro) {
        return notificacionRepository.findByUsuarioOrRolDestino(usuarioId, rol, fechaRegistro);
    }

    public long contarNoLeidas(Long usuarioId, String rol, LocalDateTime fechaRegistro) {
        return notificacionRepository.countUnreadByUsuarioOrRolDestino(usuarioId, rol, fechaRegistro);
    }

    public Notificacion marcarComoLeida(Long id, Long usuarioId, String rol, LocalDateTime fechaRegistro) {
        Notificacion notificacion = notificacionRepository.findById(id).orElse(null);
        if (notificacion == null) {
            return null;
        }
        boolean destinatario = usuarioId != null && (notificacion.getUsuarioDestino() != null
                ? usuarioId.equals(notificacion.getUsuarioDestino().getId())
                : rol != null && rol.equals(notificacion.getRolDestino()));
        if (!destinatario || fechaRegistro == null || notificacion.getFechaCreacion() == null
                || notificacion.getFechaCreacion().isBefore(fechaRegistro)) {
            throw new org.springframework.security.access.AccessDeniedException("No puedes modificar notificaciones ajenas");
        }
        notificacion.setLeida(true);
        return notificacionRepository.save(notificacion);
    }
}
