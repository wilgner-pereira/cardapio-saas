package com.wilgner.cardapio.security;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class RequestRateLimiterTest {
    private final AtomicLong now = new AtomicLong();

    @Test
    void fifthRequestIsAllowedSixthRejectedAndWindowRecovers() {
        RequestRateLimiter limiter = new RequestRateLimiter(5, 30, 5, 300, 100, now::get);
        for (int i = 0; i < 5; i++) assertThat(limiter.retryAfter("login", "ip")).isZero();
        assertThat(limiter.retryAfter("login", "ip")).isEqualTo(60);
        now.set(Duration.ofSeconds(59).toNanos());
        assertThat(limiter.retryAfter("login", "ip")).isEqualTo(1);
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(limiter.retryAfter("login", "ip")).isZero();
    }

    @Test
    void ipsAndScopesHaveIndependentBudgets() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, 1, 1, 1, 100, now::get);
        assertThat(limiter.retryAfter("login", "a")).isZero();
        assertThat(limiter.retryAfter("login", "a")).isPositive();
        assertThat(limiter.retryAfter("login", "b")).isZero();
        assertThat(limiter.retryAfter("refresh", "a")).isZero();
        assertThat(limiter.retryAfter("setup", "a")).isZero();
        assertThat(limiter.retryAfter("public", "a")).isZero();
    }

    @Test
    void capacityNeverEvictsLiveBudgetsAndExpiredEntriesAreReclaimed() {
        RequestRateLimiter limiter = new RequestRateLimiter(1, 1, 1, 1, 2, now::get);
        limiter.retryAfter("login", "a");
        limiter.retryAfter("login", "b");
        assertThat(limiter.retryAfter("login", "c")).isPositive();
        assertThat(limiter.retryAfter("login", "a")).isPositive();
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(limiter.retryAfter("login", "c")).isZero();
    }

    @Test
    void concurrentRequestsCannotExceedTheBudget() {
        RequestRateLimiter limiter = new RequestRateLimiter(5, 30, 5, 300, 100, now::get);
        long accepted = IntStream.range(0, 100).parallel()
                .filter(i -> limiter.retryAfter("login", "same-ip") == 0).count();
        assertThat(accepted).isEqualTo(5);
    }

    @Test
    void invalidConfigurationFailsClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestRateLimiter(0, 1, 1, 1, 1, now::get));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestRateLimiter(1, 1, 1, 1, 0, now::get));
    }
}
