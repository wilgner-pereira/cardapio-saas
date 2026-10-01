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
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Service
public class SupabaseStorageService {

    private final WebClient webClient;

    @Value("${supabase.url}")
    private String projectUrl;

    @Value("${supabase.key}")
    private String apiKey;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private DataSize maxFileSize = DataSize.ofMegabytes(1);

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

    public byte[] validateFile(MultipartFile file, ImageUploadProfile profile) throws IOException {

        if (profile == null) throw new IllegalArgumentException("Tipo de imagem inválido");

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (!"image/webp".equals(file.getContentType())) {
            throw new IllegalArgumentException("Apenas imagens WebP otimizadas são permitidas nos novos uploads");
        }

        long sizeLimit = Math.min(maxFileSize.toBytes(), profile.maxBytes());
        if (file.getSize() > sizeLimit) {
            throw new MaxUploadSizeExceededException(sizeLimit);
        }

        // Bound the read independently of multipart metadata before allocating decoder buffers.
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes((int) sizeLimit + 1);
        }
        if (bytes.length > sizeLimit) throw new MaxUploadSizeExceededException(sizeLimit);
        inspectWebp(bytes, profile);
        decodeWebp(bytes, profile);
        return bytes;
    }

    private void decodeWebp(byte[] bytes, ImageUploadProfile profile) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            BufferedImage image = null;
            try {
                reader.setInput(input, false, true);
                if (!"webp".equalsIgnoreCase(reader.getFormatName())) throw invalidImage();
                profile.validateDimensions(reader.getWidth(0), reader.getHeight(0));
                // Dimension checks above and in every encoded frame occur BEFORE full decode.
                image = reader.read(0);
                if (image == null) throw invalidImage();
                profile.validateDimensions(image.getWidth(), image.getHeight());
            } finally {
                if (image != null) image.flush();
                reader.dispose();
            }
        } catch (IOException | RuntimeException failure) {
            // Never expose decoder/provider details in client errors or logs.
            throw invalidImage();
        }
    }

    private IllegalArgumentException invalidImage() {
        return new IllegalArgumentException("Não foi possível validar essa imagem WebP. Escolha outra foto.");
    }

    private void inspectWebp(byte[] bytes, ImageUploadProfile profile) {
        if (bytes.length < 20 || !fourCC(bytes, 0).equals("RIFF") || !fourCC(bytes, 8).equals("WEBP")) throw invalidImage();
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(data.getInt(4)) + 8 != bytes.length) throw invalidImage();
        int width = 0, height = 0, frames = 0;
        boolean extended = false;
        for (int offset = 12; offset < bytes.length;) {
            if (offset + 8 > bytes.length) throw invalidImage();
            String chunk = fourCC(bytes, offset);
            long size = Integer.toUnsignedLong(data.getInt(offset + 4));
            int start = offset + 8;
            long end = start + size + (size & 1);
            if (end > bytes.length) throw invalidImage();
            int nextWidth = 0, nextHeight = 0;
            switch (chunk) {
                case "VP8X" -> {
                    if (size != 10 || extended || offset != 12 || (bytes[start] & 2) != 0) throw invalidImage();
                    extended = true;
                    nextWidth = uint24(bytes, start + 4) + 1;
                    nextHeight = uint24(bytes, start + 7) + 1;
                }
                case "VP8 " -> {
                    if (size < 10 || (bytes[start] & 1) != 0 || (bytes[start + 3] & 255) != 0x9d
                            || bytes[start + 4] != 1 || bytes[start + 5] != 0x2a) throw invalidImage();
                    int frameTag = uint24(bytes, start);
                    int partitionSize = frameTag >>> 5;
                    if (((frameTag >>> 1) & 7) > 3 || (frameTag & 16) == 0
                            || partitionSize == 0 || partitionSize > size - 10) throw invalidImage();
                    nextWidth = Short.toUnsignedInt(data.getShort(start + 6)) & 0x3fff;
                    nextHeight = Short.toUnsignedInt(data.getShort(start + 8)) & 0x3fff;
                    frames++;
                }
                case "VP8L" -> {
                    if (size < 5 || bytes[start] != 0x2f) throw invalidImage();
                    int bits = data.getInt(start + 1);
                    if ((bits >>> 29) != 0) throw invalidImage();
                    nextWidth = (bits & 0x3fff) + 1;
                    nextHeight = ((bits >>> 14) & 0x3fff) + 1;
                    frames++;
                }
                case "ANIM", "ANMF" -> throw invalidImage();
                default -> { /* Bounded metadata/alpha chunks are handled by the decoder. */ }
            }
            if (nextWidth != 0 || nextHeight != 0) {
                profile.validateDimensions(nextWidth, nextHeight);
                if (width != 0 && (width != nextWidth || height != nextHeight)) throw invalidImage();
                width = nextWidth;
                height = nextHeight;
            }
            if (frames > 1) throw invalidImage();
            offset = (int) end;
        }
        if (frames != 1 || width == 0 || height == 0) throw invalidImage();
    }

    private String fourCC(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private int uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8) | ((bytes[offset + 2] & 255) << 16);
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

    public record StoredFile(
            byte[] bytes,
            MediaType contentType
    ) {
    }
}
