package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses NSE's real per-symbol shareholding JSON (verified live against
 * {@code https://www.nseindia.com/api/corporate-share-holdings-master} - field names: symbol
 * (injected by {@link HttpShareholdingCollector}, NSE's own array elements don't repeat it), date
 * (the quarter-end, e.g. "30-JUN-2026"), pr_and_prgrp (promoter %), public_val (public %), xbrl
 * (the linked filing URL - see {@link ShareholdingXbrlUrlWriter})). {@code fiiPct}/{@code diiPct}/
 * {@code mfPct} are not in this feed at all - they come back null here and are derived later from
 * the XBRL filing. Uses {@link JsonNode} rather than a fully-typed POJO for the same reason
 * {@code corporate.documents.AnnouncementsParser} does - the real feed carries many more fields
 * (broadcastDate, isin, remarksWeb, ...) this system doesn't need.
 */
@Component
public class ShareholdingParser implements Parser<String, RawShareholdingRow> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawShareholdingRow> parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse shareholding pattern JSON", new IOException(e));
        }

        List<RawShareholdingRow> rows = new ArrayList<>();
        for (JsonNode node : root) {
            rows.add(new RawShareholdingRow(
                textOrNull(node, "symbol"), textOrNull(node, "date"), textOrNull(node, "pr_and_prgrp"),
                null, null, null, textOrNull(node, "public_val"), textOrNull(node, "xbrl")
            ));
        }
        return rows;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
