package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.service.SupabaseStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;

@RestController
@RequestMapping("/painel/storage")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class SupabaseStorageController {

    private final SupabaseStorageService storageService;

    public SupabaseStorageController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return storageService.uploadFile(file).map(ResponseEntity::ok);
    }
}
