package com.clinica.real.madrid.backend_citas.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/teleconsulta")
public class TeleconsultaController {

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
            @org.springframework.web.bind.annotation.RequestParam(value = "canal", required = false) String canal,
            @org.springframework.web.bind.annotation.RequestParam(value = "rol", required = false) String rol) {
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

        System.out.println("DEBUG RAILWAY: agoraAppId from @Value=" + agoraAppId);
        System.out.println("DEBUG RAILWAY: AGORA_APP_ID env=" + System.getenv("AGORA_APP_ID"));
        System.out.println("DEBUG RAILWAY: agoraAppCertificate from @Value=" + agoraAppCertificate);
        System.out.println("DEBUG RAILWAY: AGORA_APP_CERTIFICATE env=" + System.getenv("AGORA_APP_CERTIFICATE"));

        config.put("agoraAppId", (aId != null && !aId.isEmpty()) ? aId : "AGORA_NOT_FOUND");
        config.put("deepgramApiKey", (dKey != null && !dKey.isEmpty()) ? dKey : "DEEPGRAM_NOT_FOUND");

        // Generar Token de Agora si se pasó un canal y se tiene el certificado
        if (canal != null && !canal.isEmpty() && aId != null && !aId.isEmpty() && aCert != null && !aCert.isEmpty()) {
            try {
                io.agora.media.RtcTokenBuilder2 tokenBuilder = new io.agora.media.RtcTokenBuilder2();
                // Expira en 2 horas (7200 segundos)
                int expirationTimeInSeconds = 7200;
                int timestamp = (int)(System.currentTimeMillis() / 1000 + expirationTimeInSeconds);
                
                String result = tokenBuilder.buildTokenWithUid(
                    aId, aCert, canal, 0, io.agora.media.RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp);
                
                config.put("agoraToken", result);
                // Notificar al paciente si el que entra es el MÉDICO
                System.out.println("DEBUG TELECONSULTA -> Petición de config: canal=" + canal + ", rol=" + rol);
                if (rol != null && rol.toUpperCase().contains("MEDICO") && canal.startsWith("cita-")) {
                    try {
                        Long citaId = Long.parseLong(canal.substring(5)); // "cita-88" -> "88"
                        System.out.println("DEBUG TELECONSULTA -> Disparando notificación para cita " + citaId);
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
