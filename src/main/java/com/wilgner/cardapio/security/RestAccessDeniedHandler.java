package com.wilgner.cardapio.security;

import com.wilgner.cardapio.model.dto.error.ApiErrorDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.util.LogSanitizer;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("Acesso negado metodo={} uri={} usuario={} autenticado={} motivo={}",
                LogSanitizer.sanitize(request.getMethod()),
                LogSanitizer.sanitize(request.getRequestURI()),
                authentication != null ? LogSanitizer.sanitize(authentication.getName()) : "anonymous",
                authentication != null && authentication.isAuthenticated(),
                accessDeniedException.getClass().getSimpleName());

        HttpStatus status = HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorDTO.of(
                status.value(),
                status.getReasonPhrase(),
                "Acesso negado",
                request.getRequestURI()
        ));
    }
}
