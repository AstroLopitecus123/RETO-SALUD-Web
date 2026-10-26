package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.exception.ResourceNotFoundException;
import com.clinica.real.madrid.backend_citas.model.SolicitudEmpleo;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.model.Rol;
import com.clinica.real.madrid.backend_citas.model.EstadoUsuario;
import com.clinica.real.madrid.backend_citas.repository.SolicitudEmpleoRepository;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SolicitudEmpleoService {

    @Autowired
    private SolicitudEmpleoRepository solicitudRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public SolicitudEmpleo enviarSolicitud(SolicitudEmpleo solicitud) {
        if (solicitud.getCorreo() == null || solicitud.getCorreo().isBlank()) {
            throw new BadRequestException("El correo electrónico es obligatorio.");
        }
        if (usuarioRepository.existsByCorreo(solicitud.getCorreo())) {
            throw new BadRequestException("Ya existe un usuario con ese correo electrónico.");
        }
        if (solicitudRepository.existsByCorreoAndEstado(solicitud.getCorreo(), SolicitudEmpleo.EstadoSolicitud.PENDIENTE)) {
            throw new BadRequestException("Ya cuentas con una postulación pendiente de revisión.");
        }
        if (solicitud.getDni() != null && !solicitud.getDni().isBlank() &&
            solicitudRepository.existsByDniAndEstado(solicitud.getDni(), SolicitudEmpleo.EstadoSolicitud.PENDIENTE)) {
            throw new BadRequestException("Ya existe una postulación pendiente con este número de DNI.");
        }
        solicitud.setId(null);
        solicitud.setEstado(SolicitudEmpleo.EstadoSolicitud.PENDIENTE);
        return solicitudRepository.save(solicitud);
    }

    public List<SolicitudEmpleo> listarTodas() {
        return solicitudRepository.findAll();
    }

    @Transactional
    public SolicitudEmpleo procesarSolicitud(Long id, SolicitudEmpleo.EstadoSolicitud nuevoEstado) {
        SolicitudEmpleo solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));

        if (solicitud.getEstado() != SolicitudEmpleo.EstadoSolicitud.PENDIENTE) {
            throw new BadRequestException("Esta solicitud ya ha sido procesada.");
        }

        if (nuevoEstado == SolicitudEmpleo.EstadoSolicitud.APROBADA) {
            crearUsuarioDesdeSolicitud(solicitud);
        }

        solicitud.setEstado(nuevoEstado);
        return solicitudRepository.save(solicitud);
    }

    private void crearUsuarioDesdeSolicitud(SolicitudEmpleo solicitud) {
        Usuario usuario = new Usuario();
        usuario.setNombre(solicitud.getNombre());
        usuario.setApellido(solicitud.getApellido());
        usuario.setCorreo(solicitud.getCorreo());
        usuario.setDni(solicitud.getDni());
        usuario.setTelefono(solicitud.getTelefono());

        String tempPass = UUID.randomUUID().toString().substring(0, 8);
        usuario.setContrasena(passwordEncoder.encode(tempPass));

        usuario.setEstado(EstadoUsuario.ACTIVADO);

        usuario = usuarioRepository.save(usuario);

        Rol rolDestino = solicitud.getPuesto() == SolicitudEmpleo.Puesto.MEDICO ? Rol.MEDICO : Rol.RECEPCION;
        usuarioService.actualizarRol(usuario.getId(), rolDestino.name());
    }
}
