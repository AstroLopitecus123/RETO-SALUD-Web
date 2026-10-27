package com.clinica.real.madrid.backend_citas.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @org.springframework.beans.factory.annotation.Autowired
    private com.clinica.real.madrid.backend_citas.repository.UsuarioRepository usuarioRepository;

    @Value("${jwt.secret:}")
    private String jwtSecret; 

    @Value("${jwt.expirationMs:86400000}")
    private long jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.contains("dummy_jwt_secret")) {
            // Genera clave segura efímera en memoria para evitar uso de claves por defecto públicas
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
    }

    private SecretKey getSigningKey() {
        if (this.signingKey == null) {
            init();
        }
        return this.signingKey;
    }

    public String generateToken(String username) {
        long version = usuarioRepository.findByCorreo(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado")).getTokenVersion();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim("ver", version)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String token, UsuarioPrincipal principal) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token).getBody();
            Object version = claims.get("ver");
            // Los JWT emitidos antes de esta migracion equivalen solo a version cero.
            long tokenVersion = version == null ? 0L : claims.get("ver", Long.class);
            return tokenVersion == principal.getTokenVersion()
                    && principal.getUsername().equals(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
