package com.wilgner.cardapio.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wilgner.cardapio.model.entity.RefreshSession;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.RefreshSessionRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshSessionService {
    private final RefreshSessionRepository sessions;
    private final UsuarioRepository users;
    private final TokenService tokens;
    private final SecurityAuditLogger auditLogger;

    public RefreshSessionService(RefreshSessionRepository sessions, UsuarioRepository users, TokenService tokens,
                                 SecurityAuditLogger auditLogger) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public String start(Usuario user) {
        requireActive(user);
        String id = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        Instant expiration = Instant.now().plus(tokens.getRefreshTokenDuration()).truncatedTo(ChronoUnit.SECONDS);
        sessions.save(new RefreshSession(id, user.getId(), hash(jti), expiration));
        return tokens.generateRefreshToken(user, id, jti, expiration);
    }

    @Transactional
    public Rotation rotate(String token) {
        var claims = tokens.verifyRefreshToken(token);
        var session = sessions.findLockedById(claims.sessionId()).orElseThrow(RefreshSessionService::invalid);
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())
                || !session.getTokenHash().equals(hash(claims.jti()))) {
            throw invalid();
        }
        Usuario user = users.findWithRolesAndEstabelecimentoByUsername(claims.username())
                .filter(u -> u.getId().equals(session.getUsuarioId())).orElseThrow(RefreshSessionService::invalid);
        requireActive(user);
        String nextJti = UUID.randomUUID().toString();
        session.rotate(hash(nextJti));
        // Absolute session lifetime: refresh never extends the original expiration.
        return new Rotation(user, tokens.generateAccessToken(user),
                tokens.generateRefreshToken(user, session.getId(), nextJti, session.getExpiresAt()));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        TokenService.RefreshClaims claims;
        try {
            claims = tokens.verifyRefreshToken(token);
        } catch (JWTVerificationException e) {
            return; // Logout remains idempotent for missing/expired/invalid cookies.
        }
        // The signed session id also revokes the successor if logout races with rotation.
        sessions.findLockedById(claims.sessionId()).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.revoke(Instant.now());
                auditLogger.record(SecurityAuditLogger.Event.REFRESH_SESSION_REVOKED,
                        session.getUsuarioId(), session.getUsuarioId(), null, null);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        sessions.revokeAllForUser(userId, Instant.now());
    }

    private static void requireActive(Usuario user) {
        boolean platformAdmin = user.getRoles().stream()
                .anyMatch(role -> "ROLE_PLATFORM_ADMIN".equals(role.getRoleName()));
        if (!platformAdmin && (user.getEstabelecimento() == null
                || !Boolean.TRUE.equals(user.getEstabelecimento().getAtivo()))) {
            throw invalid();
        }
    }

    private static JWTVerificationException invalid() {
        return new JWTVerificationException("Sessão expirada");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public record Rotation(Usuario user, String accessToken, String refreshToken) {}
}
