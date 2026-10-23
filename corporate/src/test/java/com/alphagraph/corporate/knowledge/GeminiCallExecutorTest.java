package com.alphagraph.corporate.knowledge;

import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiCallExecutorTest {

    private final AtomicLong nowNanos = new AtomicLong(1_000_000_000_000L);
    private final List<Long> sleepsMillis = new ArrayList<>();

    /** A sleeper that just advances the fake clock, so the whole throttle/backoff timeline is deterministic and instant. */
    private GeminiCallExecutor executor(int rpm, int attempts, Duration maxWait, int breakerFailures, Duration breakerOpen) {
        return new GeminiCallExecutor(rpm, attempts, maxWait, breakerFailures, breakerOpen, millis -> {
            sleepsMillis.add(millis);
            nowNanos.addAndGet(millis * 1_000_000L);
        }, nowNanos::get);
    }

    private GeminiCallExecutor defaultExecutor() {
        return executor(4, 4, Duration.ofSeconds(90), 3, Duration.ofMinutes(10));
    }

    private static ApiException api(int code, String status, String message) {
        return new ApiException(code, status, message);
    }

    @Test
    void callsAreSpacedToTheConfiguredRatePerMinute() {
        GeminiCallExecutor executor = defaultExecutor(); // 4/min = one slot every 15s

        executor.execute(() -> "a");
        executor.execute(() -> "b");
        executor.execute(() -> "c");

        assertThat(sleepsMillis).containsExactly(15_000L, 15_000L); // the first call goes straight through
    }

    @Test
    void timeSpentOutsideTheExecutorCountsTowardTheSpacing() {
        GeminiCallExecutor executor = defaultExecutor();

        executor.execute(() -> "a");
        nowNanos.addAndGet(Duration.ofSeconds(20).toNanos()); // longer than the 15s slot - no wait needed
        executor.execute(() -> "b");

        assertThat(sleepsMillis).isEmpty();
    }

    @Test
    void zeroRequestsPerMinuteDisablesPacing() {
        GeminiCallExecutor executor = executor(0, 4, Duration.ofSeconds(90), 3, Duration.ofMinutes(10));

        for (int i = 0; i < 5; i++) {
            executor.execute(() -> "ok");
        }

        assertThat(sleepsMillis).isEmpty();
    }

    @Test
    void a429WaitsTheDelayGeminiAskedForPlusAMarginThenSucceeds() {
        GeminiCallExecutor executor = defaultExecutor();
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw api(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota. Please retry in 4.016118499s.");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
        assertThat(sleepsMillis).contains(5_017L); // 4.016s rounded up, plus the 1s margin
    }

    @Test
    void retryDelayIsAlsoReadFromTheRetryInfoJson() {
        assertThat(GeminiCallExecutor.retryDelayMillis("{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"4s\"}"))
            .isEqualTo(4_000L);
        assertThat(GeminiCallExecutor.retryDelayMillis("no hint in this message")).isEqualTo(15_000L);
        assertThat(GeminiCallExecutor.retryDelayMillis(null)).isEqualTo(15_000L);
    }

    @Test
    void a503IsRetriedWithBackoffThenSucceeds() {
        GeminiCallExecutor executor = executor(0, 4, Duration.ofSeconds(90), 3, Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw api(503, "UNAVAILABLE", "This model is currently experiencing high demand.");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(sleepsMillis).containsExactly(5_000L, 10_000L);
    }

    @Test
    void exhaustingTheAttemptsThrowsGeminiUnavailableAfterExactlyThatManyCalls() {
        GeminiCallExecutor executor = executor(0, 3, Duration.ofSeconds(90), 0, Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw api(503, "UNAVAILABLE", "high demand");
        })).isInstanceOf(GeminiUnavailableException.class).hasMessageContaining("high demand");

        assertThat(calls).hasValue(3);
    }

    @Test
    void a429AskingForLongerThanTheMaximumWaitIsNotWaitedFor() {
        GeminiCallExecutor executor = defaultExecutor();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw api(429, "RESOURCE_EXHAUSTED", "Daily quota reached. Please retry in 3600s.");
        })).isInstanceOf(GeminiUnavailableException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleepsMillis).isEmpty();
    }

    @Test
    void aNonTransientErrorIsRethrownUntouchedAndNeverRetried() {
        GeminiCallExecutor executor = defaultExecutor();
        AtomicInteger calls = new AtomicInteger();
        ApiException badRequest = api(400, "INVALID_ARGUMENT", "schema rejected");

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw badRequest;
        })).isSameAs(badRequest);

        assertThat(calls).hasValue(1);
    }

    @Test
    void afterRepeatedExhaustionTheCircuitOpensAndFailsFastThenClosesAfterThePause() {
        GeminiCallExecutor executor = executor(0, 1, Duration.ofSeconds(90), 3, Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();
        Runnable failingCall = () -> assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw api(503, "UNAVAILABLE", "down");
        })).isInstanceOf(GeminiUnavailableException.class);

        failingCall.run();
        failingCall.run();
        failingCall.run(); // third consecutive exhaustion trips the breaker
        assertThat(calls).hasValue(3);

        failingCall.run(); // open: must fail fast without calling Gemini
        assertThat(calls).hasValue(3);

        nowNanos.addAndGet(Duration.ofMinutes(11).toNanos());
        assertThat(executor.execute(() -> "back")).isEqualTo("back"); // closed again, and a success resets it
        assertThat(executor.execute(() -> "still fine")).isEqualTo("still fine");
    }

    @Test
    void aSuccessInBetweenResetsTheConsecutiveFailureCount() {
        GeminiCallExecutor executor = executor(0, 1, Duration.ofSeconds(90), 3, Duration.ofMinutes(10));
        AtomicInteger calls = new AtomicInteger();
        Runnable failingCall = () -> assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw api(503, "UNAVAILABLE", "down");
        })).isInstanceOf(GeminiUnavailableException.class);

        failingCall.run();
        failingCall.run();
        executor.execute(() -> "ok"); // resets the count
        failingCall.run();
        failingCall.run();
        failingCall.run(); // only now does a third consecutive failure happen

        // had the success not reset the count, the 5th call would have been refused without being made
        assertThat(calls).hasValue(5);
    }
}
