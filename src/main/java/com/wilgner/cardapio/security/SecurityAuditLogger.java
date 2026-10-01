package com.wilgner.cardapio.security;

import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.service.SecurityUtils;
import com.wilgner.cardapio.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {
    static final String LOGGER_NAME = "SECURITY_AUDIT";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    public enum Event {
        ESTABLISHMENT_CREATED,
        ESTABLISHMENT_ACTIVATED,
        ESTABLISHMENT_DISABLED,
        ESTABLISHMENT_DELETED,
        ESTABLISHMENT_UPDATED,
        CLIENT_PASSWORD_RESET,
        PLATFORM_ADMIN_CREATED,
        PRODUCT_DELETED,
        LOGIN_SUCCEEDED,
        LOGIN_FAILED,
        LOGOUT,
        REFRESH_SESSION_REVOKED,
        SETUP_COMPLETED,
        SETUP_REJECTED
    }

    public void recordCurrent(Event event, Long targetUserId, Long establishmentId, Long productId) {
        Usuario actor = SecurityUtils.getUsuarioAutenticado();
        record(event, actor != null ? actor.getId() : null, targetUserId, establishmentId, productId);
    }

    public void record(Event event, Long actorUserId, Long targetUserId, Long establishmentId, Long productId) {
        log.info("security_audit event={} actor_user_id={} target_user_id={} establishment_id={} product_id={}",
                LogSanitizer.sanitize(event.name()), value(actorUserId), value(targetUserId),
                value(establishmentId), value(productId));
    }

    private static String value(Long id) {
        return id == null ? "-" : id.toString();
    }
}
