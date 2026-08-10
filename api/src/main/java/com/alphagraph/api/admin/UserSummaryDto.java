package com.alphagraph.api.admin;

import com.alphagraph.api.auth.PlatformUserRepository;

import java.util.UUID;

public record UserSummaryDto(UUID id, String email, String role, boolean active) {

    public static UserSummaryDto from(PlatformUserRepository.UserSummary summary) {
        return new UserSummaryDto(summary.id(), summary.email(), summary.role(), summary.active());
    }
}
