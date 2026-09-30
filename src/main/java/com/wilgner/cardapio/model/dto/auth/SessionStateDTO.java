package com.wilgner.cardapio.model.dto.auth;

// Read-only bootstrap state. Anonymous clients receive no account information.
public record SessionStateDTO(boolean authenticated, boolean refreshAvailable,
                              AuthenticationResponseDTO session) {}
