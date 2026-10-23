package com.alphagraph.corporate.knowledge;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every Gemini call in this app goes through here (Stage 1 classification and news extraction
 * share one project quota, so they share one limiter). Three jobs:
 *
 * <ol>
 *   <li><b>Pace</b> calls to {@code requests-per-minute} so a sequential sweep stays under the
 *       provider's cap instead of tripping it - the free tier allows 5 a minute, and a news
 *       sweep makes two calls per article. Every attempt, including retries, takes a slot.</li>
 *   <li><b>Wait and retry</b> transient failures instead of giving up: a 429 waits the delay
 *       Gemini asked for, a 5xx or network error backs off (5s, 10s, 20s...). A 429 that asks
 *       for longer than {@code max-retry-wait-seconds} (a daily quota, say) is not waited for.</li>
 *   <li><b>Open a circuit</b> after {@code breaker-failures} calls in a row that exhausted their
 *       retries, so an outage costs a few minutes of waiting instead of hours: further calls fail
 *       immediately for {@code breaker-open-minutes}.</li>
 * </ol>
 *
 * Retry exhaustion throws {@link GeminiUnavailableException} - a signal to defer the document,
 * not to spend Claude. Anything that isn't transient (a 400 bad request, a 403 bad key) is
 * rethrown untouched for the caller to treat as a content/config failure. The SDK's own retry is
 * switched off (see {@code GeminiClientConfig}) so waits here aren't multiplied by hidden ones.
 */
@Component
class GeminiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(GeminiCallExecutor.class);

    private static final Pattern RETRY_IN_TEXT = Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY_JSON = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)s\"");
    private static final long DEFAULT_429_WAIT_MILLIS = 15_000;
    private static final long RETRY_MARGIN_MILLIS = 1_000;
    private static final long BASE_BACKOFF_MILLIS = 5_000;

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final long minIntervalNanos;
    private final int maxAttempts;
    private final long maxRetryWaitMillis;
    private final int breakerThreshold;
    private final long breakerOpenNanos;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;

    private long nextSlotNanos; // guarded by this
    private int consecutiveExhausted; // guarded by this
    private boolean breakerTripped; // guarded by this
    private long breakerOpenUntilNanos; // guarded by this

    @Autowired
    GeminiCallExecutor(
        @Value("${alphagraph.corporate.gemini.requests-per-minute:4}") int requestsPerMinute,
        @Value("${alphagraph.corporate.gemini.max-attempts:4}") int maxAttempts,
        @Value("${alphagraph.corporate.gemini.max-retry-wait-seconds:90}") int maxRetryWaitSeconds,
        @Value("${alphagraph.corporate.gemini.breaker-failures:3}") int breakerFailures,
        @Value("${alphagraph.corporate.gemini.breaker-open-minutes:10}") int breakerOpenMinutes
    ) {
        this(
            requestsPerMinute, maxAttempts, Duration.ofSeconds(maxRetryWaitSeconds), breakerFailures,
            Duration.ofMinutes(breakerOpenMinutes), Thread::sleep, System::nanoTime
        );
    }

    GeminiCallExecutor(
        int requestsPerMinute, int maxAttempts, Duration maxRetryWait, int breakerFailures, Duration breakerOpenFor,
        Sleeper sleeper, LongSupplier nanoClock
    ) {
        this.minIntervalNanos = requestsPerMinute > 0 ? Duration.ofMinutes(1).toNanos() / requestsPerMinute : 0;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.maxRetryWaitMillis = maxRetryWait.toMillis();
        this.breakerThreshold = breakerFailures;
        this.breakerOpenNanos = breakerOpenFor.toNanos();
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
        this.nextSlotNanos = nanoClock.getAsLong();
    }

    <T> T execute(Supplier<T> call) {
        failFastIfCircuitOpen();

        RuntimeException lastTransient = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            acquireSlot();
            try {
                T result = call.get();
                recordSuccess();
                return result;
            } catch (RuntimeException e) {
                long waitMillis = transientWaitMillis(e, attempt);
                if (waitMillis < 0) {
                    throw e;
                }
                lastTransient = e;
                if (attempt == maxAttempts || waitMillis > maxRetryWaitMillis) {
                    break;
                }
                log.warn("Gemini call failed transiently (attempt {}/{}), waiting {}s before retrying: {}",
                    attempt, maxAttempts, waitMillis / 1000, shorten(e.getMessage()));
                sleep(waitMillis);
            }
        }

        recordExhausted();
        throw new GeminiUnavailableException(
            "Gemini unavailable after retrying: " + shorten(lastTransient == null ? null : lastTransient.getMessage()), lastTransient
        );
    }

    private synchronized void acquireSlot() {
        if (minIntervalNanos <= 0) {
            return;
        }
        long now = nanoClock.getAsLong();
        long slot = nextSlotNanos - now > 0 ? nextSlotNanos : now;
        long waitNanos = slot - now;
        if (waitNanos > 0) {
            sleep(Math.max(1, (waitNanos + 999_999) / 1_000_000));
        }
        nextSlotNanos = slot + minIntervalNanos;
    }

    private synchronized void failFastIfCircuitOpen() {
        if (breakerTripped && breakerOpenUntilNanos - nanoClock.getAsLong() > 0) {
            throw new GeminiUnavailableException(
                "Gemini circuit open after repeated failures - not calling it again until it closes", null
            );
        }
    }

    private synchronized void recordSuccess() {
        consecutiveExhausted = 0;
        breakerTripped = false;
    }

    private synchronized void recordExhausted() {
        consecutiveExhausted++;
        if (breakerThreshold > 0 && consecutiveExhausted >= breakerThreshold) {
            breakerTripped = true;
            breakerOpenUntilNanos = nanoClock.getAsLong() + breakerOpenNanos;
            log.warn("Gemini failed {} calls in a row - pausing Gemini calls for {} minutes",
                consecutiveExhausted, Duration.ofNanos(breakerOpenNanos).toMinutes());
        }
    }

    /** Milliseconds to wait before retrying, or -1 if this failure isn't transient and shouldn't be retried. */
    private static long transientWaitMillis(RuntimeException e, int attempt) {
        if (e instanceof ApiException api) {
            int code = api.code();
            if (code == 429) {
                return retryDelayMillis(api.getMessage()) + RETRY_MARGIN_MILLIS;
            }
            if (code == 500 || code == 502 || code == 503 || code == 504) {
                return backoffMillis(attempt);
            }
            return -1;
        }
        if (e instanceof GenAiIOException) {
            return backoffMillis(attempt);
        }
        return -1;
    }

    private static long backoffMillis(int attempt) {
        return BASE_BACKOFF_MILLIS << Math.min(attempt - 1, 6);
    }

    /** Gemini states the wait both in prose ("Please retry in 4.01s") and as RetryInfo JSON ("retryDelay":"4s"). */
    static long retryDelayMillis(String message) {
        if (message != null) {
            Matcher text = RETRY_IN_TEXT.matcher(message);
            if (text.find()) {
                return (long) Math.ceil(Double.parseDouble(text.group(1)) * 1000);
            }
            Matcher json = RETRY_DELAY_JSON.matcher(message);
            if (json.find()) {
                return (long) Math.ceil(Double.parseDouble(json.group(1)) * 1000);
            }
        }
        return DEFAULT_429_WAIT_MILLIS;
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiUnavailableException("Interrupted while waiting to call Gemini", e);
        }
    }

    private static String shorten(String message) {
        if (message == null) {
            return "no detail";
        }
        String oneLine = message.replaceAll("\\s+", " ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
