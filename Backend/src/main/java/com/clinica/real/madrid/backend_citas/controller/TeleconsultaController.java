package com.clinica.real.madrid.backend_citas.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.model.Cita;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/teleconsulta")
public class TeleconsultaController {

    private static final Map<String, List<Long>> peticionesPorIp = new ConcurrentHashMap<>();
    private static final int MAX_PETICIONES_POR_MINUTO = 30;

    @Value("${AGORA_APP_ID:}")
    private String agoraAppId;

    @Value("${AGORA_APP_CERTIFICATE:}")
    private String agoraAppCertificate;

    @Value("${DEEPGRAM_API_KEY:}")
    private String deepgramApiKey;

    @SuppressWarnings("java:S6813")
    @org.springframework.beans.factory.annotation.Autowired
    private com.clinica.real.madrid.backend_citas.service.CitaService citaService;

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(
            @RequestParam(value = "canal", required = false) String canal,
            @RequestParam(value = "rol", required = false) String rol,
            HttpServletRequest httpRequest,
            Authentication auth) {

        if (!admitir(httpRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Demasiadas peticiones. Por favor espere un momento."));
        }

        Map<String, String> config = new HashMap<>();

        String dKey = deepgramApiKey;
        if (dKey == null || dKey.isEmpty() || dKey.equals("DEEPGRAM_NOT_FOUND")) {
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if (entry.getKey().contains("DEEPGRAM_API_KEY") && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    dKey = entry.getValue().trim();
                }
            }
        }

        String aId = agoraAppId;
        if (aId == null || aId.isEmpty() || aId.equals("AGORA_NOT_FOUND")) {
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if (entry.getKey().contains("AGORA_APP_ID") && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    aId = entry.getValue();
                }
            }
        }

        String aCert = agoraAppCertificate;
        if (aCert == null || aCert.isEmpty() || aCert.equals("CERT_NOT_FOUND")) {
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if (entry.getKey().contains("AGORA_APP_CERTIFICATE") && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    aCert = entry.getValue();
                }
            }
        }

        if (canal != null && !canal.trim().isEmpty()) {
            if (!canal.matches("^cita-\\d+$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Formato de canal invalido"));
            }

            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UsuarioPrincipal)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Debe iniciar sesion para ingresar a la videollamada"));
            }

            Long cId;
            Cita cita;
            try {
                cId = Long.parseLong(canal.substring(5));
                cita = citaService.obtenerPorId(cId);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Cita no valida o no encontrada"));
            }

            boolean esStaff = auth.getAuthorities().stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCION"));
            boolean esMedico = false;

            if (!esStaff && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
                boolean esPaciente = cita.getPaciente() != null && cita.getPaciente().getId().equals(principal.getId());
                esMedico = cita.getMedico() != null && cita.getMedico().getUsuario() != null 
                    && cita.getMedico().getUsuario().getId().equals(principal.getId());
                
                if (!esPaciente && !esMedico) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acceso denegado a esta consulta"));
                }
            } else if (auth.getPrincipal() instanceof UsuarioPrincipal principal && cita.getMedico() != null && cita.getMedico().getUsuario() != null) {
                esMedico = cita.getMedico().getUsuario().getId().equals(principal.getId());
            }

            config.put("agoraAppId", (aId != null && !aId.isEmpty()) ? aId : "AGORA_NOT_FOUND");
            // Compatibilidad con el WebSocket actual: solo accesos autorizados al canal reciben la clave.
            config.put("deepgramApiKey", (dKey != null && !dKey.isEmpty()) ? dKey : "DEEPGRAM_NOT_FOUND");

            if (aId != null && !aId.isEmpty() && aCert != null && !aCert.isEmpty()) {
                try {
                    io.agora.media.RtcTokenBuilder2 tokenBuilder = new io.agora.media.RtcTokenBuilder2();
                    int expirationTimeInSeconds = 7200;
                    int timestamp = (int)(System.currentTimeMillis() / 1000 + expirationTimeInSeconds);
                    String result = tokenBuilder.buildTokenWithUid(
                        aId, aCert, canal, 0, io.agora.media.RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp);
                    config.put("agoraToken", result);

                    if (esMedico) {
                        citaService.notificarDoctorEnSala(cId);
                    }
                } catch (Exception e) {
                    config.put("agoraToken", "ERROR_GENERATING_TOKEN");
                }
            } else {
                config.put("agoraToken", "MISSING_VARIABLES");
            }
            return ResponseEntity.ok(config);
        }

        config.put("agoraAppId", (aId != null && !aId.isEmpty()) ? aId : "AGORA_NOT_FOUND");
        return ResponseEntity.ok(config);
    }

    private static boolean admitir(String ip) {
        synchronized (peticionesPorIp) {
            long ahora = System.currentTimeMillis();
            peticionesPorIp.entrySet().removeIf(e -> e.getValue().isEmpty()
                    || ahora - e.getValue().get(e.getValue().size() - 1) >= 60_000);
            List<Long> intentos = peticionesPorIp.get(ip);
            if (intentos == null) {
                if (peticionesPorIp.size() >= 1000) { return false; }
                intentos = new ArrayList<>();
                peticionesPorIp.put(ip, intentos);
            }
            intentos.removeIf(ts -> ahora - ts >= 60_000);
            if (intentos.size() >= MAX_PETICIONES_POR_MINUTO) { return false; }
            intentos.add(ahora);
            return true;
        }
    }
}
