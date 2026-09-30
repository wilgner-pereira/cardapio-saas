package com.wilgner.cardapio.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.exception.ApiExceptionHandler;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.util.LogSanitizer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class LogPrivacyTest {
    @Test void securityAuditUsesStableIdsWithoutPiiOrControlCharacters() {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityAuditLogger.LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            new SecurityAuditLogger().record(SecurityAuditLogger.Event.ESTABLISHMENT_DISABLED,
                    12L, null, 37L, null);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .isEqualTo("security_audit event=ESTABLISHMENT_DISABLED actor_user_id=12 "
                            + "target_user_id=- establishment_id=37 product_id=-")
                    .doesNotContain("\r", "\n", "email", "password", "token", "cookie");
        } finally { logger.detachAppender(appender); }
    }

    @Test void unexpectedFailureDoesNotLogExceptionSecretsOrRawUrl() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("GET", "/public/owner@example.test/private-token");
            new ApiExceptionHandler().handleUnexpected(new RuntimeException("password=private-password cookie=private-cookie"), request);
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("eventId=", "/public/**", "RuntimeException")
                    .doesNotContain("owner@", "private-token", "private-password", "private-cookie");
            assertThat(event.getThrowableProxy()).isNull();
        } finally { logger.detachAppender(appender); }
    }

    @Test void deniedAuthenticationUsesInternalIdAndRouteGroup() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RestAccessDeniedHandler.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var user = new Usuario(); user.setId(123L); user.setUsername("private-owner@example.test");
            var details = new CustomUserDetails(user);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            new RestAccessDeniedHandler(new ObjectMapper().findAndRegisterModules()).handle(
                    new MockHttpServletRequest("POST", "/painel/private-owner@example.test"), new MockHttpServletResponse(),
                    new AccessDeniedException("private-secret"));
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("userId=123", "/painel/**")
                    .doesNotContain("private-owner", "private-secret");
            assertThat(LogSanitizer.route("/unknown/secret?token=secret")).isEqualTo("other");
            assertThat(LogSanitizer.method("GET\r\nsecret")).isEqualTo("OTHER");
        } finally { SecurityContextHolder.clearContext(); logger.detachAppender(appender); }
    }
}
