package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.ManagementObservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reassembles {@code corporate.knowledge.ManagementExtractor}'s facts (grouped by
 * {@code fact_group}, since one document can carry several distinct statements) into
 * {@link ManagementObservation}s. A group missing its required fields (metricType, direction) is
 * skipped rather than producing a malformed observation - the same "zero is a valid outcome"
 * precedent as every other Stage 2 consumer in this codebase.
 */
@Component
class ManagementObservationParser {

    List<ManagementObservation> parse(UUID documentId, UUID instrumentId, String symbol, Instant announcedAt, List<DocumentFact> facts) {
        Map<UUID, List<DocumentFact>> byGroup = new LinkedHashMap<>();
        for (DocumentFact fact : facts) {
            if (fact.factGroup() == null) {
                continue;
            }
            byGroup.computeIfAbsent(fact.factGroup(), g -> new ArrayList<>()).add(fact);
        }

        List<ManagementObservation> observations = new ArrayList<>();
        for (List<DocumentFact> group : byGroup.values()) {
            parseOne(documentId, instrumentId, symbol, announcedAt, group).ifPresent(observations::add);
        }
        return observations;
    }

    private Optional<ManagementObservation> parseOne(
        UUID documentId, UUID instrumentId, String symbol, Instant announcedAt, List<DocumentFact> group
    ) {
        Map<String, DocumentFact> byType = new LinkedHashMap<>();
        for (DocumentFact fact : group) {
            byType.put(fact.factType(), fact);
        }

        DocumentFact metricTypeFact = byType.get("metrictype");
        DocumentFact directionFact = byType.get("direction");
        if (metricTypeFact == null || directionFact == null) {
            return Optional.empty();
        }

        GuidanceDirection direction;
        try {
            direction = GuidanceDirection.valueOf(directionFact.factValue().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        CommitmentLevel commitmentLevel = parseCommitment(metricTypeFact.commitmentLevel());
        if (commitmentLevel == null) {
            return Optional.empty();
        }

        DocumentFact valueFact = byType.get("guidancevalue");
        DocumentFact valueNumericFact = byType.get("guidancevaluenumeric");
        DocumentFact periodFact = byType.get("guidanceperiod");
        DocumentFact signalFact = byType.get("signal");

        Double numeric = parseDouble(valueNumericFact == null ? null : valueNumericFact.factValue());

        return Optional.of(new ManagementObservation(
            UUID.randomUUID(), documentId, instrumentId, symbol,
            metricTypeFact.factValue(),
            valueFact == null ? "" : valueFact.factValue(),
            numeric,
            periodFact == null ? null : periodFact.factValue(),
            direction,
            signalFact == null ? "" : signalFact.factValue(),
            commitmentLevel,
            metricTypeFact.confidence(),
            announcedAt
        ));
    }

    private static CommitmentLevel parseCommitment(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CommitmentLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
