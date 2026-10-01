package com.wilgner.cardapio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        Exception exception = assertThrows(IllegalArgumentException.class, () -> service.validateFile(file, ImageUploadProfile.PRODUCT));
        assertTrue(exception.getMessage().contains("Apenas imagens"));
    }

    @Test
    public void oversizedAndForgedImagesAreRejectedBeforeNetwork() {
        MockMultipartFile large = file(new byte[ImageUploadProfile.PRODUCT.maxBytes() + 1]);
        assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class, () -> service.validateFile(large, ImageUploadProfile.PRODUCT));
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(new byte[12]), ImageUploadProfile.PRODUCT));
    }

    @Test public void realLossyAndLosslessWebpAreDecoded() throws Exception {
        for (String name : new String[]{"product.webp", "lossless-alpha.webp"}) {
            byte[] bytes = fixture(name);
            assertArrayEquals(bytes, service.validateFile(file(bytes), ImageUploadProfile.PRODUCT));
        }
        byte[] hero = fixture("hero.webp");
        assertArrayEquals(hero, service.validateFile(file(hero), ImageUploadProfile.HERO));
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(hero), ImageUploadProfile.PRODUCT));
    }

    @ParameterizedTest @ValueSource(strings={"image/png", "image/jpeg", "image/gif", "image/heic"})
    public void newUploadsRequireWebpAndRejectSpoofedMime(String mime) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(
                new MockMultipartFile("file", "x.webp", mime, fixture("product.webp")), ImageUploadProfile.PRODUCT));
    }

    @Test public void emptyCorruptNonDecodableAndTrailingContentAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(new byte[0]), ImageUploadProfile.HERO));
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file("GIF89a fake gif".getBytes()), ImageUploadProfile.HERO));
        byte[] headerOnly = losslessHeader(100, 100); // Valid RIFF/magic/dimensions, no encoded pixels.
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(headerOnly), ImageUploadProfile.PRODUCT));
        // Keep RIFF/chunk lengths consistent but truncate the declared VP8 partition.
        byte[] corrupt = Arrays.copyOf(fixture("product.webp"), 40);
        ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 32).putInt(16, 20);
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(corrupt), ImageUploadProfile.PRODUCT));
        byte[] appended = Arrays.copyOf(fixture("product.webp"), fixture("product.webp").length + 1);
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(appended), ImageUploadProfile.PRODUCT));
    }

    @Test public void dimensionsAndInternalBitstreamAreBoundedBeforeDecode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(fixture("oversized.webp")), ImageUploadProfile.HERO));
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(losslessHeader(16384, 16384)), ImageUploadProfile.HERO));
        byte[] extended = new byte[44];
        ByteBuffer data = ByteBuffer.wrap(extended).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36).put("WEBPVP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10);
        data.position(30);
        data.put("VP8L".getBytes(StandardCharsets.US_ASCII)).putInt(5).put((byte) 0x2f).putInt(1999 | (1999 << 14));
        // Outer dimensions are 1x1, inner lossless frame is 2000x2000.
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(extended), ImageUploadProfile.HERO));
        extended[20] = 2; // Animation flag.
        assertThrows(IllegalArgumentException.class, () -> service.validateFile(file(extended), ImageUploadProfile.HERO));
    }

    @Test public void profilesAndMultipartConfigurationCannotBypassLimits() throws Exception {
        for (String type : new String[]{null, "", "logo", "HERO"}) assertThrows(IllegalArgumentException.class, () -> ImageUploadProfile.fromRequest(type));
        assertEquals(ImageUploadProfile.PRODUCT, ImageUploadProfile.fromRequest("product"));
        assertEquals(ImageUploadProfile.HERO, ImageUploadProfile.fromRequest("hero"));
        assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class,
                () -> service.validateFile(file(new byte[1024 * 1024 + 1]), ImageUploadProfile.HERO));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxFileSize", org.springframework.util.unit.DataSize.ofBytes(100));
        assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class,
                () -> service.validateFile(file(fixture("product.webp")), ImageUploadProfile.PRODUCT));
    }

    @Test public void boundedStreamReadDoesNotTrustMultipartSizeMetadata() {
        var underreported = new MockMultipartFile("file", "optimized.webp", "image/webp", new byte[500 * 1024 + 1]) {
            @Override public long getSize() { return 1; }
        };
        assertThrows(org.springframework.web.multipart.MaxUploadSizeExceededException.class,
                () -> service.validateFile(underreported, ImageUploadProfile.PRODUCT));
    }

    static byte[] fixture(String name) throws Exception {
        try (var input = SupabaseStorageServiceTest.class.getResourceAsStream("/images/" + name)) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }
    static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "optimized.webp", "image/webp", bytes);
    }
    static byte[] losslessHeader(int width, int height) {
        return ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(18)
                .put("WEBPVP8L".getBytes(StandardCharsets.US_ASCII)).putInt(5)
                .put((byte) 0x2f).putInt((width - 1) | ((height - 1) << 14)).put((byte) 0).array();
    }
}
