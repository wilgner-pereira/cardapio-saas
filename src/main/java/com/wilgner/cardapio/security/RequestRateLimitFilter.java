package com.wilgner.cardapio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.model.dto.error.ApiErrorDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    private final ObjectMapper mapper;

    public RequestRateLimitFilter(RequestRateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = org.springframework.web.util.UriUtils.decode(
                request.getRequestURI().substring(request.getContextPath().length()),
                java.nio.charset.StandardCharsets.UTF_8);
        String scope = scope(request.getMethod(), path);
        if (scope != null) {
            // Do not accept user-supplied Forwarded/X-Forwarded-For as a client identity.
            long retryAfter = limiter.retryAfter(scope, request.getRemoteAddr());
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retryAfter));
                response.setHeader("Cache-Control", "no-store");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(response.getOutputStream(), ApiErrorDTO.of(429, "Too Many Requests",
                        "Muitas requisicoes. Tente novamente mais tarde.", request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String scope(String method, String path) {
        if ("POST".equals(method)) {
            return switch (path) {
                case "/auth/login" -> "login";
                case "/auth/refresh" -> "refresh";
                case "/setup/admin" -> "setup";
                default -> null;
            };
        }
        if (("GET".equals(method) || "HEAD".equals(method)) && path.startsWith("/public/")) {
            return "public";
        }
        return null;
    }
}
