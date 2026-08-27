package com.wilgner.cardapio;

import com.wilgner.cardapio.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "server.tomcat.threads.max=4",
        "server.tomcat.threads.min-spare=4",
        "supabase.bucket=test-bucket",
        "supabase.key=dummy",
        "JWT_SECRET=dummy_dummy_dummy_dummy_dummy_dummy",
        "spring.main.allow-bean-definition-overriding=true"
})
public class DownloadPerformanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private SupabaseStorageService storageService;

    @BeforeEach
    void configureStorage() {
        when(storageService.downloadFile("test.png"))
                .thenReturn(Mono.delay(Duration.ofMillis(200))
                        .map(ignored -> new SupabaseStorageService.StoredFile(
                                new byte[]{1, 2, 3, 4},
                                org.springframework.http.MediaType.IMAGE_PNG
                        )));
    }

    @Test
    public void testDownloadPerformance() throws InterruptedException {
        int numRequests = 20;
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numRequests; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<byte[]> response = restTemplate.getForEntity("http://localhost:" + port + "/public/storage/test.png", byte[].class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "As requisições não terminaram dentro do limite");
        executor.shutdownNow();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(numRequests, successCount.get(), "Todas as requisições devem retornar sucesso");

        System.out.println("====================================================");
        System.out.println("Total time for " + numRequests + " concurrent requests: " + duration + " ms");
        System.out.println("Successful requests: " + successCount.get());
        System.out.println("====================================================");
    }
}
