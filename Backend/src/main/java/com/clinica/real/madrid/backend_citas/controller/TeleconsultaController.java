package com.clinica.real.madrid.backend_citas.controller;

import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<Map<String, String>> getConfig(
            @RequestParam(value = "canal", required = false) String canal,
            @RequestParam(value = "rol", required = false) String rol,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

        long ahora = System.currentTimeMillis();
        boolean bloqueado = peticionesPorIp.compute(ip, (k, lista) -> {
            if (lista == null) {
                lista = new ArrayList<>();
            }
            lista.removeIf(timestamp -> (ahora - timestamp) > 60_000);
            if (lista.size() >= MAX_PETICIONES_POR_MINUTO) {
                return lista;
            }
            lista.add(ahora);
            return lista;
        }).size() > MAX_PETICIONES_POR_MINUTO;

        if (bloqueado) {
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

        config.put("agoraAppId", (aId != null && !aId.isEmpty()) ? aId : "AGORA_NOT_FOUND");
        config.put("deepgramApiKey", (dKey != null && !dKey.isEmpty()) ? dKey : "DEEPGRAM_NOT_FOUND");

        if (canal != null && !canal.isEmpty() && aId != null && !aId.isEmpty() && aCert != null && !aCert.isEmpty()) {
            try {
                io.agora.media.RtcTokenBuilder2 tokenBuilder = new io.agora.media.RtcTokenBuilder2();
                int expirationTimeInSeconds = 7200;
                int timestamp = (int)(System.currentTimeMillis() / 1000 + expirationTimeInSeconds);
                
                String result = tokenBuilder.buildTokenWithUid(
                    aId, aCert, canal, 0, io.agora.media.RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp);
                
                config.put("agoraToken", result);
                if (rol != null && rol.toUpperCase().contains("MEDICO") && canal.startsWith("cita-")) {
                    try {
                        Long citaId = Long.parseLong(canal.substring(5));
                        citaService.notificarDoctorEnSala(citaId);
                    } catch (NumberFormatException e) {
                        System.err.println("Error al extraer ID de cita del canal: " + canal);
                    }
                }
            } catch (Exception e) {
                config.put("agoraToken", "ERROR_GENERATING_TOKEN");
            }
        } else {
            config.put("agoraToken", "MISSING_VARIABLES_OR_CANAL");
        }

        return ResponseEntity.ok(config);
    }
}
