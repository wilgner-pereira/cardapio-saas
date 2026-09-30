package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ExternalServiceException;
import com.wilgner.cardapio.exception.StorageWriteRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @Value("${spring.servlet.multipart.max-file-size:5MB}")
    private DataSize maxFileSize = DataSize.ofMegabytes(5);

    @PostConstruct
    void validateEndpoint() {
        URI endpoint;
        try {
            endpoint = URI.create(projectUrl);
        } catch (IllegalArgumentException ex) {
            // URI parser messages include the supplied value, which may contain credentials.
            throw new IllegalArgumentException("Configuração de storage inválida");
        }
        if (!Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !bucket.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Configuração de storage inválida");
        }
        projectUrl = projectUrl.replaceAll("/+$", "");
    }

    public SupabaseStorageService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public byte[] validateFile(MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Apenas imagens JPEG, PNG, WEBP ou GIF são permitidas"
            );
        }

        if (file.getSize() > maxFileSize.toBytes()) {
            throw new MaxUploadSizeExceededException(maxFileSize.toBytes());
        }

        byte[] bytes = file.getBytes();

        if (!isValidImageMagicBytes(bytes)) {
            throw new IllegalArgumentException(
                    "Conteúdo do arquivo não corresponde a uma imagem válida"
            );
        }

        return bytes;
    }

    public String publicUrl(String key) {
        return projectUrl + "/storage/v1/object/public/" + bucket + "/" + key;
    }

    public Mono<Void> uploadBytes(String key, String contentType, byte[] bytes) {
        String uploadUrl = projectUrl + "/storage/v1/object/" + bucket + "/" + key;

        return webClient.post()
                .uri(uploadUrl)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("apikey", apiKey)
                .bodyValue(bytes)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        Mono.error(response.statusCode().is4xxClientError()
                                ? new StorageWriteRejectedException()
                                : new ExternalServiceException("Falha ao enviar imagem para o storage")))
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(45))
                .onErrorMap(ex -> ex instanceof ExternalServiceException
                        ? ex
                        : new ExternalServiceException("Falha ao enviar imagem para o storage"));
    }

    public Mono<Void> deleteFile(String key) {
        validateObjectKey(key);
        URI deleteUrl = UriComponentsBuilder.fromHttpUrl(projectUrl)
                .pathSegment("storage", "v1", "object", bucket, key).build().encode().toUri();
        return webClient.delete().uri(deleteUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).header("apikey", apiKey)
                // A retry after a completed deletion may find the object already absent.
                .retrieve().onStatus(status -> status.value() == 404, response -> Mono.empty())
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        Mono.error(new ExternalServiceException("Falha ao remover imagem do storage")))
                .bodyToMono(Void.class).timeout(Duration.ofSeconds(45))
                .onErrorMap(ex -> new ExternalServiceException("Falha ao remover imagem do storage"));
    }

    public String managedObjectKey(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI candidate = URI.create(url);
            URI project = URI.create(projectUrl);
            String prefix = "/storage/v1/object/public/" + bucket + "/";
            if (!project.getScheme().equalsIgnoreCase(candidate.getScheme())
                    || !project.getHost().equalsIgnoreCase(candidate.getHost())
                    || project.getPort() != candidate.getPort() || candidate.getUserInfo() != null
                    || candidate.getPath() == null || !candidate.getPath().startsWith(prefix)) return null;
            String key = candidate.getPath().substring(prefix.length());
            validateObjectKey(key);
            return key;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void validateObjectKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100 || key.contains("/")
                || key.contains("\\") || key.equals(".") || key.equals("..")) {
            throw new IllegalArgumentException("Chave de imagem inválida");
        }
    }

    public Mono<StoredFile> downloadFile(String fileName) {

        if (fileName == null
                || fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\") || fileName.equals(".") || fileName.equals("..")) {
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
                .build().encode()
                .toUriString();

        return webClient.get()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("apikey", apiKey)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        Mono.error(new ExternalServiceException("Falha ao buscar imagem no storage")))
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(45))
                .onErrorMap(ex -> ex instanceof ExternalServiceException
                        ? ex
                        : new ExternalServiceException("Falha ao buscar imagem no storage"))
                .map(response -> {
                    if (response.getBody() == null) {
                        throw new ExternalServiceException("Imagem não encontrada no storage");
                    }

                    MediaType contentType = response.getHeaders().getContentType();
                    return new StoredFile(
                            response.getBody(),
                            contentType != null
                                    ? contentType
                                    : MediaType.APPLICATION_OCTET_STREAM
                    );
                });
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
                && (bytes[4] == '7' || bytes[4] == '9')
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
