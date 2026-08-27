package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.product.CategoriaOrdemRequestDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.service.AdminProdutoServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/painel/categorias")
public class AdminCategoriaController {

    private final AdminProdutoServiceImpl produtoService;

    public AdminCategoriaController(AdminProdutoServiceImpl produtoService) {
        this.produtoService = produtoService;
    }

    @PatchMapping("/ordem")
    public ResponseEntity<List<ProdutoResponseDTO>> atualizarOrdem(
            @RequestBody @Valid CategoriaOrdemRequestDTO request) {
        return ResponseEntity.ok(produtoService.trocarOrdemCategorias(
                request.categoria().trim(),
                request.categoriaAlvo().trim()
        ));
    }
}
