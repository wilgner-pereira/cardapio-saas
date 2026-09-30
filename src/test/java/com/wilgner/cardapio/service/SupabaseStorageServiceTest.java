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
    public void resolvesOnlyKeysFromTheConfiguredPublicBucket() {
        String base="http://localhost:8080/storage/v1/object/public/test-bucket/";
        assertEquals("abc-image.png",service.managedObjectKey(base+"abc-image.png"));
        assertEquals("old image.png",service.managedObjectKey(base+"old%20image.png"));
        assertEquals("abc",service.managedObjectKey(base+"abc?alias=1"));
        for(String url:new String[]{"https://example.com/image.png",base+"../private",base+"folder%2Ffile.png",
                "http://localhost:8080/storage/v1/object/public/other/abc",base.replace("localhost","localhost.evil")+"abc"}){
            assertNull(service.managedObjectKey(url));
        }
    }

    @Test
    public void testInvalidFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "test".getBytes());
        Exception exception = assertThrows(IllegalArgumentException.class, () -> service.validateFile(file));
        assertTrue(exception.getMessage().contains("Apenas imagens"));
    }

    @Test
    public void oversizedAndForgedImagesAreRejectedBeforeNetwork() {
        MockMultipartFile large = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class, () -> service.validateFile(large));
        MockMultipartFile forged = new MockMultipartFile("file", "x.png", "image/png", new byte[12]);
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(forged));
    }

    @Test
    public void supportedMagicBytesRemainAccepted() throws Exception {
        for (byte[] data : new byte[][] {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0},
                {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0},
                {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}
        }) {
            assertArrayEquals(data, service.validateFile(new MockMultipartFile("file", "x.png", "image/png", data)));
        }
    }
}
