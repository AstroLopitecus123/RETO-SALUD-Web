package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.Cita;
import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.model.Usuario;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.CitaService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    @Autowired
    private CitaService citaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private boolean esStaff(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION")
        );
    }

    private boolean esMedicoPropio(Long id, Authentication auth) {
        if (esStaff(auth)) return true;
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) return false;
        Cita cita = citaService.obtenerPorId(id);
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"))
                && cita.getMedico() != null && cita.getMedico().getUsuario() != null
                && principal.getId().equals(cita.getMedico().getUsuario().getId());
    }

    private boolean esPacientePropio(Cita cita, Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal
                && cita.getPaciente() != null && principal.getId().equals(cita.getPaciente().getId());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(citaService.listar());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO') or hasRole('PACIENTE')")
    public ResponseEntity<?> obtener(@PathVariable Long id, Authentication auth) {
        Cita cita = citaService.obtenerPorId(id);
        if (cita == null) {
            return ResponseEntity.notFound().build();
        }
        if (!esStaff(auth)) {
            if (!esPacientePropio(cita, auth) && !esMedicoPropio(id, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acceso denegado a esta cita"));
            }
        }
        return ResponseEntity.ok(cita);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        citaService.eliminar(id);
        return ResponseEntity.ok("Cita eliminada correctamente");
    }

    @DeleteMapping("/{id}/abandonar")
    @PreAuthorize("hasRole('PACIENTE') or hasRole('ADMIN')")
    public ResponseEntity<?> abandonarCheckout(@PathVariable Long id, Authentication auth) {
        try {
            Cita cita = citaService.obtenerPorId(id);
            if (!esStaff(auth)) {
                if (!esPacientePropio(cita, auth)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes abandonar una cita ajena"));
                }
            }
            citaService.abandonarCheckout(id);
            return ResponseEntity.ok(Map.of("mensaje", "Reserva abandonada y horario liberado"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/usuario/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO') or #id == authentication.principal.id")
    public ResponseEntity<?> listarCitasPorUsuario(@PathVariable Long id, Authentication auth) {
        if (!esStaff(auth)) {
            if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"))) {
                return ResponseEntity.ok(citaService.obtenerCitasPorPacienteYMedico(id, principal.getId()));
            }
            if (!id.equals(principal.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Cita> citas = citaService.obtenerCitasPorPaciente(id);
        return ResponseEntity.ok(citas);
    }

    @GetMapping("/medico/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO')")
    public ResponseEntity<?> listarCitasPorMedico(@PathVariable Long id, Authentication auth) {
        if (!esStaff(auth) && (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)
                || !citaService.esMedicoPropio(id, principal.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Cita> citas = citaService.obtenerCitasPorMedico(id);
        return ResponseEntity.ok(citas);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Cita crear(@RequestBody Cita cita, Authentication auth) {
        if (!esStaff(auth) && (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal))) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario autenticado requerido");
        }
        if (!esStaff(auth) && auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            Usuario paciente = usuarioRepository.findById(principal.getId())
                    .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
            cita.setPaciente(paciente);
        }
        return citaService.crearCita(cita);
    }

    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> confirmarCita(@PathVariable Long id) {
        try {
            citaService.confirmarCita(id);
            return ResponseEntity.ok(Map.of("mensaje", "Cita confirmada correctamente"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al confirmar la cita");
        }
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> cancelarCita(@PathVariable Long id) {
        try {
            citaService.cancelarCita(id);
            return ResponseEntity.ok("Cita cancelada correctamente");
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al cancelar la cita");
        }
    }

    @PutMapping("/{id}/reprogramar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> reprogramarCita(@PathVariable Long id, @RequestBody Cita nuevaCita) {
        try {
            citaService.reprogramarCita(id, nuevaCita.getFecha());
            return ResponseEntity.ok(Map.of("mensaje", "Cita reprogramada correctamente"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al reprogramar la cita");
        }
    }

    @PutMapping("/{id}/solicitar-reprogramar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> solicitarReprogramar(@PathVariable Long id, @RequestBody Cita nuevaCita, Authentication auth) {
        try {
            Cita cita = citaService.obtenerPorId(id);
            if (!esStaff(auth)) {
                if (!esPacientePropio(cita, auth)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes reprogramar una cita ajena"));
                }
            }
            citaService.solicitarReprogramacion(id, nuevaCita.getFecha(), nuevaCita.getMotivo());
            return ResponseEntity.ok(Map.of("mensaje", "Solicitud de reprogramación enviada"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al solicitar reprogramación: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/confirmar-reprogramar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO')")
    public ResponseEntity<?> confirmarReprogramar(@PathVariable Long id, Authentication auth) {
        if (!esMedicoPropio(id, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            citaService.confirmarReprogramacion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Reprogramación confirmada correctamente"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/rechazar-reprogramar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO')")
    public ResponseEntity<?> rechazarReprogramar(@PathVariable Long id, Authentication auth) {
        if (!esMedicoPropio(id, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            citaService.rechazarReprogramacion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Solicitud de reprogramación rechazada correctamente"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/solicitar-cancelar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> solicitarCancelar(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        try {
            Cita cita = citaService.obtenerPorId(id);
            if (!esStaff(auth)) {
                if (!esPacientePropio(cita, auth)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes cancelar una cita ajena"));
                }
            }
            String motivo = body.getOrDefault("motivo", "Sin motivo especificado");
            citaService.solicitarCancelacion(id, motivo);
            return ResponseEntity.ok(Map.of("mensaje", "Solicitud de cancelación enviada"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al solicitar cancelación: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/confirmar-cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> confirmarCancelar(@PathVariable Long id) {
        try {
            citaService.confirmarCancelacion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Cancelación aprobada y reembolso procesado"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/rechazar-cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<?> rechazarCancelar(@PathVariable Long id) {
        try {
            citaService.rechazarCancelacion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Solicitud de cancelación rechazada correctamente"));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

}
