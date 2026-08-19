package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.FinancialFactGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@link FinancialResultsExtractor}'s output for {@code intelligence.financial.
 * FinancialResultsBridgeOrchestrator} to read - the only public reader in this package, since
 * every other Stage 2 extractor's facts are consumed entirely within {@code corporate} (Order
 * Book, Management Commentary). {@code financial.financial_results} lives in a different module
 * this one cannot depend on (docs/001_System_Architecture.md §4), so unlike {@code
 * corporate.commentary.ManagementObservationParser} this deliberately does NOT persist a
 * corporate-schema table of its own - it groups {@code document_facts} by {@code fact_group} on
 * every call and returns the raw grouped facts, letting the caller (which already knows both the
 * corporate and financial fact vocabularies) do the mapping.
 *
 * <p>Distinguishes this extractor's fact groups from {@code ManagementExtractor}'s (both use
 * {@code fact_group}) by requiring the group to contain a {@code periodtype} fact - only {@link
 * FinancialResultsExtractor} ever writes that key.
 */
@Component
public class FinancialResultFactReader {

    private final JdbcTemplate jdbcTemplate;

    FinancialResultFactReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FinancialFactGroup> findFinancialResultFactGroups() {
        List<RawRow> rows = jdbcTemplate.query(
            """
            SELECT d.id AS document_id, d.instrument_id, d.symbol,
                   f.id, f.fact_type, f.fact_value, f.unit, f.confidence, f.created_at, f.commitment_level, f.fact_group
            FROM corporate.document_facts f
            JOIN corporate.documents d ON d.id = f.document_id
            WHERE f.fact_group IS NOT NULL AND d.status = 'KNOWLEDGE_EXTRACTED'
            ORDER BY d.id, f.fact_group
            """,
            (rs, rowNum) -> new RawRow(
                (UUID) rs.getObject("document_id"), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
                new DocumentFact(
                    (UUID) rs.getObject("id"), (UUID) rs.getObject("document_id"), rs.getString("fact_type"),
                    rs.getString("fact_value"), rs.getString("unit"), rs.getDouble("confidence"),
                    rs.getTimestamp("created_at").toInstant(), rs.getString("commitment_level"),
                    (UUID) rs.getObject("fact_group")
                )
            )
        );

        Map<UUID, List<RawRow>> byGroup = new LinkedHashMap<>();
        for (RawRow row : rows) {
            byGroup.computeIfAbsent(row.fact().factGroup(), g -> new ArrayList<>()).add(row);
        }

        List<FinancialFactGroup> groups = new ArrayList<>();
        for (List<RawRow> group : byGroup.values()) {
            List<DocumentFact> facts = group.stream().map(RawRow::fact).toList();
            boolean isFinancialResultGroup = facts.stream().anyMatch(f -> f.factType().equals("periodtype"));
            if (!isFinancialResultGroup) {
                continue;
            }
            RawRow first = group.get(0);
            if (first.instrumentId() == null) {
                // A document can reach KNOWLEDGE_EXTRACTED and still have no instrument link -
                // e.g. a market-wide news article (corporate.newsfeed) about a company outside
                // the tracked universe, whose text still happens to read like a financial result
                // (a headline literally stating "Revenue... PAT..." is enough for Stage 1 to
                // classify it and Stage 2 to extract real numbers from it). financial.
                // financial_results has no meaning without a known instrument, so this group is
                // never published rather than being handed to a caller that has no way to write
                // it - the same "quarantine before it reaches a hard constraint" discipline
                // DocumentProcessingOrchestrator's non-PDF check follows.
                continue;
            }
            groups.add(new FinancialFactGroup(first.documentId(), first.instrumentId(), first.symbol(), facts));
        }
        return groups;
    }

    private record RawRow(UUID documentId, UUID instrumentId, String symbol, DocumentFact fact) {
    }
}
