package com.wilgner.cardapio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "server.tomcat.threads.max=20",
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

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("supabase.url", () -> "http://localhost:8080"); // We will just mock the property here to pass context load
    }

    @RestController
    static class MockSupabaseController {
        @GetMapping("/storage/v1/object/test-bucket/test.png")
        public Mono<ResponseEntity<byte[]>> mockDownload() {
            return Mono.delay(Duration.ofMillis(200))
                    .map(it -> ResponseEntity.ok(new byte[]{1, 2, 3, 4}));
        }
    }

    @Test
    public void testDownloadPerformance() throws InterruptedException {
        int numRequests = 20;
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger();

        // Overriding the mocked URL to actually point to our local mock controller
        String actualMockUrl = "http://localhost:" + port;
        System.setProperty("supabase.url", actualMockUrl);

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

        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("====================================================");
        System.out.println("Total time for " + numRequests + " concurrent requests: " + duration + " ms");
        System.out.println("Successful requests: " + successCount.get());
        System.out.println("====================================================");
    }
}
