package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.product.ProdutoRequestDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoOrdemRequestDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoStatusRequestDTO;
import com.wilgner.cardapio.service.AdminProdutoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/painel/produtos")
public class AdminProdutoController {

    private final AdminProdutoServiceImpl produtoService;

    public AdminProdutoController(AdminProdutoServiceImpl produtoService) {
        this.produtoService = produtoService;
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> cadastrarProduto(@RequestBody @Valid ProdutoRequestDTO produtoRequestDTO) {
        ProdutoResponseDTO response = produtoService.criarProduto(produtoRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> atualizarProduto(@PathVariable Long id, @RequestBody @Valid ProdutoRequestDTO produtoRequestDTO) {
        ProdutoResponseDTO response = produtoService.atualizarProduto(produtoRequestDTO, id);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProdutoResponseDTO> atualizarStatus(@PathVariable Long id, @RequestBody @Valid ProdutoStatusRequestDTO statusRequestDTO) {
        ProdutoResponseDTO response = produtoService.atualizarStatus(id, statusRequestDTO.ativo());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/ordem")
    public ResponseEntity<ProdutoResponseDTO> atualizarOrdem(@PathVariable Long id, @RequestBody @Valid ProdutoOrdemRequestDTO ordemRequestDTO) {
        ProdutoResponseDTO response = produtoService.atualizarOrdem(id, ordemRequestDTO.ordem());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirProduto(@PathVariable Long id) {
        produtoService.deleteProduto(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarProdutoPorId(@PathVariable Long id) {
        ProdutoResponseDTO response = produtoService.pesquisarProdutoPorId(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> buscarDinamico(@RequestParam(required = false) String categoria) {
        List<ProdutoResponseDTO> lista = produtoService.listarProdutoDinamico(categoria);
        return ResponseEntity.status(HttpStatus.OK).body(lista);
    }
}
