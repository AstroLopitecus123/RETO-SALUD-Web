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

    public Notificacion marcarComoLeida(Long id) {
        return marcarComoLeida(id, null);
    }

    public Notificacion marcarComoLeida(Long id, Long usuarioId) {
        try {
            Notificacion notificacion = notificacionRepository.findById(id).orElse(null);
            if (notificacion == null) {
                return null;
            }
            if (usuarioId != null && notificacion.getUsuarioDestino() != null && !notificacion.getUsuarioDestino().getId().equals(usuarioId)) {
                throw new com.clinica.real.madrid.backend_citas.exception.BadRequestException("No puedes modificar notificaciones ajenas");
            }
            notificacion.setLeida(true);
            return notificacionRepository.save(notificacion);
        } catch (com.clinica.real.madrid.backend_citas.exception.BadRequestException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error al marcar notificación como leída: " + e.getMessage());
            return null;
        }
    }
}
