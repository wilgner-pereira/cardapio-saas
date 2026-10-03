package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.service.SupabaseStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/public/storage")
public class PublicStorageController {

    private final SupabaseStorageService storageService;

    public PublicStorageController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{fileName:.+}")
    public Mono<ResponseEntity<byte[]>> download(@PathVariable String fileName) {
        // Enforce restrictive Content-Security-Policy on public user-content downloads
        // to prevent script execution (defense-in-depth against Stored XSS).
        return storageService.downloadFile(fileName)
                .map(file -> ResponseEntity.ok()
                        .contentType(file.contentType())
                        .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox")
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                        .body(file.bytes()));
    }
}
