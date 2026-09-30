package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Estabelecimento e WHERE e.id = :id")
    Optional<Estabelecimento> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Estabelecimento> findBySlug(String slug);
    Optional<Estabelecimento> findByNome(String nome);
    boolean existsBySlug(String slug);
}
