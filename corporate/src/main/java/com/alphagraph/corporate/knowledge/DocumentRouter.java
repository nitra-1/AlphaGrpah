package com.alphagraph.corporate.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches a Stage 1 {@link DocumentClassification} to every registered {@link DocumentExtractor}
 * whose {@link DocumentExtractor#supports} returns true, then hands the combined facts to
 * {@link CanonicalKnowledgeWriter} (Stage 3: normalization into canonical facts). Spring
 * auto-injects every {@code @Component} implementing {@link DocumentExtractor}, so adding a new
 * extractor (a future {@code PatentExtractor}) never requires touching this class.
 */
@Component
class DocumentRouter {

    private static final Logger log = LoggerFactory.getLogger(DocumentRouter.class);

    private final List<DocumentExtractor> extractors;

    DocumentRouter(List<DocumentExtractor> extractors) {
        this.extractors = extractors;
    }

    List<ExtractedFact> route(DocumentContext context) {
        List<ExtractedFact> facts = new ArrayList<>();
        for (DocumentExtractor extractor : extractors) {
            if (!extractor.supports(context.classification())) {
                continue;
            }
            try {
                ExtractionResult result = extractor.extract(context);
                facts.addAll(result.facts());
            } catch (Exception e) {
                log.error("Extractor {} failed for document {} (symbol={}): {}",
                    extractor.getClass().getSimpleName(), context.documentId(), context.symbol(), e.getMessage(), e);
            }
        }
        return facts;
    }
}
