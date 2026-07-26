package com.alphagraph.api.auth;

public record LoginResponse(String token, String tokenType, long expiresInSeconds) {
}
