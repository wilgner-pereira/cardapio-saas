package com.wilgner.cardapio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class SupabaseStorageServiceTest {

    private SupabaseStorageService service;

    @BeforeEach
    public void setup() throws Exception {
        WebClient.Builder builder = WebClient.builder();
        service = new SupabaseStorageService(builder);

        // Inject properties
        Field projectUrl = SupabaseStorageService.class.getDeclaredField("projectUrl");
        projectUrl.setAccessible(true);
        projectUrl.set(service, "http://localhost:8080");

        Field apiKey = SupabaseStorageService.class.getDeclaredField("apiKey");
        apiKey.setAccessible(true);
        apiKey.set(service, "test-key");

        Field bucket = SupabaseStorageService.class.getDeclaredField("bucket");
        bucket.setAccessible(true);
        bucket.set(service, "test-bucket");
    }

    @Test
    public void testInvalidFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "test".getBytes());
        Exception exception = assertThrows(IllegalArgumentException.class, () -> service.uploadFile(file));
        assertTrue(exception.getMessage().contains("Apenas imagens"));
    }
}
