package com.wilgner.cardapio.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class TokenCookieService {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Value("${api.security.cookie.secure}")
    private boolean secure;

    @Value("${api.security.cookie.same-site}")
    private String sameSite;

    @Value("${api.security.cookie.domain:}")
    private String domain;

    public ResponseCookie accessToken(String token, Duration maxAge) {
        return tokenCookie(ACCESS_TOKEN_COOKIE, token, maxAge);
    }

    public ResponseCookie refreshToken(String token, Duration maxAge) {
        return tokenCookie(REFRESH_TOKEN_COOKIE, token, maxAge);
    }

    public ResponseCookie clearAccessToken() {
        return tokenCookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO);
    }

    public ResponseCookie clearRefreshToken() {
        return tokenCookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO);
    }

    private ResponseCookie tokenCookie(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge);

        if (StringUtils.hasText(domain)) {
            builder.domain(domain);
        }

        return builder.build();
    }
}
