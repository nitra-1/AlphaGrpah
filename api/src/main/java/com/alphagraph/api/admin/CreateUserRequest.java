package com.alphagraph.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @Email @NotBlank String email,
    @Size(min = 8, message = "must be at least 8 characters") String password,
    @Pattern(regexp = "ADMIN|USER", message = "must be ADMIN or USER") String role
) {
}
