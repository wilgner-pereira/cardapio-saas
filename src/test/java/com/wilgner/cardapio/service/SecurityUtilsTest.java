package com.wilgner.cardapio.service;

import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityUtilsTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUsuarioAutenticado_ReturnsUsuario_WhenAuthenticated() {
        // Arrange
        Usuario expectedUsuario = new Usuario();
        expectedUsuario.setUsername("testuser");
        CustomUserDetails customUserDetails = new CustomUserDetails(expectedUsuario);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(customUserDetails);

        // Act
        Usuario result = SecurityUtils.getUsuarioAutenticado();

        // Assert
        assertNotNull(result);
        assertEquals(expectedUsuario, result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void getUsuarioAutenticado_ReturnsNull_WhenUnauthenticated() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        // Act
        Usuario result = SecurityUtils.getUsuarioAutenticado();

        // Assert
        assertNull(result);
    }

    @Test
    void missingAuthenticationReturnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(SecurityUtils.getUsuarioAutenticado());
    }

    @Test
    void customPrincipalWithoutAuthenticatedFlagIsRejected() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        assertNull(SecurityUtils.getUsuarioAutenticado());
        verify(authentication, never()).getPrincipal();
    }

    @Test
    void unexpectedPrincipalIsRejectedEvenWhenAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("unexpected");
        assertNull(SecurityUtils.getUsuarioAutenticado());
    }
}
