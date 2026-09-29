package com.wilgner.cardapio.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, per-instance fixed windows. Never evict live entries to admit new IPs. */
@Component
public class RequestRateLimiter {
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private final Map<Key, Window> windows = new HashMap<>();
    private final Map<String, Integer> limits;
    private final int maxKeys;
    private final LongSupplier ticker;
    private long lastCleanup;

    @Autowired
    public RequestRateLimiter(@Value("${app.rate-limit.login:5}") int login,
                              @Value("${app.rate-limit.refresh:30}") int refresh,
                              @Value("${app.rate-limit.setup:5}") int setup,
                              @Value("${app.rate-limit.public:300}") int publicRequests,
                              @Value("${app.rate-limit.max-keys:10000}") int maxKeys) {
        this(login, refresh, setup, publicRequests, maxKeys, System::nanoTime);
    }

    RequestRateLimiter(int login, int refresh, int setup, int publicRequests, int maxKeys, LongSupplier ticker) {
        if (login < 1 || refresh < 1 || setup < 1 || publicRequests < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("Limites de requisicoes devem ser positivos");
        }
        limits = Map.of("login", login, "refresh", refresh, "setup", setup, "public", publicRequests);
        this.maxKeys = maxKeys;
        this.ticker = ticker;
        this.lastCleanup = ticker.getAsLong();
    }

    /** Returns zero when admitted, otherwise the Retry-After delay in seconds. */
    public synchronized long retryAfter(String scope, String address) {
        long now = ticker.getAsLong();
        if (now - lastCleanup >= WINDOW_NANOS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW_NANOS);
            lastCleanup = now;
        }
        Key key = new Key(scope, address);
        Window window = windows.get(key);
        if (window == null || now - window.start >= WINDOW_NANOS) {
            if (window == null && windows.size() >= maxKeys) {
                return 60;
            }
            window = new Window(now);
            windows.put(key, window);
        }
        if (window.count >= limits.get(scope)) {
            long remaining = WINDOW_NANOS - (now - window.start);
            return Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L);
        }
        window.count++;
        return 0;
    }

    private record Key(String scope, String address) {}

    private static final class Window {
        private final long start;
        private int count;

        private Window(long start) {
            this.start = start;
        }
    }
}
