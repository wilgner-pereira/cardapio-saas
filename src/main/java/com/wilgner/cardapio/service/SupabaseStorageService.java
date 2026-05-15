package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class SupabaseStorageService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp",
            MediaType.IMAGE_GIF_VALUE
    );

    private final WebClient webClient;

    @Value("${supabase.url}")
    private String projectUrl;

    @Value("${supabase.key}")
    private String apiKey;

    @Value("${supabase.bucket}")
    private String bucket;

    public SupabaseStorageService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String uploadFile(MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Apenas imagens JPEG, PNG, WEBP ou GIF são permitidas");
        }

        String originalFilename = file.getOriginalFilename() == null ? "imagem" : file.getOriginalFilename();
        String original = originalFilename
                .replaceAll("[^a-zA-Z0-9\\.\\-]", "_");

        String fileName = UUID.randomUUID() + "-" + original;

        String uploadUrl = projectUrl + "/storage/v1/object/" + bucket + "/" + fileName;

        webClient.put()
                .uri(uploadUrl)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("apikey", apiKey)
                .bodyValue(file.getBytes())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.createException()
                                .map(ex -> new ExternalServiceException("Falha ao enviar imagem para o storage", ex))
                )
                .bodyToMono(String.class)
                .onErrorMap(ex -> ex instanceof ExternalServiceException
                        ? ex
                        : new ExternalServiceException("Falha ao enviar imagem para o storage", ex))
                .block(Duration.ofMinutes(2));

        return projectUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
    }
}
