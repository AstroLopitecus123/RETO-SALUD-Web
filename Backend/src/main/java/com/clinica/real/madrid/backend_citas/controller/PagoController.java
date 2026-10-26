package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.dto.*;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.PagoService;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")

public class PagoController {

    @Autowired
    private PagoService pagoService;

    private boolean esStaff(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION") || a.getAuthority().equals("ROLE_MEDICO")
        );
    }

    @PostMapping("/efectivo")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<PagoResponse> pagarEfectivo(@RequestBody PagoEfectivoRequest request) {
        PagoResponse response = pagoService.pagarEfectivo(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/promesa-efectivo")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<PagoResponse> prometerEfectivo(@RequestBody PagoEfectivoRequest request, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            request.setUsuarioId(principal.getId());
        }
        PagoResponse response = pagoService.prometerPagoEfectivo(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/completar/{pagoId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<PagoResponse> completarPagoFisico(@PathVariable Long pagoId) {
        PagoResponse response = pagoService.completarPago(pagoId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/crear-intent")
    public ResponseEntity<PaymentIntentResponse> crearPaymentIntent(@RequestBody PaymentIntentRequest request) throws StripeException {
        PaymentIntentResponse response = pagoService.crearPaymentIntent(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tarjeta")
    public ResponseEntity<PagoResponse> pagarTarjeta(@RequestBody PagoTarjetaRequest request) {
        PagoResponse response = pagoService.pagarTarjeta(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cita/{citaId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO') or hasRole('PACIENTE')")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorCita(@PathVariable Long citaId, Authentication auth) {
        List<PagoResponse> response = pagoService.obtenerPagosPorCita(citaId);
        if (!esStaff(auth) && auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal && !response.isEmpty()) {
            if (!response.get(0).getUsuarioId().equals(principal.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/anular/{pagoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponse> anularPago(@PathVariable Long pagoId) {
        PagoResponse response = pagoService.anularPago(pagoId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION')")
    public ResponseEntity<List<PagoResponse>> obtenerTodosLosPagos() {
        return ResponseEntity.ok(pagoService.obtenerTodosLosPagos());
    }

    @GetMapping("/paciente/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or #usuarioId == authentication.principal.id")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(pagoService.obtenerPagosPorUsuario(usuarioId));
    }

    @GetMapping("/medico/{medicoId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('RECEPCION') or hasRole('MEDICO')")
    public ResponseEntity<List<PagoResponse>> obtenerPagosPorMedico(@PathVariable Long medicoId) {
        return ResponseEntity.ok(pagoService.obtenerPagosPorMedico(medicoId));
    }
}
