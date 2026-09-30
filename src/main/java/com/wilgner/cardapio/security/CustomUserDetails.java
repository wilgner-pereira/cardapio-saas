package com.wilgner.cardapio.security;

import com.wilgner.cardapio.model.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    private final Usuario usuario;
    public CustomUserDetails(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        boolean activeEstablishment = hasActiveEstablishment();
        return usuario.getRoles().stream()
                .filter(role -> activeEstablishment || !"ROLE_TENANT_ADMIN".equals(role.getRoleName()))
                .map(role -> new SimpleGrantedAuthority(role.getRoleName()))
                .toList();
    }

    @Override
    public String getPassword() {
        return usuario.getPassword();
    }

    @Override
    public String getUsername() {
        return usuario.getUsername();
    }

    public Usuario getUser() {
        return this.usuario;
    }

    @Override
    public boolean isEnabled() {
        return hasRole("ROLE_PLATFORM_ADMIN") || hasActiveEstablishment();
    }

    private boolean hasActiveEstablishment() {
        return usuario.getEstabelecimento() != null
                && Boolean.TRUE.equals(usuario.getEstabelecimento().getAtivo());
    }

    private boolean hasRole(String roleName) {
        return usuario.getRoles().stream().anyMatch(role -> roleName.equals(role.getRoleName()));
    }
}
