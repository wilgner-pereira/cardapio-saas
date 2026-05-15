package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Buscar 1 produto
    Optional<Produto> findByUsuarioAndId(Usuario usuario, Long id);

    // Buscar todos do usuário
    List<Produto> findByUsuario(Usuario usuario);

    // Buscar por ativo
    List<Produto> findByUsuarioAndAtivo(Usuario usuario, Boolean ativo);

    // Buscar por categoria
    List<Produto> findByUsuarioAndCategoria(Usuario usuario, String categoria);

    // Buscar por categoria + ativo
    List<Produto> findByUsuarioAndCategoriaAndAtivo(Usuario usuario, String categoria, Boolean ativo);
}
