package com.wilgner.cardapio.service;

import com.sun.net.httpserver.HttpServer;
import com.wilgner.cardapio.config.WebClientConfig;
import com.wilgner.cardapio.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class StorageRedirectTest {
    @Test void optimizedUploadAndPublicDownloadPreserveWebpAndLegacyGifContentTypes() throws Exception {
        byte[] image = SupabaseStorageServiceTest.fixture("product.webp");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) {
                assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("image/webp");
                assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo(image);
                exchange.sendResponseHeaders(200, -1);
            } else {
                boolean gif = exchange.getRequestURI().getPath().endsWith("old-gif");
                byte[] body = gif ? java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7") : image;
                exchange.getResponseHeaders().add("Content-Type", gif ? "image/gif" : "image/webp");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            var service = new SupabaseStorageService(new WebClientConfig().webClientBuilder());
            ReflectionTestUtils.setField(service, "projectUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(service, "bucket", "test");
            ReflectionTestUtils.setField(service, "apiKey", "test-only-key");
            var quota = org.mockito.Mockito.mock(StorageQuotaService.class);
            new TenantStorageService(service, quota).upload(1L, SupabaseStorageServiceTest.file(image), ImageUploadProfile.PRODUCT);
            org.mockito.Mockito.verify(quota).reserve(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq((long) image.length));
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    new com.wilgner.cardapio.controller.PublicStorageController(service)).build();
            for (String name : new String[]{"new-webp", "old-gif"}) {
                var pending = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/public/storage/" + name))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(pending))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "inline"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(name.equals("old-gif") ? "image/gif" : "image/webp"));
            }
        } finally { server.stop(0); }
    }
    @Test void missingDeletionIsIdempotentButPermissionFailureRemainsAnError() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
            int code=exchange.getRequestURI().getPath().endsWith("forbidden")?403:404;
            exchange.sendResponseHeaders(code,-1);exchange.close();
        });
        server.start();
        try {
            var service=new SupabaseStorageService(new WebClientConfig().webClientBuilder());
            ReflectionTestUtils.setField(service,"projectUrl","http://127.0.0.1:"+server.getAddress().getPort());
            ReflectionTestUtils.setField(service,"bucket","test");
            ReflectionTestUtils.setField(service,"apiKey","test-only-key");
            assertThatCode(()->service.deleteFile("already removed.png").block()).doesNotThrowAnyException();
            assertThatThrownBy(()->service.deleteFile("forbidden").block()).isInstanceOf(ExternalServiceException.class);
            assertThatThrownBy(()->service.deleteFile("../private")).isInstanceOf(IllegalArgumentException.class);
        } finally {server.stop(0);}
    }

    @Test void uploadDownloadAndDeleteRejectRedirectWithoutContactingDestination() throws Exception {
        AtomicInteger destinationHits = new AtomicInteger();
        var target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", exchange -> {
            destinationHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        var source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + target.getAddress().getPort() + "/private");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        source.start(); target.start();
        try {
            var service = new SupabaseStorageService(new WebClientConfig().webClientBuilder());
            ReflectionTestUtils.setField(service, "projectUrl", "http://127.0.0.1:" + source.getAddress().getPort());
            ReflectionTestUtils.setField(service, "bucket", "test");
            ReflectionTestUtils.setField(service, "apiKey", "test-only-key");
            assertThatThrownBy(() -> service.downloadFile("test.png").block()).isInstanceOf(ExternalServiceException.class);
            assertThatThrownBy(() -> service.uploadBytes("test", "image/png", new byte[12]).block()).isInstanceOf(ExternalServiceException.class);
            assertThatThrownBy(() -> service.deleteFile("test").block()).isInstanceOf(ExternalServiceException.class);
            assertThat(destinationHits.get()).isZero();
            assertThatThrownBy(() -> service.downloadFile("../private")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.downloadFile("http://169.254.169.254/latest")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.downloadFile("a".repeat(101))).isInstanceOf(IllegalArgumentException.class);
        } finally { source.stop(0); target.stop(0); }
    }

    @Test void endpointRejectsNonHttpSchemesCredentialsAndQueryInjection() {
        var service = new SupabaseStorageService(new WebClientConfig().webClientBuilder());
        ReflectionTestUtils.setField(service, "bucket", "test");
        for (String url : new String[]{"file:///etc/passwd", "https://user:pass@example.test", "https://example.test?x=1", "https://private secret@example.test"}) {
            ReflectionTestUtils.setField(service, "projectUrl", url);
            assertThatThrownBy(service::validateEndpoint).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Configuração de storage inválida");
        }
    }
}
