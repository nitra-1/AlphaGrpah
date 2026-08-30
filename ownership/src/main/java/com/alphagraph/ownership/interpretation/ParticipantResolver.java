package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a raw + normalized client name to a {@code deal_participants} row, creating one (via
 * {@link ParticipantClassifier} + seeding its first alias) if none exists yet for that normalized
 * name. Used both by {@code ownership.deals.DiscoveredDealWriter} at capture time and by
 * {@link InstitutionalInterpretationOrchestrator}'s self-healing backfill pass for pre-existing
 * {@code discovered_deals} rows still missing {@code participant_id}.
 */
@Component
public class ParticipantResolver {

    private final JdbcTemplate jdbcTemplate;

    public ParticipantResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID resolve(String rawName, String normalizedName) {
        Optional<UUID> existing = findExisting(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }

        ParticipantClassification classification = ParticipantClassifier.classify(normalizedName);
        UUID participantId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO ownership.deal_participants
                (id, canonical_name, normalized_name, participant_type, classification_source, classification_confidence)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (normalized_name) DO NOTHING
            """,
            participantId, rawName, normalizedName, classification.type().name(),
            classification.source(), classification.confidence()
        );

        // A concurrent insert may have won the race (ON CONFLICT DO NOTHING) - re-read to get the
        // real, possibly-different id rather than assuming ours landed.
        UUID resolvedId = findExisting(normalizedName).orElse(participantId);

        jdbcTemplate.update(
            """
            INSERT INTO ownership.deal_participant_aliases (id, participant_id, alias, normalized_alias)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (normalized_alias) DO NOTHING
            """,
            UUID.randomUUID(), resolvedId, rawName, normalizedName
        );

        return resolvedId;
    }

    private Optional<UUID> findExisting(String normalizedName) {
        return jdbcTemplate.query(
            "SELECT id FROM ownership.deal_participants WHERE normalized_name = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"), normalizedName
        ).stream().findFirst();
    }
}
