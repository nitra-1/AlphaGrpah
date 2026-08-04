package com.alphagraph.corporate.knowledge;

import java.util.List;

/** One {@link DocumentExtractor}'s output for one document - normalized into canonical facts (Stage 3) by {@link DocumentRouter}. */
public record ExtractionResult(List<ExtractedFact> facts) {
}
