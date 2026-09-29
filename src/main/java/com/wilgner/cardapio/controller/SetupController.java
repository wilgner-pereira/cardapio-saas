package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.service.AdminEstabelecimentoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final AdminEstabelecimentoServiceImpl estabelecimentoService;
    private final byte[] expectedToken;

    public SetupController(AdminEstabelecimentoServiceImpl estabelecimentoService,
                           @Value("${app.setup-admin.token:}") String setupToken) {
        if (setupToken == null || setupToken.isBlank() || setupToken.length() < 32) {
            throw new IllegalStateException("Setup habilitado exige APP_SETUP_ADMIN_TOKEN com pelo menos 32 caracteres");
        }
        this.estabelecimentoService = estabelecimentoService;
        this.expectedToken = setupToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin")
    public ResponseEntity<RegisterResponseDTO> criarPrimeiroAdministrador(
            @RequestBody @Valid RegisterRequestDTO dto,
            @RequestHeader(name = "X-Setup-Token", required = false) String setupToken) {
        if (setupToken == null || !java.security.MessageDigest.isEqual(expectedToken,
                setupToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            log.warn("Tentativa de setup rejeitada: credencial de setup ausente ou invalida");
            throw new AccessDeniedException("Acesso negado");
        }
        RegisterResponseDTO response = estabelecimentoService.cadastrarPrimeiroAdministrador(dto);
        log.info("Administrador inicial criado pelo setup");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
