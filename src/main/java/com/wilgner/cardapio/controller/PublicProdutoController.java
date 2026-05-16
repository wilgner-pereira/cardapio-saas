package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.service.PublicProdutoServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public/{slug}/cardapio")
public class PublicProdutoController {

    private final PublicProdutoServiceImpl produtoService;

    public PublicProdutoController(PublicProdutoServiceImpl produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> buscarDinamico(
            @PathVariable String slug,
            @RequestParam(required = false) String categoria) {
        List<ProdutoResponseDTO> lista = produtoService.listarProdutoDinamico(slug, categoria);
        return ResponseEntity.status(HttpStatus.OK).body(lista);
    }

    @GetMapping("/info")
    public ResponseEntity<PublicProdutoServiceImpl.EstabelecimentoInfoDTO> obterInfo(
            @PathVariable String slug) {
        return ResponseEntity.ok(produtoService.obterInfoEstabelecimento(slug));
    }
}
