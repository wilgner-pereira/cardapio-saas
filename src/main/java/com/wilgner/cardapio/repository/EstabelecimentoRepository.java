package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {
    Optional<Estabelecimento> findBySlug(String slug);
    Optional<Estabelecimento> findByNome(String nome);
    boolean existsBySlug(String slug);
}
