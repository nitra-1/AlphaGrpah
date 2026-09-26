package com.alphagraph.corporate.news;

import com.alphagraph.corporate.relationships.EntityNameNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One resolution outcome - {@code UNRESOLVED} means every field but {@code matchType} is null,
 * never guessed. {@code symbol}/{@code companyName} are populated whenever either id is (reading
 * them back off the same row the match came from, so {@code corporate.news.EconomicEventWriter}
 * never needs a second lookup to populate {@code news_discovery_candidates}).
 */
record CompanyMatch(UUID matchedInstrumentId, UUID matchedSecurityMasterId, String symbol, String companyName, String matchType) {
    static final CompanyMatch UNRESOLVED = new CompanyMatch(null, null, null, null, "UNRESOLVED");
}

/**
 * Resolves a free-text company name (as {@code corporate.knowledge.NewsExtractor} wrote it, in
 * its own words) against the real universe - tracked and untracked - never guessing. A wrong
 * match is worse than no match (plan review correction 1): a single naive string lookup would
 * conflate "Tata Motors", "Tata Motors Ltd", "Tata Motors Passenger Vehicles" with genuinely
 * different real-world entities. Resolution order, first hit wins:
 * <ol>
 *   <li>Exact match against a tracked {@code reference.instruments} name/symbol - the strongest
 *       possible signal, this company is already in AlphaGraph's own tracked universe.</li>
 *   <li>Exact match against {@code reference.security_master} name/symbol - real, NSE-listed, but
 *       not yet tracked - the exact case this whole module exists to surface.</li>
 *   <li>Exact match against {@code corporate.news_company_aliases.normalized_alias} - a small,
 *       curated table grown incrementally by an admin confirming a real name variant (e.g. "BEL"
 *       -> Bharat Electronics), never auto-populated.</li>
 *   <li>A bounded "safe" substring/phrase-containment match against {@code
 *       reference.security_master} company names, via {@link EntityNameNormalizer#matches} - the
 *       same shared normalization {@link NewsInstrumentMatcher}/{@code EntityResolver} already use
 *       for alias comparison, not fuzzy/ML matching (which exists nowhere in this codebase -
 *       confirmed against {@code ownership.deal_participant_aliases}, the one existing
 *       name-matching precedent, itself exact-match-on-normalized-alias). Applied ONLY when it
 *       resolves to exactly one candidate - two or more matches means genuinely ambiguous, which
 *       resolves to {@link CompanyMatch#UNRESOLVED}, never a guess.</li>
 * </ol>
 * Shares {@link EntityNameNormalizer} with {@link NewsInstrumentMatcher}/{@code
 * corporate.relationships.EntityResolver} rather than a third copy of the same normalization.
 */
@Component
class CompanyResolver {

    private final JdbcTemplate jdbcTemplate;

    CompanyResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    CompanyMatch resolve(String companyNameRaw) {
        String normalizedInput = EntityNameNormalizer.normalize(companyNameRaw);
        if (normalizedInput.isBlank()) {
            return CompanyMatch.UNRESOLVED;
        }

        List<NameRow> instrumentRows = loadInstruments();
        for (NameRow row : instrumentRows) {
            if (exactMatches(normalizedInput, row)) {
                return new CompanyMatch(row.id(), null, row.symbol(), row.rawName(), "EXACT_INSTRUMENT");
            }
        }

        // A symbol resolved by any of the security-master-driven steps below may still belong to
        // an already-tracked instrument (e.g. the extracted name doesn't match that instrument's
        // own canonical name/symbol closely enough for the exact step above, but does match
        // loosely enough for substring/alias) - checked by symbol, since every tracked
        // instrument's symbol is guaranteed present in security_master (Add Instrument's own flow
        // requires it). Getting this wrong would let an already-tracked company be silently
        // treated as an "untracked discovery candidate," which is a real correctness bug, not a
        // cosmetic one.
        Map<String, NameRow> trackedInstrumentBySymbol = new HashMap<>();
        for (NameRow row : instrumentRows) {
            trackedInstrumentBySymbol.put(row.symbol(), row);
        }

        List<NameRow> securityMasterRows = loadSecurityMaster();
        for (NameRow row : securityMasterRows) {
            if (exactMatches(normalizedInput, row)) {
                return resolveAgainstTrackedUniverse(row, trackedInstrumentBySymbol, "EXACT_SECURITY_MASTER");
            }
        }

        NameRow aliasedRow = findAlias(normalizedInput, securityMasterRows);
        if (aliasedRow != null) {
            return resolveAgainstTrackedUniverse(aliasedRow, trackedInstrumentBySymbol, "ALIAS");
        }

        // A genuine substring/phrase-containment check ("kaynes technology" found inside "kaynes
        // technology india"), not single-token equality - EntityNameNormalizer.matches already
        // implements exactly this (equals-or-contains-either-way on normalized strings), the same
        // method NewsInstrumentMatcher/EntityResolver already share for alias comparison. Exact
        // equality was already ruled out by the steps above, so a true result here specifically
        // means a genuine, non-exact containment relationship.
        List<NameRow> substringCandidates = new ArrayList<>();
        for (NameRow row : securityMasterRows) {
            if (EntityNameNormalizer.matches(normalizedInput, row.normalizedName())) {
                substringCandidates.add(row);
            }
        }
        if (substringCandidates.size() == 1) {
            return resolveAgainstTrackedUniverse(substringCandidates.get(0), trackedInstrumentBySymbol, "SAFE_SUBSTRING");
        }

        return CompanyMatch.UNRESOLVED;
    }

    private static CompanyMatch resolveAgainstTrackedUniverse(NameRow securityMasterRow, Map<String, NameRow> trackedInstrumentBySymbol, String matchType) {
        NameRow tracked = trackedInstrumentBySymbol.get(securityMasterRow.symbol());
        return tracked != null
            ? new CompanyMatch(tracked.id(), null, tracked.symbol(), tracked.rawName(), matchType)
            : new CompanyMatch(null, securityMasterRow.id(), securityMasterRow.symbol(), securityMasterRow.rawName(), matchType);
    }

    private static boolean exactMatches(String normalizedInput, NameRow row) {
        return normalizedInput.equals(row.normalizedName()) || normalizedInput.equalsIgnoreCase(row.symbol());
    }

    private List<NameRow> loadInstruments() {
        return jdbcTemplate.query(
            "SELECT id, symbol, name FROM reference.instruments",
            (rs, rowNum) -> new NameRow(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("name"), EntityNameNormalizer.normalize(rs.getString("name"))
            )
        );
    }

    private List<NameRow> loadSecurityMaster() {
        return jdbcTemplate.query(
            "SELECT id, symbol, company_name FROM reference.security_master",
            (rs, rowNum) -> new NameRow(
                (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("company_name"), EntityNameNormalizer.normalize(rs.getString("company_name"))
            )
        );
    }

    private NameRow findAlias(String normalizedInput, List<NameRow> securityMasterRows) {
        List<String> symbols = jdbcTemplate.query(
            "SELECT symbol FROM corporate.news_company_aliases WHERE normalized_alias = ?",
            (rs, rowNum) -> rs.getString("symbol"),
            normalizedInput
        );
        if (symbols.isEmpty()) {
            return null;
        }
        String aliasedSymbol = symbols.get(0);
        return securityMasterRows.stream().filter(row -> row.symbol().equals(aliasedSymbol)).findFirst().orElse(null);
    }

    private record NameRow(UUID id, String symbol, String rawName, String normalizedName) {
    }
}
