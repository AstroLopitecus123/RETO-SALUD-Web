package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.Historial;
import com.clinica.real.madrid.backend_citas.service.HistorialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historiales")
public class HistorialController {

	private final HistorialService historialService;

    public HistorialController(HistorialService historialService) {
        this.historialService = historialService;
    }

    @PostMapping("/{citaId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public ResponseEntity<Historial> registrarHistorial(
            @PathVariable Long citaId,
            @RequestBody Historial historial) {
        return ResponseEntity.ok(historialService.registrarHistorial(citaId, historial));
    }

    @GetMapping("/cita/{citaId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or hasRole('PACIENTE')")
    public ResponseEntity<Historial> obtenerPorCita(@PathVariable Long citaId) {
        return historialService.obtenerPorCita(citaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null)); 
    }

    @GetMapping("/paciente/{pacienteId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or #pacienteId == authentication.principal.id")
    public List<Historial> obtenerPorPaciente(@PathVariable Long pacienteId) {
        return historialService.listarPorPaciente(pacienteId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION') or hasRole('PACIENTE')")
    public ResponseEntity<Historial> obtenerPorId(@PathVariable Long id) {
        return historialService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION')")
    public List<Historial> listarTodos() {
        return historialService.listarTodos();
    }

    @GetMapping("/todas-con-citas")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO') or hasRole('RECEPCION')")
    public List<Historial> listarTodasConCitas() {
        return historialService.listarTodasConCitas();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public ResponseEntity<Historial> actualizarHistorial(
            @PathVariable Long id,
            @RequestBody Historial historial) {
        return ResponseEntity.ok(historialService.actualizarHistorial(id, historial));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public ResponseEntity<Void> eliminarHistorial(@PathVariable Long id) {
        historialService.eliminarHistorial(id);
        return ResponseEntity.noContent().build();
    }
}
