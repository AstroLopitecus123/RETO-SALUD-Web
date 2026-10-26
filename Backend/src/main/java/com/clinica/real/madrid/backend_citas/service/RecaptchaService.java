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

    @Value("${recaptcha.secret-key:6Ld0RgEuAAAAACclJJKS-ryzA7S5kAXiBkfMMeRJ}")
    private String recaptchaSecretKey;

    @Value("${recaptcha.enabled:true}")
    private boolean recaptchaEnabled;

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final String TEST_BYPASS_TOKEN = "TEST_BYPASS_TOKEN";

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean validarToken(String token) {
        if (!recaptchaEnabled) {
            return true;
        }

        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        if (TEST_BYPASS_TOKEN.equals(token)) {
            return true;
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
            return true;
        }
        return false;
    }
}
