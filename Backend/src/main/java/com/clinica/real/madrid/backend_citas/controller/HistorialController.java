package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.Historial;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.HistorialService;
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

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/historiales")
public class HistorialController {

    private final HistorialService historialService;

    public HistorialController(HistorialService historialService) {
        this.historialService = historialService;
    }

    private boolean esStaff(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MEDICO") || a.getAuthority().equals("ROLE_RECEPCION")
        );
    }

    @PostMapping("/{citaId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public ResponseEntity<Historial> registrarHistorial(
            @PathVariable Long citaId,
            @RequestBody Historial historial,
            Authentication auth) {
        boolean esAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Long usuarioId = (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) ? principal.getId() : null;
        return ResponseEntity.ok(historialService.registrarHistorial(citaId, historial, usuarioId, esAdmin));
    }

    @GetMapping("/cita/{citaId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or hasRole('PACIENTE')")
    public ResponseEntity<?> obtenerPorCita(@PathVariable Long citaId, Authentication auth) {
        Historial historial = historialService.obtenerPorCita(citaId).orElse(null);
        if (historial == null) {
            return ResponseEntity.ok(null);
        }
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            boolean esAdminORecepcion = auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"));
            if (!esAdminORecepcion) {
                boolean esMedico = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"));
                if (esMedico) {
                    if (historial.getCita() == null || historial.getCita().getMedico() == null
                            || historial.getCita().getMedico().getUsuario() == null
                            || !historial.getCita().getMedico().getUsuario().getId().equals(principal.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes ver el historial clinico de otra consulta"));
                    }
                } else {
                    if (historial.getCita() == null || historial.getCita().getPaciente() == null
                            || !historial.getCita().getPaciente().getId().equals(principal.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes ver el historial clinico de otro paciente"));
                    }
                }
            }
        }
        return ResponseEntity.ok(historial); 
    }

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or hasRole('PACIENTE')")
    public ResponseEntity<?> obtenerPorPaciente(@PathVariable Long pacienteId, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            boolean esAdminORecepcion = auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"));
            if (!esAdminORecepcion) {
                boolean esPaciente = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PACIENTE"));
                if (esPaciente && !pacienteId.equals(principal.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes ver el historial de otro paciente"));
                }
            }
        }
        List<Historial> historiales = historialService.listarPorPaciente(pacienteId);
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"))
                && !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            if (auth.getPrincipal() instanceof UsuarioPrincipal principal) {
                historiales = historiales.stream().filter(h -> h.getCita() != null && h.getCita().getMedico() != null
                        && h.getCita().getMedico().getUsuario() != null
                        && h.getCita().getMedico().getUsuario().getId().equals(principal.getId())).collect(Collectors.toList());
            }
        }
        return ResponseEntity.ok(historiales);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or hasRole('PACIENTE')")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id, Authentication auth) {
        Historial historial = historialService.obtenerPorId(id).orElse(null);
        if (historial == null) {
            return ResponseEntity.notFound().build();
        }
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            boolean esAdminORecepcion = auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"));
            if (!esAdminORecepcion) {
                boolean esMedico = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"));
                if (esMedico) {
                    if (historial.getCita() == null || historial.getCita().getMedico() == null
                            || historial.getCita().getMedico().getUsuario() == null
                            || !historial.getCita().getMedico().getUsuario().getId().equals(principal.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes ver el historial clinico de otra consulta"));
                    }
                } else {
                    if (historial.getCita() == null || historial.getCita().getPaciente() == null
                            || !historial.getCita().getPaciente().getId().equals(principal.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes ver el historial clinico de otro paciente"));
                    }
                }
            }
        }
        return ResponseEntity.ok(historial);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION')")
    public List<Historial> listarTodos(Authentication auth) {
        List<Historial> todos = historialService.listarTodos();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"))
                && !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"))) {
            if (auth.getPrincipal() instanceof UsuarioPrincipal principal) {
                return todos.stream().filter(h -> h.getCita() != null && h.getCita().getMedico() != null
                        && h.getCita().getMedico().getUsuario() != null
                        && h.getCita().getMedico().getUsuario().getId().equals(principal.getId())).collect(Collectors.toList());
            }
        }
        return todos;
    }

    @GetMapping("/todas-con-citas")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION')")
    public List<Historial> listarTodasConCitas(Authentication auth) {
        List<Historial> todas = historialService.listarTodasConCitas();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MEDICO"))
                && !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"))) {
            if (auth.getPrincipal() instanceof UsuarioPrincipal principal) {
                return todas.stream().filter(h -> h.getCita() != null && h.getCita().getMedico() != null
                        && h.getCita().getMedico().getUsuario() != null
                        && h.getCita().getMedico().getUsuario().getId().equals(principal.getId())).collect(Collectors.toList());
            }
        }
        return todas;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public ResponseEntity<?> actualizarHistorial(
            @PathVariable Long id,
            @RequestBody Historial historial,
            Authentication auth) {
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            if (auth.getPrincipal() instanceof UsuarioPrincipal principal) {
                Historial existente = historialService.obtenerPorId(id).orElse(null);
                if (existente != null && existente.getCita() != null && existente.getCita().getMedico() != null
                        && existente.getCita().getMedico().getUsuario() != null) {
                    if (!existente.getCita().getMedico().getUsuario().getId().equals(principal.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permiso para modificar el historial clinico de otra consulta"));
                    }
                }
            }
        }
        return ResponseEntity.ok(historialService.actualizarHistorial(id, historial));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarHistorial(@PathVariable Long id) {
        historialService.eliminarHistorial(id);
        return ResponseEntity.noContent().build();
    }
}
