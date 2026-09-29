package com.wilgner.cardapio.service;

import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.security.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static Usuario getUsuarioAutenticado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getUser();
        }
        return null;
    }
}
