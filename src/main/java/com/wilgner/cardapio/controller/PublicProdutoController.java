package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.service.AdminProdutoServiceImpl;
import com.wilgner.cardapio.service.PublicProdutoServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public/{username}/cardapio")
public class PublicProdutoController {

    private PublicProdutoServiceImpl produtoService;
    public PublicProdutoController(PublicProdutoServiceImpl produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> buscarDinamico(
            @PathVariable String username,
            @RequestParam(required = false) String categoria) {
        List<ProdutoResponseDTO> lista = produtoService.listarProdutoDinamico(username, categoria);
        return ResponseEntity.status(HttpStatus.OK).body(lista);
    }

}
