package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Por estabelecimento
    Optional<Produto> findByEstabelecimentoAndId(Estabelecimento estabelecimento, Long id);
    List<Produto> findByEstabelecimento(Estabelecimento estabelecimento);
    List<Produto> findByEstabelecimentoAndAtivo(Estabelecimento estabelecimento, Boolean ativo);
    List<Produto> findByEstabelecimentoAndCategoria(Estabelecimento estabelecimento, String categoria);
    List<Produto> findByEstabelecimentoAndCategoriaAndAtivo(Estabelecimento estabelecimento, String categoria, Boolean ativo);

    @Query("SELECT p FROM Produto p WHERE p.estabelecimento = :estabelecimento ORDER BY p.categoria ASC, p.ordem ASC, p.id ASC")
    List<Produto> findByEstabelecimentoOrderByCategoriaAndOrdem(@Param("estabelecimento") Estabelecimento estabelecimento);

    @Query("SELECT p FROM Produto p WHERE p.estabelecimento = :estabelecimento AND p.ativo = true ORDER BY p.categoria ASC, p.ordem ASC, p.id ASC")
    List<Produto> findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(@Param("estabelecimento") Estabelecimento estabelecimento);

    @Query("SELECT p FROM Produto p WHERE p.estabelecimento = :estabelecimento AND p.categoria = :categoria AND p.ativo = true ORDER BY p.ordem ASC, p.id ASC")
    List<Produto> findByEstabelecimentoCategoriaAtivoOrderByOrdem(@Param("estabelecimento") Estabelecimento estabelecimento, @Param("categoria") String categoria);

    @Query("SELECT COALESCE(MAX(p.ordem), -1) FROM Produto p WHERE p.estabelecimento = :estabelecimento AND p.categoria = :categoria")
    Integer findMaiorOrdemPorCategoria(@Param("estabelecimento") Estabelecimento estabelecimento, @Param("categoria") String categoria);

    // Por usuário (para compatibilidade com código antigo)
    Optional<Produto> findByUsuarioAndId(Usuario usuario, Long id);
    List<Produto> findByUsuario(Usuario usuario);
    List<Produto> findByUsuarioAndAtivo(Usuario usuario, Boolean ativo);
    List<Produto> findByUsuarioAndCategoria(Usuario usuario, String categoria);
    List<Produto> findByUsuarioAndCategoriaAndAtivo(Usuario usuario, String categoria, Boolean ativo);
}
