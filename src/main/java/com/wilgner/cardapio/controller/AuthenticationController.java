package com.wilgner.cardapio.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.wilgner.cardapio.exception.ConflictException;
import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.auth.AuthenticationRequestDTO;
import com.wilgner.cardapio.model.dto.auth.AuthenticationResponseDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.security.CustomUserDetails;
import com.wilgner.cardapio.security.TokenCookieService;
import com.wilgner.cardapio.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth/admin")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenCookieService tokenCookieService;

    public AuthenticationController(AuthenticationManager authenticationManager,
                                    TokenService tokenService,
                                    UsuarioRepository usuarioRepository,
                                    RoleRepository roleRepository,
                                    PasswordEncoder passwordEncoder,
                                    TokenCookieService tokenCookieService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenCookieService = tokenCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDTO> login(
            @RequestBody @Valid AuthenticationRequestDTO dto
    ){
        var authToken = new UsernamePasswordAuthenticationToken(dto.username(), dto.password());
        var authentication = authenticationManager.authenticate(authToken);
        var userDetails = (CustomUserDetails) authentication.getPrincipal();

        var accessToken = tokenService.generateAccessToken(userDetails.getUser());
        var refreshToken = tokenService.generateRefreshToken(userDetails.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(accessToken, tokenService.getAccessTokenDuration()).toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.refreshToken(refreshToken, tokenService.getRefreshTokenDuration()).toString())
                .body(new AuthenticationResponseDTO("Autenticado", accessToken, "Bearer"));

    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@RequestBody @Valid RegisterRequestDTO dto){
        if (usuarioRepository.findByUsername(dto.username()).isPresent()) {
            throw new ConflictException("Username já está em uso");
        }
        if (usuarioRepository.findByEmail(dto.email()).isPresent()) {
            throw new ConflictException("E-mail já está em uso");
        }
        var encodedPassword = passwordEncoder.encode(dto.password());
        var usuario = new Usuario(dto.username(), encodedPassword, dto.email());

        var roleUser = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Role padrão ROLE_USER ausente no banco"));
        usuario.setRoles(Set.of(roleUser));
        var saved = usuarioRepository.save(usuario);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponseDTO(saved.getUsername()));


    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponseDTO> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken) {

        if (refreshToken == null) {
            throw new BadCredentialsException("Refresh token ausente");
        }

        String username;

        try {
            username = tokenService.validateRefreshToken(refreshToken);
        } catch (JWTVerificationException e) {
            throw new BadCredentialsException("Refresh token inválido");
        }

        Usuario user = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String newAccessToken = tokenService.generateAccessToken(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(newAccessToken, tokenService.getAccessTokenDuration()).toString())
                .body(new AuthenticationResponseDTO("Token renovado", newAccessToken, "Bearer"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.clearAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, tokenCookieService.clearRefreshToken().toString())
                .body(Map.of("message", "Sessão encerrada"));
    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateToken() {
        // Se a requisição chegou aqui, significa que o filtro de segurança (JWT Filter)
        // já validou o token com sucesso. Basta retornar 200 OK.
        return ResponseEntity.ok("Token válido");
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok(token);
    }
}
