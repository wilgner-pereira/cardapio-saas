package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {
    // Platform accounts also have an establishment due to the required user FK.
    // They are not customers and must not be managed through customer endpoints.
    @org.springframework.data.jpa.repository.Query("""
            SELECT e FROM Estabelecimento e WHERE NOT EXISTS (
                SELECT u.id FROM Usuario u JOIN u.roles r
                WHERE u.estabelecimento = e AND r.roleName = 'ROLE_PLATFORM_ADMIN'
            ) ORDER BY e.id
            """)
    List<Estabelecimento> findPlatformClients();

    @org.springframework.data.jpa.repository.Query("""
            SELECT e FROM Estabelecimento e WHERE e.id = :id AND NOT EXISTS (
                SELECT u.id FROM Usuario u JOIN u.roles r
                WHERE u.estabelecimento = e AND r.roleName = 'ROLE_PLATFORM_ADMIN'
            )
            """)
    Optional<Estabelecimento> findPlatformClientById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Estabelecimento e WHERE e.id = :id")
    Optional<Estabelecimento> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Estabelecimento> findBySlug(String slug);
    Optional<Estabelecimento> findByNome(String nome);
    boolean existsBySlug(String slug);
}
