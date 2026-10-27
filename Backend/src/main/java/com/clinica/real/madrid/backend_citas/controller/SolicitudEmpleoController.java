package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.model.SolicitudEmpleo;
import com.clinica.real.madrid.backend_citas.service.SolicitudEmpleoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudEmpleoController {

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> peticionesPorIp = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SOLICITUDES_POR_10_MIN = 5;

    @Autowired
    private SolicitudEmpleoService solicitudService;

    @PostMapping
    public ResponseEntity<?> enviarSolicitud(@RequestBody SolicitudEmpleo solicitud, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!admitir(httpRequest.getRemoteAddr())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Demasiadas solicitudes enviadas. Por favor espere unos minutos."));
        }

        return ResponseEntity.ok(solicitudService.enviarSolicitud(solicitud));
    }

    private static boolean admitir(String ip) {
        synchronized (peticionesPorIp) {
            long ahora = System.currentTimeMillis();
            peticionesPorIp.entrySet().removeIf(e -> e.getValue().isEmpty()
                    || ahora - e.getValue().get(e.getValue().size() - 1) >= 600_000);
            List<Long> intentos = peticionesPorIp.get(ip);
            if (intentos == null) {
                if (peticionesPorIp.size() >= 1000) { return false; }
                intentos = new java.util.ArrayList<>();
                peticionesPorIp.put(ip, intentos);
            }
            intentos.removeIf(ts -> ahora - ts >= 600_000);
            if (intentos.size() >= MAX_SOLICITUDES_POR_10_MIN) { return false; }
            intentos.add(ahora);
            return true;
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SolicitudEmpleo>> listarTodas() {
        return ResponseEntity.ok(solicitudService.listarTodas());
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SolicitudEmpleo> procesarSolicitud(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String estadoStr = body.get("estado");
        SolicitudEmpleo.EstadoSolicitud nuevoEstado = SolicitudEmpleo.EstadoSolicitud.valueOf(estadoStr.toUpperCase());

        return ResponseEntity.ok(solicitudService.procesarSolicitud(id, nuevoEstado));
    }
}
