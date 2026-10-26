package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.Disponibilidad;
import com.clinica.real.madrid.backend_citas.model.Medico;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.DisponibilidadService;
import com.clinica.real.madrid.backend_citas.service.MedicoService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disponibilidades")
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;
    private final MedicoService medicoService;

    public DisponibilidadController(DisponibilidadService disponibilidadService, MedicoService medicoService) {
        this.disponibilidadService = disponibilidadService;
        this.medicoService = medicoService;
    }

    private void validarPermisoMedico(Long medicoId, Authentication auth) {
        if (auth == null) return;
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        if (auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            Medico medico = medicoService.obtenerPorId(medicoId);
            if (medico == null || medico.getUsuario() == null || !medico.getUsuario().getId().equals(principal.getId())) {
                throw new AccessDeniedException("No tienes permiso para modificar los horarios de otro médico");
            }
        }
    }

    @GetMapping("/medico/{id}")
    public List<Disponibilidad> listarPorMedico(@PathVariable("id") Long medicoId) {
        return disponibilidadService.listarPorMedico(medicoId);
    }

    @GetMapping("/medico/{id}/rango")
    public List<Disponibilidad> listarPorRango(
            @PathVariable("id") Long medicoId,
            @RequestParam("inicio") String inicio,
            @RequestParam("fin") String fin) {
        return disponibilidadService.listarPorRango(medicoId, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    @PostMapping("/medico/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public List<Disponibilidad> guardarDisponibilidades(
            @PathVariable("id") Long medicoId,
            @RequestBody List<Disponibilidad> disponibilidades,
            Authentication auth) {
        validarPermisoMedico(medicoId, auth);
        return disponibilidadService.guardarDisponibilidades(medicoId, disponibilidades);
    }

    @DeleteMapping("/medico/{id}/rango")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDICO')")
    public void eliminarPorRango(
            @PathVariable("id") Long medicoId,
            @RequestParam("inicio") String inicio,
            @RequestParam("fin") String fin,
            Authentication auth) {
        validarPermisoMedico(medicoId, auth);
        disponibilidadService.eliminarPorRango(medicoId, LocalDate.parse(inicio), LocalDate.parse(fin));
    }
}
