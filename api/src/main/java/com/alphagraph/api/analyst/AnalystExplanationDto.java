package com.alphagraph.api.analyst;

import java.util.UUID;

public record AnalystExplanationDto(UUID instrumentId, String explanationType, String explanation) {
}
