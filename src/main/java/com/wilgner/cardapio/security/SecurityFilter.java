package com.wilgner.cardapio.security;


import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    public SecurityFilter(TokenService tokenService, UsuarioRepository usuarioRepository) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;

    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = recoverToken(request);

        if (token != null && !token.isBlank()) {
            try {

                // tenta validar o token normalmente
                String username = tokenService.validateAccessToken(token);

                Usuario user = usuarioRepository.findWithRolesAndEstabelecimentoByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

                UserDetails userDetails = new CustomUserDetails(user);

                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT de acesso autenticado para usuario={} metodo={} uri={}",
                        username, request.getMethod(), request.getRequestURI());

            } catch (Exception ex) {
                log.debug("JWT de acesso rejeitado metodo={} uri={} motivo={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        ex.getClass().getSimpleName());
            }
        } else {
            log.trace("Requisicao sem JWT de acesso metodo={} uri={}",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }


    private String recoverToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        if(request.getCookies() != null) {
            for(Cookie cookie : request.getCookies()) {
                if(cookie.getName().equals("access_token")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
