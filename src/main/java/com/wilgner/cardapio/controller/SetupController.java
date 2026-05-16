package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.service.AdminEstabelecimentoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setup")
public class SetupController {

    private final AdminEstabelecimentoServiceImpl estabelecimentoService;

    public SetupController(AdminEstabelecimentoServiceImpl estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    @PostMapping("/admin")
    public ResponseEntity<RegisterResponseDTO> criarPrimeiroAdministrador(
            @RequestBody @Valid RegisterRequestDTO dto) {
        RegisterResponseDTO response = estabelecimentoService.cadastrarPrimeiroAdministrador(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
