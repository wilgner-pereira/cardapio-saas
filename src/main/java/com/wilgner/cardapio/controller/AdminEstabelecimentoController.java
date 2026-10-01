package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoCreateRequestDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResponseDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResumoDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientDetailsDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientPasswordRequestDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientUpdateRequestDTO;
import com.wilgner.cardapio.service.AdminEstabelecimentoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plataforma/estabelecimentos")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminEstabelecimentoController {

    private final AdminEstabelecimentoServiceImpl estabelecimentoService;

    public AdminEstabelecimentoController(AdminEstabelecimentoServiceImpl estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    @PostMapping
    public ResponseEntity<EstabelecimentoResponseDTO> cadastrarEstabelecimento(
            @RequestBody @Valid EstabelecimentoCreateRequestDTO dto) {
        EstabelecimentoResponseDTO response = estabelecimentoService.cadastrarEstabelecimento(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EstabelecimentoResumoDTO>> listarEstabelecimentos() {
        return ResponseEntity.ok(estabelecimentoService.listarEstabelecimentos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlatformClientDetailsDTO> obterEstabelecimento(@PathVariable Long id) {
        PlatformClientDetailsDTO response = estabelecimentoService.obterEstabelecimento(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlatformClientDetailsDTO> atualizarEstabelecimento(@PathVariable Long id,
            @RequestBody @Valid PlatformClientUpdateRequestDTO dto) {
        return ResponseEntity.ok(estabelecimentoService.atualizarEstabelecimento(id, dto));
    }

    @PatchMapping("/{id}/senha")
    public ResponseEntity<Void> redefinirSenha(@PathVariable Long id,
            @RequestBody @Valid PlatformClientPasswordRequestDTO dto) {
        estabelecimentoService.redefinirSenha(id, dto);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<EstabelecimentoResponseDTO> ativarEstabelecimento(@PathVariable Long id) {
        EstabelecimentoResponseDTO response = estabelecimentoService.ativarDesativarEstabelecimento(id, true);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<EstabelecimentoResponseDTO> desativarEstabelecimento(@PathVariable Long id) {
        EstabelecimentoResponseDTO response = estabelecimentoService.ativarDesativarEstabelecimento(id, false);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEstabelecimento(@PathVariable Long id) {
        estabelecimentoService.deletarEstabelecimento(id);
        return ResponseEntity.noContent().build();
    }
}
