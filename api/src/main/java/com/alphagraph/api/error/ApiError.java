package com.alphagraph.api.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** The one error shape every non-2xx response returns, per docs/004_API_Architecture.md §2. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.name(), message, path);
    }
}
