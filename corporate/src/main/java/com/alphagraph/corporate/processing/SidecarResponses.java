package com.alphagraph.corporate.processing;

import java.util.List;

/** Mirrors the NLP sidecar's /documents/process JSON response shape exactly (field names match, no @JsonProperty needed). */
record SidecarEntityResponse(String text, String label) {
}

record SidecarChunkResponse(int index, String text, int wordCount, float[] embedding, List<SidecarEntityResponse> entities) {
}

record SidecarProcessedDocumentResponse(int pageCount, String fullText, boolean needsOcr, List<SidecarChunkResponse> chunks) {
}
