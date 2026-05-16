package com.wilgner.cardapio.repository;

import com.wilgner.cardapio.model.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByUsernameOrEmail(String username, String email);

    @EntityGraph(attributePaths = {"roles", "estabelecimento"})
    Optional<Usuario> findWithRolesAndEstabelecimentoByUsername(String username);

    boolean existsByUsernameOrEmail(String username, String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u JOIN u.roles r WHERE r.roleName = :roleName")
    boolean existsByRoleName(@Param("roleName") String roleName);
}
