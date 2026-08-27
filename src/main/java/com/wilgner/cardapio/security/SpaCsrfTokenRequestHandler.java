package com.wilgner.cardapio.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Permite que uma SPA envie no header o valor bruto publicado no cookie
 * XSRF-TOKEN, preservando o token com codificacao XOR nos atributos da request.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(SpaCsrfTokenRequestHandler.class);

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       Supplier<CsrfToken> deferredCsrfToken) {
        xor.handle(request, response, deferredCsrfToken);
        deferredCsrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        boolean tokenCameFromHeader = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()));
        CsrfTokenRequestHandler delegate = tokenCameFromHeader ? plain : xor;

        log.debug("Resolvendo CSRF metodo={} uri={} origem={}",
                request.getMethod(),
                request.getRequestURI(),
                tokenCameFromHeader ? "header-spa" : "parametro-xor");

        return delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
