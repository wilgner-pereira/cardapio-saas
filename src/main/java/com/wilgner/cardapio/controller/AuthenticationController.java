package com.wilgner.cardapio.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.auth.AuthenticationRequestDTO;
import com.wilgner.cardapio.model.dto.auth.AuthenticationResponseDTO;
import com.wilgner.cardapio.model.dto.auth.SessionStateDTO;
import com.wilgner.cardapio.model.dto.error.ApiErrorDTO;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.security.CustomUserDetails;
import com.wilgner.cardapio.security.TokenCookieService;
import com.wilgner.cardapio.security.TokenService;
import com.wilgner.cardapio.security.RefreshSessionService;
import com.wilgner.cardapio.security.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final TokenCookieService tokenCookieService;
    private final RefreshSessionService refreshSessions;
    private final SecurityAuditLogger auditLogger;

    public AuthenticationController(AuthenticationManager authenticationManager,
                                    TokenService tokenService,
                                    UsuarioRepository usuarioRepository,
                                    TokenCookieService tokenCookieService,
                                    RefreshSessionService refreshSessions,
                                    SecurityAuditLogger auditLogger) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.tokenCookieService = tokenCookieService;
        this.refreshSessions = refreshSessions;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDTO> login(
            @RequestBody @Valid AuthenticationRequestDTO dto
    ){
        try {
            Usuario usuario = usuarioRepository.findByUsernameOrEmail(dto.username(), dto.username())
                    .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

            var authToken = new UsernamePasswordAuthenticationToken(usuario.getUsername(), dto.password());
            var authentication = authenticationManager.authenticate(authToken);
            var userDetails = (CustomUserDetails) authentication.getPrincipal();
            Usuario authenticatedUser = findAuthenticatedUser(userDetails.getUsername());
            var accessToken = tokenService.generateAccessToken(authenticatedUser);
            String refreshToken = refreshSessions.start(authenticatedUser);

            auditLogger.record(SecurityAuditLogger.Event.LOGIN_SUCCEEDED,
                    authenticatedUser.getId(), authenticatedUser.getId(),
                    authenticatedUser.getEstabelecimento() != null
                            ? authenticatedUser.getEstabelecimento().getId() : null,
                    null);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(accessToken, tokenService.getAccessTokenDuration()).toString())
                    .header(HttpHeaders.SET_COOKIE, tokenCookieService.refreshToken(refreshToken, tokenService.getRefreshTokenDuration()).toString())
                    .body(toResponse("Autenticado", authenticatedUser, tokenService.getAccessTokenDuration().toMillis()));
        } catch (AuthenticationException | JWTVerificationException e) {
            auditLogger.record(SecurityAuditLogger.Event.LOGIN_FAILED, null, null, null, null);
            throw new BadCredentialsException("Credenciais inválidas");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                     HttpServletRequest request) {

        if (refreshToken == null) {
            return unauthorized("Sessão expirada", request);
        }

        RefreshSessionService.Rotation rotation;
        try {
            rotation = refreshSessions.rotate(refreshToken);
        } catch (JWTVerificationException e) {
            return unauthorized("Sessão expirada", request);
        }

        Usuario user = rotation.user();
        String newAccessToken = rotation.accessToken();
        String newRefreshToken = rotation.refreshToken();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(newAccessToken, tokenService.getAccessTokenDuration()).toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.refreshToken(newRefreshToken, tokenService.getRefreshTokenDuration()).toString())
                .body(toResponse("Token renovado", user, tokenService.getAccessTokenDuration().toMillis()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        auditLogger.recordCurrent(SecurityAuditLogger.Event.LOGOUT, null, null, null);
        refreshSessions.revoke(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.clearAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.clearRefreshToken().toString())
                .body(Map.of("message", "Sessão encerrada"));
    }

    @GetMapping("/validate")
    public ResponseEntity<AuthenticationResponseDTO> validateToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Usuario user = findAuthenticatedUser(userDetails.getUsername());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toResponse("Token válido", user, 0));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionStateDTO> sessionState(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticationResponseDTO session = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails details) {
            session = toResponse("Sessão ativa", findAuthenticatedUser(details.getUsername()), 0);
        }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new SessionStateDTO(session != null,
                        refreshToken != null && !refreshToken.isBlank(), session));
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        // Keep XOR masking on the JSON token. The dedicated header distinguishes
        // this representation from the raw cookie-to-header SPA mechanism.
        CsrfToken responseToken = new DefaultCsrfToken("X-CSRF-TOKEN", token.getParameterName(), token.getToken());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(responseToken);
    }

    private AuthenticationResponseDTO toResponse(String message, Usuario user, long accessExpiresInMs) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getRoleName())
                .sorted()
                .toList();

        return new AuthenticationResponseDTO(
                message,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEstabelecimento() != null ? user.getEstabelecimento().getSlug() : null,
                roles,
                accessExpiresInMs
        );
    }

    private Usuario findAuthenticatedUser(String username) {
        return usuarioRepository.findWithRolesAndEstabelecimentoByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private ResponseEntity<ApiErrorDTO> unauthorized(String message, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(ApiErrorDTO.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }
}
