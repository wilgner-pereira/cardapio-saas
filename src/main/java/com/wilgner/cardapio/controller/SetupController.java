package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.service.AdminEstabelecimentoServiceImpl;
import com.wilgner.cardapio.security.SecurityAuditLogger;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setup")
@ConditionalOnProperty(prefix = "app.setup-admin", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SetupController {

    private final AdminEstabelecimentoServiceImpl estabelecimentoService;
    private final SecurityAuditLogger auditLogger;
    private final byte[] expectedToken;

    public SetupController(AdminEstabelecimentoServiceImpl estabelecimentoService,
                           SecurityAuditLogger auditLogger,
                           @Value("${app.setup-admin.token:}") String setupToken) {
        if (setupToken == null || setupToken.isBlank() || setupToken.length() < 32) {
            throw new IllegalStateException("Setup habilitado exige APP_SETUP_ADMIN_TOKEN com pelo menos 32 caracteres");
        }
        this.estabelecimentoService = estabelecimentoService;
        this.auditLogger = auditLogger;
        this.expectedToken = setupToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin")
    public ResponseEntity<RegisterResponseDTO> criarPrimeiroAdministrador(
            @RequestBody @Valid RegisterRequestDTO dto,
            @RequestHeader(name = "X-Setup-Token", required = false) String setupToken) {
        if (setupToken == null || !java.security.MessageDigest.isEqual(expectedToken,
                setupToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            auditLogger.record(SecurityAuditLogger.Event.SETUP_REJECTED, null, null, null, null);
            throw new AccessDeniedException("Acesso negado");
        }
        RegisterResponseDTO response = estabelecimentoService.cadastrarPrimeiroAdministrador(dto);
        auditLogger.record(SecurityAuditLogger.Event.SETUP_COMPLETED, null, null, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
