package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.service.TenantStorageService;
import com.wilgner.cardapio.service.SecurityUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/painel/storage")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class SupabaseStorageController {

    private final TenantStorageService storageService;
    private final com.wilgner.cardapio.service.StorageImageLifecycle images;

    public SupabaseStorageController(TenantStorageService storageService, com.wilgner.cardapio.service.StorageImageLifecycle images) {
        this.storageService = storageService;
        this.images = images;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        var user = SecurityUtils.getUsuarioAutenticado();
        if (user == null) throw new BadCredentialsException("Credenciais inválidas");
        return ResponseEntity.ok(storageService.upload(user.getEstabelecimento().getId(), file));
    }

    @DeleteMapping("/unused-image")
    public ResponseEntity<Void> discardUnused(@RequestBody @jakarta.validation.Valid UnusedImage request) {
        var user = SecurityUtils.getUsuarioAutenticado();
        if (user == null) throw new BadCredentialsException("Credenciais inválidas");
        images.discardUnused(user.getEstabelecimento().getId(), request.url());
        return ResponseEntity.noContent().build();
    }

    public record UnusedImage(@jakarta.validation.constraints.NotBlank
                             @jakarta.validation.constraints.Size(max=2048) String url) {}
}
