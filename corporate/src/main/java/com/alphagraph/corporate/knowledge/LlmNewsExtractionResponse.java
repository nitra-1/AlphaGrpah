package com.alphagraph.corporate.knowledge;

import java.util.List;

record LlmNewsExtractionResponse(List<LlmNewsCompanyImpact> impacts) {
}

record LlmNewsCompanyImpact(
    String companyName, String direction, String signal, String impactSummary, int confidence
) {
}
