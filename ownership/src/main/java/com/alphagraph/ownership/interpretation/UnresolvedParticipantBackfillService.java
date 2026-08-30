package com.alphagraph.ownership.interpretation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link InstitutionalInterpretationOrchestrator}'s self-healing first step - resolves
 * {@code participant_id} for any pre-existing {@code discovered_deals} row still missing it (every
 * row captured before Sprint 3 shipped, since V8's migration added the column without a SQL
 * backfill). Going forward, {@code ownership.deals.DiscoveredDealWriter} resolves it at capture
 * time directly - this only ever needs to catch up on the backlog and the rare case a capture-time
 * resolution failed.
 */
@Component
class UnresolvedParticipantBackfillService {

    private static final Logger log = LoggerFactory.getLogger(UnresolvedParticipantBackfillService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ParticipantResolver participantResolver;

    UnresolvedParticipantBackfillService(JdbcTemplate jdbcTemplate, ParticipantResolver participantResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.participantResolver = participantResolver;
    }

    void resolveOutstanding() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, client_name, client_name_normalized FROM ownership.discovered_deals " +
            "WHERE participant_id IS NULL AND client_name_normalized IS NOT NULL"
        );
        int resolved = 0;
        for (Map<String, Object> row : rows) {
            UUID dealId = (UUID) row.get("id");
            String rawName = (String) row.get("client_name");
            String normalizedName = (String) row.get("client_name_normalized");
            try {
                UUID participantId = participantResolver.resolve(rawName, normalizedName);
                jdbcTemplate.update("UPDATE ownership.discovered_deals SET participant_id = ? WHERE id = ?", participantId, dealId);
                resolved++;
            } catch (Exception e) {
                log.warn("Failed to backfill participant for discovered_deals row {}: {}", dealId, e.getMessage());
            }
        }
        if (resolved > 0) {
            log.info("Resolved participants for {} previously-unresolved discovered_deals row(s).", resolved);
        }
    }
}
