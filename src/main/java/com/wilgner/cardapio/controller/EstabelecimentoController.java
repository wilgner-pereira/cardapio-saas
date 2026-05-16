package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResponseDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoUpdateRequestDTO;
import com.wilgner.cardapio.service.EstabelecimentoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/painel/estabelecimento")
@PreAuthorize("hasRole('USER')")
public class EstabelecimentoController {

    private final EstabelecimentoServiceImpl estabelecimentoService;

    public EstabelecimentoController(EstabelecimentoServiceImpl estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    @GetMapping
    public ResponseEntity<EstabelecimentoResponseDTO> obterMeuEstabelecimento() {
        EstabelecimentoResponseDTO response = estabelecimentoService.obterMeuEstabelecimento();
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<EstabelecimentoResponseDTO> atualizarMeuEstabelecimento(
            @RequestBody @Valid EstabelecimentoUpdateRequestDTO dto) {
        EstabelecimentoResponseDTO response = estabelecimentoService.atualizarMeuEstabelecimento(dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/logo")
    public ResponseEntity<EstabelecimentoResponseDTO> atualizarLogo(
            @RequestParam String logoUrl) {
        EstabelecimentoResponseDTO response = estabelecimentoService.atualizarLogo(logoUrl);
        return ResponseEntity.ok(response);
    }
}
