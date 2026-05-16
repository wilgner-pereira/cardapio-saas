package com.wilgner.cardapio.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.auth.AuthenticationRequestDTO;
import com.wilgner.cardapio.model.dto.auth.AuthenticationResponseDTO;
import com.wilgner.cardapio.model.dto.error.ApiErrorDTO;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.security.CustomUserDetails;
import com.wilgner.cardapio.security.TokenCookieService;
import com.wilgner.cardapio.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
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

    public AuthenticationController(AuthenticationManager authenticationManager,
                                    TokenService tokenService,
                                    UsuarioRepository usuarioRepository,
                                    TokenCookieService tokenCookieService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.tokenCookieService = tokenCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDTO> login(
            @RequestBody @Valid AuthenticationRequestDTO dto
    ){
        Usuario usuario = usuarioRepository.findByUsernameOrEmail(dto.username(), dto.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        var authToken = new UsernamePasswordAuthenticationToken(usuario.getUsername(), dto.password());
        var authentication = authenticationManager.authenticate(authToken);
        var userDetails = (CustomUserDetails) authentication.getPrincipal();
        Usuario authenticatedUser = findAuthenticatedUser(userDetails.getUsername());

        var accessToken = tokenService.generateAccessToken(authenticatedUser);
        var refreshToken = tokenService.generateRefreshToken(authenticatedUser);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(accessToken, tokenService.getAccessTokenDuration()).toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.refreshToken(refreshToken, tokenService.getRefreshTokenDuration()).toString())
                .body(toResponse("Autenticado", accessToken, authenticatedUser));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                     HttpServletRequest request) {

        if (refreshToken == null) {
            return unauthorized("Sessão expirada", request);
        }

        String username;

        try {
            username = tokenService.validateRefreshToken(refreshToken);
        } catch (JWTVerificationException e) {
            return unauthorized("Sessão expirada", request);
        }

        Usuario user = findAuthenticatedUser(username);

        String newAccessToken = tokenService.generateAccessToken(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(newAccessToken, tokenService.getAccessTokenDuration()).toString())
                .body(toResponse("Token renovado", newAccessToken, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
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
        return ResponseEntity.ok(toResponse("Token válido", null, user));
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok(token);
    }

    private AuthenticationResponseDTO toResponse(String message, String accessToken, Usuario user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getRoleName())
                .sorted()
                .toList();

        return new AuthenticationResponseDTO(
                message,
                accessToken,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEstabelecimento() != null ? user.getEstabelecimento().getSlug() : null,
                roles,
                accessToken != null ? tokenService.getAccessTokenDuration().toMillis() : 0
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
