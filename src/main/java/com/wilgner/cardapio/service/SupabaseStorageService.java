package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

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

    private final RestClient restClient;

    @Value("${supabase.url}")
    private String projectUrl;

    @Value("${supabase.key}")
    private String apiKey;

    @Value("${supabase.bucket}")
    private String bucket;

    public SupabaseStorageService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String uploadFile(MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Apenas imagens JPEG, PNG, WEBP ou GIF são permitidas"
            );
        }

        byte[] bytes = file.getBytes();

        if (!isValidImageMagicBytes(bytes)) {
            throw new IllegalArgumentException(
                    "Conteúdo do arquivo não corresponde a uma imagem válida"
            );
        }

        String originalFilename =
                file.getOriginalFilename() == null
                        ? "imagem"
                        : file.getOriginalFilename();

        String original = originalFilename
                .replaceAll("[^a-zA-Z0-9\\.\\-]", "_");

        String fileName = UUID.randomUUID() + "-" + original;

        String uploadUrl =
                projectUrl
                        + "/storage/v1/object/"
                        + bucket
                        + "/"
                        + fileName;

        restClient.put()
                .uri(uploadUrl)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("apikey", apiKey)
                .body(bytes)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ExternalServiceException(
                            "Falha ao enviar imagem para o storage"
                    );
                })
                .body(String.class);

        return projectUrl
                + "/storage/v1/object/public/"
                + bucket
                + "/"
                + fileName;
    }

    public StoredFile downloadFile(String fileName) {

        if (fileName == null
                || fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }

        String downloadUrl = UriComponentsBuilder
                .fromHttpUrl(projectUrl)
                .pathSegment(
                        "storage",
                        "v1",
                        "object",
                        bucket,
                        fileName
                )
                .build()
                .toUriString();

        ResponseEntity<byte[]> response = restClient.get()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("apikey", apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw new ExternalServiceException(
                            "Falha ao buscar imagem no storage"
                    );
                })
                .toEntity(byte[].class);

        if (response.getBody() == null) {
            throw new ExternalServiceException(
                    "Imagem não encontrada no storage"
            );
        }

        MediaType contentType =
                response.getHeaders().getContentType();

        return new StoredFile(
                response.getBody(),
                contentType != null
                        ? contentType
                        : MediaType.APPLICATION_OCTET_STREAM
        );
    }

    private boolean isValidImageMagicBytes(byte[] bytes) {

        if (bytes == null || bytes.length < 12) {
            return false;
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return true;
        }

        // GIF: GIF87a ou GIF89a
        if (bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '8')
                && bytes[5] == 'a') {
            return true;
        }

        // WEBP: RIFF....WEBP
        if (bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return true;
        }

        return false;
    }

    public record StoredFile(
            byte[] bytes,
            MediaType contentType
    ) {
    }
}