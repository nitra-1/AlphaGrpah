package com.alphagraph.corporate.documents;

import com.alphagraph.common.etl.Parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses NSE's real corporate-announcements JSON array (docs verified live against
 * https://www.nseindia.com/api/corporate-announcements - field names: symbol, sm_isin, desc,
 * attchmntText, attchmntFile, an_dt, seq_id). Uses {@link JsonNode} rather than a fully-typed
 * POJO since the real feed carries many more fields (exchdisstime, smIndustry, hasXbrl, ...) this
 * system doesn't need - only the 6 used fields are extracted, everything else is ignored.
 */
@Component
public class AnnouncementsParser implements Parser<String, RawAnnouncementRow> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawAnnouncementRow> parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse corporate announcements JSON", new java.io.IOException(e));
        }

        List<RawAnnouncementRow> rows = new ArrayList<>();
        for (JsonNode node : root) {
            rows.add(new RawAnnouncementRow(
                textOrNull(node, "symbol"), textOrNull(node, "sm_isin"), textOrNull(node, "desc"),
                textOrNull(node, "attchmntText"), textOrNull(node, "attchmntFile"),
                textOrNull(node, "an_dt"), textOrNull(node, "seq_id")
            ));
        }
        return rows;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
