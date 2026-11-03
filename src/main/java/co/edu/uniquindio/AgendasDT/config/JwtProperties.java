package co.edu.uniquindio.AgendasDT.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.jwt")
public record JwtProperties(String secret, Duration expiration) {
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Configure JWT_SECRET con al menos 32 caracteres para firmar los tokens de acceso.");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("security.jwt.expiration debe ser una duración positiva.");
        }
    }
}
