package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.service.SupabaseStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/public/storage")
public class PublicStorageController {

    private final SupabaseStorageService storageService;

    public PublicStorageController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String fileName) {
        SupabaseStorageService.StoredFile file = storageService.downloadFile(fileName);

        return ResponseEntity.ok()
                .contentType(file.contentType())
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(file.bytes());
    }
}
