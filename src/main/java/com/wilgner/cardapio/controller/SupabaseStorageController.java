package com.wilgner.cardapio.controller;

import com.wilgner.cardapio.service.SupabaseStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/storage")
public class SupabaseStorageController {

    private final SupabaseStorageService storageService;

    public SupabaseStorageController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String url = storageService.uploadFile(file);
        return ResponseEntity.ok(url);
    }
}
