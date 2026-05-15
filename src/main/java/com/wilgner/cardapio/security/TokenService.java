package com.wilgner.cardapio.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wilgner.cardapio.model.entity.Usuario;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.auth0.jwt.algorithms.Algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
public class TokenService {
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${api.security.token.issuer}")
    private String issuer;

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${api.security.token.access-expiration-ms}")
    private Long accessExpirationMs;

    @Value("${api.security.token.refresh-expiration-ms}")
    private Long refreshExpirationMs;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET deve ter pelo menos 32 caracteres");
        }
    }

    public String generateAccessToken(Usuario usuario) {
        return generateToken(usuario, getAccessTokenDuration(), ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(Usuario usuario) {
        return generateToken(usuario, getRefreshTokenDuration(), REFRESH_TOKEN_TYPE);
    }

    public String validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN_TYPE);
    }

    public String validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN_TYPE);
    }

    public Duration getAccessTokenDuration() {
        return Duration.ofMillis(accessExpirationMs);
    }

    public Duration getRefreshTokenDuration() {
        return Duration.ofMillis(refreshExpirationMs);
    }

    private String generateToken(Usuario usuario, Duration duration, String tokenType) {
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            Instant now = Instant.now();
            return JWT.create()
                    .withIssuer(issuer)
                    .withSubject(usuario.getUsername())
                    .withClaim(TOKEN_TYPE_CLAIM, tokenType)
                    .withIssuedAt(Date.from(now))
                    .withExpiresAt(Date.from(now.plus(duration)))
                    .sign(algorithm);

        }catch(JWTCreationException exception){
            throw new RuntimeException("Erro ao gerar token", exception);
        }
    }

    private String validateToken(String token, String expectedType){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decodedJwt = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .build()
                    .verify(token);

            String tokenType = decodedJwt.getClaim(TOKEN_TYPE_CLAIM).asString();
            if (!Objects.equals(expectedType, tokenType)) {
                throw new JWTVerificationException("Tipo de token inválido");
            }

            return decodedJwt.getSubject();
        } catch(JWTVerificationException exception){
            throw new JWTVerificationException(exception.getMessage());
        }
    }
}
