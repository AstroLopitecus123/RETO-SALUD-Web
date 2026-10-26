package com.clinica.real.madrid.backend_citas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RecaptchaService {

    @Value("${recaptcha.secret-key:dummy_recaptcha_secret}")
    private String recaptchaSecretKey;

    @Value("${recaptcha.enabled:true}")
    private boolean recaptchaEnabled;

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    // Bypass eliminado por seguridad

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean validarToken(String token) {
        if (!recaptchaEnabled || recaptchaSecretKey == null || recaptchaSecretKey.isBlank() || "dummy_recaptcha_secret".equals(recaptchaSecretKey)) {
            return true;
        }

        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", recaptchaSecretKey);
            params.add("response", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            Map<?, ?> response = restTemplate.postForObject(RECAPTCHA_VERIFY_URL, request, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Object scoreObj = response.get("score");
                if (scoreObj instanceof Number) {
                    double score = ((Number) scoreObj).doubleValue();
                    return score >= 0.5;
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error al validar reCAPTCHA: " + e.getMessage());
            return false;
        }
        return false;
    }
}
